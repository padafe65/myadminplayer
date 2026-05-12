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
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
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

    private boolean isWindows() {
        String model = android.os.Build.MODEL.toLowerCase();
        String manufacturer = android.os.Build.MANUFACTURER.toLowerCase();
        return model.contains("wsa") || manufacturer.contains("subsystem") || model.contains("windows");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: Iniciando PlaybackService");
        instance = this;
        exoPlayer = new ExoPlayer.Builder(this).build();

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(TAG, "ERROR DE EXO_PLAYER: " + error.getMessage() + " (Code: " + error.errorCode + ")");
            }
        });

        if (isWindows()) {
            Log.d(TAG, "Detectado Windows/WSA: Saltando inicialización de Cast para evitar bloqueo.");
        } else {
            Log.d(TAG, "Intentando obtener CastContext asíncronamente...");
            try {
                Task<CastContext> castContextTask = CastContext.getSharedInstance(this, MoreExecutors.directExecutor());
                castContextTask.addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "CastContext obtenido con éxito");
                        castContext = task.getResult();
                        castPlayer = new CastPlayer(castContext);
                        castPlayer.addListener(new Player.Listener() {
                            @Override
                            public void onPlayerError(@NonNull PlaybackException error) {
                                Log.e(TAG, "ERROR DE CAST_PLAYER: " + error.getMessage() + " (Code: " + error.errorCode + ")");
                            }
                        });
                        castContext.getSessionManager().addSessionManagerListener(sessionManagerListener, CastSession.class);
                        
                        if (castContext.getSessionManager().getCurrentCastSession() != null) {
                            setCurrentPlayer(castPlayer);
                        }
                    } else {
                        Log.e(TAG, "Fallo al obtener CastContext (No crítico)", task.getException());
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Excepción al solicitar CastContext: " + e.getMessage());
            }
        }

        httpServer = new LocalHttpServer(this, SERVER_PORT);
        new Thread(() -> {
            try {
                httpServer.start();
                Log.d(TAG, "Servidor NanoHTTPD iniciado en hilo separado");
            } catch (IOException e) {
                Log.e(TAG, "Error iniciando servidor: " + e.getMessage());
            }
        }).start();

        mediaSession = new MediaSession.Builder(this, exoPlayer)
                .setCallback(new MediaSession.Callback() {
                    @NonNull
                    @Override
                    public ListenableFuture<List<MediaItem>> onAddMediaItems(@NonNull MediaSession mediaSession, @NonNull MediaSession.ControllerInfo controller, @NonNull List<MediaItem> mediaItems) {
                        Log.d(TAG, "onAddMediaItems: Interceptando");
                        List<MediaItem> updatedItems = new ArrayList<>();
                        String ipAddress = getWifiIPAddress();
                        
                        for (MediaItem item : mediaItems) {
                            MediaItem newItem = item;
                            if (mediaSession.getPlayer() instanceof CastPlayer && ipAddress != null && item.localConfiguration != null) {
                                int index;
                                synchronized (masterPlaylist) {
                                    masterPlaylist.add(item);
                                    index = masterPlaylist.size() - 1;
                                }
                                String serverUrl = "http://" + ipAddress + ":" + SERVER_PORT + "/?index=" + index + "&t=" + System.currentTimeMillis();
                                newItem = item.buildUpon()
                                        .setUri(Uri.parse(serverUrl))
                                        .setMimeType("video/mp4")
                                        .build();
                            } else {
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
    }

    private void setCurrentPlayer(Player newPlayer) {
        if (mediaSession == null || mediaSession.getPlayer() == newPlayer) return;

        Player previousPlayer = mediaSession.getPlayer();
        int windowIndex = previousPlayer.getCurrentMediaItemIndex();
        long contentPositionMs = previousPlayer.getContentPosition();
        boolean playWhenReady = previousPlayer.getPlayWhenReady();

        if (previousPlayer.getMediaItemCount() > 0) {
            masterPlaylist.clear();
            for (int i = 0; i < previousPlayer.getMediaItemCount(); i++) {
                masterPlaylist.add(previousPlayer.getMediaItemAt(i));
            }
        }

        List<MediaItem> mediaItemsToSet = new ArrayList<>();
        String ipAddress = getWifiIPAddress();

        for (int i = 0; i < masterPlaylist.size(); i++) {
            MediaItem originalItem = masterPlaylist.get(i);
            MediaItem newItem = originalItem;
            if (newPlayer instanceof CastPlayer && ipAddress != null && originalItem.localConfiguration != null) {
                String serverUrl = "http://" + ipAddress + ":" + SERVER_PORT + "/?index=" + i + "&t=" + System.currentTimeMillis();
                newItem = originalItem.buildUpon().setUri(Uri.parse(serverUrl)).setMimeType("video/mp4").build();
            }
            mediaItemsToSet.add(newItem);
        }

        previousPlayer.stop();
        previousPlayer.clearMediaItems();
        newPlayer.setMediaItems(mediaItemsToSet);
        newPlayer.setPlayWhenReady(playWhenReady);
        newPlayer.prepare();

        if (windowIndex >= 0 && windowIndex < mediaItemsToSet.size()) {
            newPlayer.seekTo(windowIndex, contentPositionMs);
        }

        mediaSession.setPlayer(newPlayer);
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
        super.onTaskRemoved(rootIntent);
        if (exoPlayer != null) {
            exoPlayer.stop();
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (httpServer != null) httpServer.stop();
        if (castContext != null) castContext.getSessionManager().removeSessionManagerListener(sessionManagerListener, CastSession.class);
        if (exoPlayer != null) exoPlayer.release();
        if (castPlayer != null) castPlayer.release();
        if (mediaSession != null) mediaSession.release();
        super.onDestroy();
    }
}
