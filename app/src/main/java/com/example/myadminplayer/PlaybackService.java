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
            Log.d(TAG, "Sesión de Cast INICIADA - Esperando un momento para estabilizar...");
            // Pequeño retraso para asegurar que el receptor esté listo
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                setCurrentPlayer(castPlayer);
            }, 500);
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
                    @Override
                    public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(@NonNull MediaSession mediaSession, @NonNull MediaSession.ControllerInfo controller) {
                        Log.d("DEBUG_REPRODUCTOR", "onPlaybackResumption: Bloqueando reanudación automática para evitar crash");
                        // Devolvemos una promesa fallida controlada para que el sistema no intente reanudar nada roto
                        return com.google.common.util.concurrent.Futures.immediateFailedFuture(new UnsupportedOperationException());
                    }

                    @NonNull
                    @Override
                    public ListenableFuture<List<MediaItem>> onAddMediaItems(@NonNull MediaSession mediaSession, @NonNull MediaSession.ControllerInfo controller, @NonNull List<MediaItem> mediaItems) {
                        Log.d("DEBUG_REPRODUCTOR", "onAddMediaItems: Recibidos " + mediaItems.size() + " videos para procesar");
                        
                        List<MediaItem> updatedItems = new ArrayList<>();
                        String ipAddress = getWifiIPAddress();
                        
                        // Si recibimos una lista completa, reiniciamos masterPlaylist para mantener índices limpios
                        synchronized (masterPlaylist) {
                            if (mediaItems.size() > 1) {
                                masterPlaylist.clear();
                                Log.d(TAG, "onAddMediaItems: MasterPlaylist reiniciada para nueva lista");
                            }
                        }

                        for (MediaItem item : mediaItems) {
                            int index;
                            synchronized (masterPlaylist) {
                                masterPlaylist.add(item);
                                index = masterPlaylist.size() - 1;
                            }

                            MediaItem newItem = item;
                            Player currentPlayer = mediaSession.getPlayer();
                            
                            // Si estamos en modo Cast, mapeamos a la URL del servidor local
                            if (currentPlayer instanceof CastPlayer && ipAddress != null && item.localConfiguration != null) {
                                String mimeType = item.localConfiguration.mimeType;
                                if (mimeType == null) mimeType = "video/mp4";

                                // Importante: USAR EL ÍNDICE CORRECTO en la URL
                                String serverUrl = "http://" + ipAddress + ":" + SERVER_PORT + "/?index=" + index + "&t=" + System.currentTimeMillis();
                                
                                newItem = item.buildUpon()
                                        .setUri(Uri.parse(serverUrl))
                                        .setMimeType(mimeType)
                                        .build();
                                Log.d("DEBUG_REPRODUCTOR", "Enviando a TV: " + cancionTitulo(item) + " -> " + serverUrl);
                            } else {
                                Log.d("DEBUG_REPRODUCTOR", "Enviando a Local: " + cancionTitulo(item));
                            }
                            updatedItems.add(newItem);
                        }
                        return com.google.common.util.concurrent.Futures.immediateFuture(updatedItems);
                    }

                    private String cancionTitulo(MediaItem item) {
                        return item.mediaMetadata.title != null ? item.mediaMetadata.title.toString() : "Sin título";
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
        String ipAddress = getWifiIPAddress();

        Log.d("DEBUG_REPRODUCTOR", "Cambiando reproductor. Posición actual: " + windowIndex + " (Player: " + newPlayer.getClass().getSimpleName() + ")");

        List<MediaItem> itemsToTransfer = new ArrayList<>();
        
        // Reconstruimos la lista desde la masterPlaylist para asegurar URIs correctas
        synchronized (masterPlaylist) {
            for (int i = 0; i < masterPlaylist.size(); i++) {
                MediaItem item = masterPlaylist.get(i);
                if (newPlayer instanceof CastPlayer && ipAddress != null && item.localConfiguration != null) {
                    String mimeType = item.localConfiguration.mimeType;
                    if (mimeType == null) mimeType = "video/mp4";
                    
                    String serverUrl = "http://" + ipAddress + ":" + SERVER_PORT + "/?index=" + i + "&t=" + System.currentTimeMillis();
                    itemsToTransfer.add(item.buildUpon()
                            .setUri(Uri.parse(serverUrl))
                            .setMimeType(mimeType)
                            .build());
                } else {
                    // Si volvemos a ExoPlayer o no hay IP, usamos el item original (local)
                    itemsToTransfer.add(item);
                }
            }
        }

        previousPlayer.stop();
        
        // Transferencia al nuevo player
        if (windowIndex >= 0 && windowIndex < itemsToTransfer.size()) {
            newPlayer.setMediaItems(itemsToTransfer, windowIndex, contentPositionMs);
        } else {
            newPlayer.setMediaItems(itemsToTransfer);
        }
        
        newPlayer.setPlayWhenReady(playWhenReady);
        newPlayer.prepare();

        mediaSession.setPlayer(newPlayer);
        Log.d("DEBUG_REPRODUCTOR", "Reproductor cambiado con éxito a: " + (newPlayer instanceof CastPlayer ? "CHROMECAST" : "LOCAL"));
    }

    private String getWifiIPAddress() {
        try {
            java.util.List<java.net.NetworkInterface> interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces());
            for (java.net.NetworkInterface intf : interfaces) {
                if (intf.getName().contains("wlan") || intf.getName().contains("eth")) {
                    java.util.List<java.net.InetAddress> addrs = java.util.Collections.list(intf.getInetAddresses());
                    for (java.net.InetAddress addr : addrs) {
                        if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error obteniendo IP: " + ex.getMessage());
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
