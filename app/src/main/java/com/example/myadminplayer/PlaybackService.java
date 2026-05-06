package com.example.myadminplayer;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.cast.CastPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class PlaybackService extends MediaSessionService {
    private static final String TAG = "PlaybackService";
    private static PlaybackService instance;

    private MediaSession mediaSession = null;
    private ExoPlayer exoPlayer;
    private CastPlayer castPlayer;
    private CastContext castContext;
    private LocalHttpServer httpServer;
    private static final int SERVER_PORT = 8080;

    // Lista maestra para guardar las URIs originales de los archivos locales
    private final List<MediaItem> masterPlaylist = new ArrayList<>();

    public static PlaybackService getInstance() {
        return instance;
    }

    public List<MediaItem> getOriginalMediaItems() {
        return new ArrayList<>(masterPlaylist);
    }

    private final SessionManagerListener<CastSession> sessionManagerListener = new SessionManagerListener<CastSession>() {
        @Override
        public void onSessionStarted(@NonNull CastSession session, @NonNull String sessionId) {
            Log.d(TAG, "Sesión de Cast INICIADA");
            setCurrentPlayer(castPlayer);
        }

        @Override
        public void onSessionResumed(@NonNull CastSession session, boolean wasSuspended) {
            Log.d(TAG, "Sesión de Cast REANUDADA");
            setCurrentPlayer(castPlayer);
        }

        @Override
        public void onSessionEnded(@NonNull CastSession session, int error) {
            Log.d(TAG, "Sesión de Cast FINALIZADA. Error code: " + error);
            setCurrentPlayer(exoPlayer);
        }

        @Override public void onSessionStarting(@NonNull CastSession session) {}
        @Override public void onSessionStartFailed(@NonNull CastSession session, int error) {}
        @Override public void onSessionEnding(@NonNull CastSession session) {}
        @Override public void onSessionResuming(@NonNull CastSession session, @NonNull String sessionId) {}
        @Override public void onSessionResumeFailed(@NonNull CastSession session, int error) {}
        @Override public void onSessionSuspended(@NonNull CastSession session, int reason) {}
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        exoPlayer = new ExoPlayer.Builder(this).build();

        castContext = CastContext.getSharedInstance(this);
        castPlayer = new CastPlayer(castContext);

        // Agregamos listeners de error para debug
        Player.Listener errorListener = new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(TAG, "ERROR DE REPRODUCTOR: " + error.getMessage() + " (Code: " + error.errorCode + ")");
            }
        };
        exoPlayer.addListener(errorListener);
        castPlayer.addListener(errorListener);

        httpServer = new LocalHttpServer(this, SERVER_PORT);
        try {
            httpServer.start();
            Log.d(TAG, "Servidor NanoHTTPD iniciado");
        } catch (IOException e) {
            Log.e(TAG, "Error iniciando servidor: " + e.getMessage());
        }

        Player currentPlayer = castContext.getSessionManager().getCurrentCastSession() != null ? castPlayer : exoPlayer;
        mediaSession = new MediaSession.Builder(this, currentPlayer)
                .setCallback(new MediaSession.Callback() {
                    @NonNull
                    @Override
                    public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(@NonNull MediaSession mediaSession, @NonNull MediaSession.ControllerInfo controller) {
                        return MediaSession.Callback.super.onPlaybackResumption(mediaSession, controller);
                    }

                    @NonNull
                    @Override
                    public ListenableFuture<List<MediaItem>> onAddMediaItems(@NonNull MediaSession mediaSession, @NonNull MediaSession.ControllerInfo controller, @NonNull List<MediaItem> mediaItems) {
                        Log.d(TAG, "onAddMediaItems: Interceptando para proyectar si es necesario");
                        List<MediaItem> updatedItems = new ArrayList<>();
                        String ipAddress = getWifiIPAddress();
                        
                        for (MediaItem item : mediaItems) {
                            MediaItem newItem = item;
                            // Si estamos en CastPlayer, convertimos los nuevos items a URLs del servidor
                            if (mediaSession.getPlayer() instanceof CastPlayer && ipAddress != null && item.localConfiguration != null) {
                                Uri uri = item.localConfiguration.uri;
                                if ("content".equals(uri.getScheme()) || "file".equals(uri.getScheme())) {
                                    // Agregamos a la lista maestra para que el servidor lo encuentre
                                    int index;
                                    synchronized (masterPlaylist) {
                                        masterPlaylist.add(item);
                                        index = masterPlaylist.size() - 1;
                                    }
                                    
                                    String serverUrl = "http://" + ipAddress + ":" + SERVER_PORT + "/?index=" + index + "&t=" + System.currentTimeMillis();
                                    Log.d(TAG, "Nuevo item " + index + " -> URL: " + serverUrl);
                                    
                                    newItem = item.buildUpon()
                                            .setUri(Uri.parse(serverUrl))
                                            .setMimeType("video/mp4")
                                            .build();
                                }
                            } else if (mediaSession.getPlayer() instanceof ExoPlayer) {
                                // Si estamos en el celular, simplemente guardamos en la maestra
                                synchronized (masterPlaylist) {
                                    masterPlaylist.add(item);
                                }
                            }
                            updatedItems.add(newItem);
                        }
                        return com.google.common.util.concurrent.Futures.immediateFuture(updatedItems);
                    }
                })
                .build();

        castContext.getSessionManager().addSessionManagerListener(sessionManagerListener, CastSession.class);
    }

    private void setCurrentPlayer(Player newPlayer) {
        if (mediaSession == null || mediaSession.getPlayer() == newPlayer) {
            Log.d(TAG, "Cambio de reproductor ignorado: ya es el mismo o sesión nula");
            return;
        }

        Player previousPlayer = mediaSession.getPlayer();
        Log.d(TAG, "Cambiando reproductor de " + previousPlayer.getClass().getSimpleName() + " a " + newPlayer.getClass().getSimpleName());

        // Transferir estado
        int windowIndex = previousPlayer.getCurrentMediaItemIndex();
        long contentPositionMs = previousPlayer.getContentPosition();
        boolean playWhenReady = previousPlayer.getPlayWhenReady();

        // Actualizamos la lista maestra SIEMPRE que haya items en el reproductor actual
        // Esto permite que el buscador (+) o nuevas listas se sincronicen al proyectar
        if (previousPlayer.getMediaItemCount() > 0) {
            masterPlaylist.clear();
            for (int i = 0; i < previousPlayer.getMediaItemCount(); i++) {
                masterPlaylist.add(previousPlayer.getMediaItemAt(i));
            }
            Log.d(TAG, "Lista maestra actualizada con " + masterPlaylist.size() + " videos");
        }

        List<MediaItem> mediaItemsToSet = new ArrayList<>();
        String ipAddress = getWifiIPAddress();

        for (int i = 0; i < masterPlaylist.size(); i++) {
            MediaItem originalItem = masterPlaylist.get(i);
            MediaItem newItem = originalItem;
            
            if (newPlayer instanceof CastPlayer && ipAddress != null && originalItem.localConfiguration != null) {
                Uri uri = originalItem.localConfiguration.uri;
                String scheme = uri.getScheme();
                
                if ("content".equals(scheme) || "file".equals(scheme)) {
                    // Usamos el índice para que el servidor sepa qué archivo de la masterPlaylist servir
                    String serverUrl = "http://" + ipAddress + ":" + SERVER_PORT + "/?index=" + i + "&t=" + System.currentTimeMillis();
                    Log.d(TAG, "Item " + i + " -> URL: " + serverUrl);
                    
                    newItem = originalItem.buildUpon()
                            .setUri(Uri.parse(serverUrl))
                            .setMimeType("video/mp4") // Crucial para CastPlayer
                            .build();
                }
            } else if (newPlayer instanceof ExoPlayer) {
                // Al volver al celular, newItem ya es el originalItem
                Log.d(TAG, "Item " + i + " -> Restaurando URI original para celular");
            }
            mediaItemsToSet.add(newItem);
        }

        previousPlayer.stop();
        previousPlayer.clearMediaItems();

        if (mediaItemsToSet.isEmpty()) {
            Log.w(TAG, "No hay items para transferir.");
            mediaSession.setPlayer(newPlayer);
            return;
        }

        newPlayer.setMediaItems(mediaItemsToSet);
        newPlayer.setPlayWhenReady(playWhenReady);
        newPlayer.prepare();

        if (windowIndex >= 0 && windowIndex < mediaItemsToSet.size()) {
            if (newPlayer instanceof CastPlayer) {
                final int finalIndex = windowIndex;
                final long finalPos = contentPositionMs;
                newPlayer.addListener(new Player.Listener() {
                    @Override
                    public void onTimelineChanged(@NonNull androidx.media3.common.Timeline timeline, int reason) {
                        if (!timeline.isEmpty()) {
                            newPlayer.removeListener(this);
                            Log.d(TAG, "Timeline del Chromecast listo. Haciendo Seek a: " + finalIndex);
                            newPlayer.seekTo(finalIndex, finalPos);
                        }
                    }
                });
            } else {
                newPlayer.seekTo(windowIndex, contentPositionMs);
            }
        }

        mediaSession.setPlayer(newPlayer);
        Log.d(TAG, "Cambio de reproductor completado con éxito");
    }

    private String getWifiIPAddress() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return null;
        
        LinkProperties linkProperties = connectivityManager.getLinkProperties(connectivityManager.getActiveNetwork());
        if (linkProperties == null) return null;

        for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
            if (linkAddress.getAddress() instanceof Inet4Address) {
                return linkAddress.getAddress().getHostAddress();
            }
        }
        return null;
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Player player = mediaSession.getPlayer();
        if (player != null) {
            player.release();
        }
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (httpServer != null) {
            httpServer.stop();
        }
        if (castContext != null) {
            castContext.getSessionManager().removeSessionManagerListener(sessionManagerListener, CastSession.class);
        }
        if (exoPlayer != null) {
            exoPlayer.release();
        }
        if (castPlayer != null) {
            castPlayer.release();
        }
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }
}
