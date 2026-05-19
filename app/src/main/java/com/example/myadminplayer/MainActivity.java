package com.example.myadminplayer;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;
    private Player.Listener playerListener;

    Button play_pause, btn_repetir, btnStop, btnAnterior, btnSiguiente, btnRetroceder10s, btnAdelantar10s, btnSpeaker;
    AutoCompleteTextView autoCompleteTextView;
    FloatingActionButton fabAddSong;
    LottieAnimationView animationView;
    TextInputLayout menu_canciones;
    FrameLayout videoContainer;
    ImageView videoBackground;
    TextView userWelcomeText;

    private boolean isAudioOnly = false;
    private List<Cancion> currentPlaylist = new ArrayList<>();
    private AppDatabase db;
    private SharedPreferences sharedPreferences;
    private List<Uri> pendingUris = new ArrayList<>();
    private int totalPendingCount = 0;
    private int currentPendingIndex = 0;
    private String activePlaylistName = null; // Track current loaded playlist scope
    private boolean hasAutoLoadedNext = false; // Evitar bucles infinitos de carga automática

    private final ActivityResultLauncher<Intent> openFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    pendingUris.clear();
                    if (result.getData().getClipData() != null) {
                        for (int i = 0; i < result.getData().getClipData().getItemCount(); i++) {
                            pendingUris.add(result.getData().getClipData().getItemAt(i).getUri());
                        }
                    } else if (result.getData().getData() != null) {
                        pendingUris.add(result.getData().getData());
                    }
                    totalPendingCount = pendingUris.size();
                    currentPendingIndex = 1;
                    processNextPendingUri();
                }
            }
    );

    private boolean isWindows() {
        String model = android.os.Build.MODEL.toLowerCase();
        String manufacturer = android.os.Build.MANUFACTURER.toLowerCase();
        return model.contains("wsa") || manufacturer.contains("subsystem") || model.contains("windows");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MainActivity", "onCreate: Inicio");
        
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("MainActivity", "CRASH DETECTADO", throwable);
            saveCrashLog(throwable);
            System.exit(1);
        });

        try {
            setContentView(R.layout.activity_main);
            Toolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) setSupportActionBar(toolbar);

            db = AppDatabase.getDatabase(getApplicationContext());
            sharedPreferences = getSharedPreferences("MyMusicalPrefs", MODE_PRIVATE);

            initializeViews();
            
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    updateWelcomeMessage();
                    checkFirstRun();
                    fixMissingThumbnails();
                } catch (Exception e) {
                    Log.e("MainActivity", "Error en tareas diferidas", e);
                }
            }, 500);
            
            Log.d("MainActivity", "onCreate: Interfaz cargada");
        } catch (Exception e) {
            Log.e("MainActivity", "Error en onCreate", e);
            saveCrashLog(e);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d("MainActivity", "onConfigurationChanged: Rotación detectada");
        
        // Guardamos el estado actual si es necesario antes de cambiar el layout
        
        // Aplicamos el nuevo layout (automáticamente elegirá el portrait o landscape)
        setContentView(R.layout.activity_main);
        
        // Re-vinculamos la Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) setSupportActionBar(toolbar);
        
        // Re-inicializamos todas las vistas y listeners
        initializeViews();
        
        // Re-vinculamos el controlador al nuevo PlayerView
        if (mediaController != null && playerView != null) {
            playerView.setPlayer(mediaController);
            // Si estaba en modo audio-only, lo mantenemos
            if (isAudioOnly) {
                if (videoContainer != null) videoContainer.setVisibility(View.GONE);
                if (btnSpeaker != null) btnSpeaker.setBackgroundResource(android.R.drawable.ic_lock_silent_mode);
            }
        }
        
        // Refrescamos el adaptador del buscador si tenemos canciones
        if (!currentPlaylist.isEmpty()) {
            updateAutoCompleteAdapter(currentPlaylist);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initializeViews() {
        playerView = findViewById(R.id.player_view);
        play_pause = findViewById(R.id.btnPlay);
        btn_repetir = findViewById(R.id.btnRepetir);
        btnStop = findViewById(R.id.btnStop);
        btnAnterior = findViewById(R.id.btnAnterior);
        btnSiguiente = findViewById(R.id.btnSiguiente);
        btnRetroceder10s = findViewById(R.id.btnRetroceder10s);
        btnAdelantar10s = findViewById(R.id.btnAdelantar10s);
        btnSpeaker = findViewById(R.id.btnSpeaker);
        autoCompleteTextView = findViewById(R.id.autoCompleteTextView);
        fabAddSong = findViewById(R.id.fab_add_song);
        menu_canciones = findViewById(R.id.menu_canciones);
        videoContainer = findViewById(R.id.videoContainer);
        videoBackground = findViewById(R.id.videoBackground);
        userWelcomeText = findViewById(R.id.user_welcome_text);
        
        animationView = findViewById(R.id.animationView);
        if (animationView != null) {
            if (isWindows()) {
                animationView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            } else {
                animationView.playAnimation();
            }
        }

        if (fabAddSong != null) fabAddSong.setOnClickListener(v -> openFilePicker());
        
        if (autoCompleteTextView != null) {
            autoCompleteTextView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    autoCompleteTextView.setText("");
                }
                return false;
            });

            autoCompleteTextView.setOnItemClickListener((parent, view, position, id) -> {
                String selection = (String) parent.getItemAtPosition(position);
                playNewSongFromSelection(selection);
            });
        }

        playerListener = new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (!isAudioOnly) {
                    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                        if (playerView != null) playerView.setVisibility(View.INVISIBLE);
                        if (videoBackground != null) videoBackground.setVisibility(View.VISIBLE);
                        
                        // Si la lista terminó y no hemos cargado extras aún
                        if (playbackState == Player.STATE_ENDED && !hasAutoLoadedNext && activePlaylistName != null) {
                            loadExtraSongsFromOtherPlaylist();
                        }
                    } else {
                        if (playerView != null) playerView.setVisibility(View.VISIBLE);
                        if (videoBackground != null) videoBackground.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (play_pause != null) {
                    play_pause.setBackgroundResource(isPlaying ? R.drawable.pausa : R.drawable.reproducir);
                }
                if (isPlaying) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                if (mediaController == null || currentPlaylist.isEmpty()) return;
                int newIndex = mediaController.getCurrentMediaItemIndex();
                if (newIndex != -1 && newIndex < currentPlaylist.size()) {
                    if (autoCompleteTextView != null) {
                        autoCompleteTextView.setText(currentPlaylist.get(newIndex).titulo, false);
                    }
                }
            }

            @Override
            public void onTracksChanged(@NonNull Tracks tracks) {
                // Eliminamos el refresco automático de aquí para evitar el bucle infinito detectado en los logs.
                // El PlaybackService ya se encarga de cambiar la lista cuando detecta la TV.
                Log.d("DEBUG_REPRODUCTOR", "onTracksChanged: Cambio detectado (sin refresco automático para evitar bucle)");
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
                    Toast.makeText(MainActivity.this, "Archivo no encontrado.", Toast.LENGTH_LONG).show();
                    int errorPosition = mediaController.getCurrentMediaItemIndex();
                    if (errorPosition != -1 && errorPosition < currentPlaylist.size()) {
                        deleteGhostSong(currentPlaylist.get(errorPosition));
                    }
                }
            }
        };
    }

    private void saveCrashLog(Throwable throwable) {
        try {
            File logFile = new File(getExternalFilesDir(null), "crash_log.txt");
            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                writer.println("--- Crash at " + new Date() + " ---");
                throwable.printStackTrace(writer);
                writer.println("\n");
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to save crash log", e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        if (!isWindows()) {
            try {
                com.google.android.gms.cast.framework.CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
            } catch (Exception e) {
                Log.e("MainActivity", "Error Cast: " + e.getMessage());
            }
        } else {
            MenuItem castItem = menu.findItem(R.id.media_route_menu_item);
            if (castItem != null) castItem.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Log.d("MainActivity", "Menu clicado: " + id);
        try {
            if (id == R.id.action_search) {
                showSearchDialog();
                return true;
            } else if (id == R.id.action_manage_songs) {
                Toast.makeText(this, "Abriendo Administrador...", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, ManageSongsActivity.class));
                return true;
            } else if (id == R.id.action_playlists) {
                Toast.makeText(this, "Abriendo Playlists...", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, PlaylistsActivity.class));
                return true;
            } else if (id == R.id.action_about) {
                showAboutDialog();
                return true;
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error al abrir actividad: " + e.getMessage());
            Toast.makeText(this, "Error al abrir: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return super.onOptionsItemSelected(item);
    }

    private void initializeMediaController() {
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                if (playerView != null) playerView.setPlayer(mediaController);
                if (mediaController != null) {
                    mediaController.addListener(playerListener);
                    handleIntent(getIntent());
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error controller", e);
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    protected void onStart() {
        super.onStart();
        initializeMediaController();
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        if (mediaController != null) {
            mediaController.removeListener(playerListener);
            MediaController.releaseFuture(controllerFuture);
            mediaController = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (mediaController != null && mediaController.isConnected()) {
            handleIntent(intent);
        }
    }

    private void handleIntent(Intent intent) {
        String playlistToPlay = intent.getStringExtra("PLAYLIST_TO_PLAY");
        String songTitle = intent.getStringExtra("SONG_TO_PLAY_TITLE");
        if (playlistToPlay != null) {
            loadAndSetPlaylist(playlistToPlay, songTitle, true);
        } else {
            loadAndSetPlaylist(null, null, false);
        }
    }

    private void loadAndSetPlaylist(String playlist, String songTitleToSeek, boolean autoPlay) {
        this.activePlaylistName = playlist;
        this.hasAutoLoadedNext = false; // Resetear cuando el usuario elige una lista manualmente
        loadSongsFromDatabase(playlist, songs -> {
            if (songs.isEmpty()) return;
            currentPlaylist = songs;
            updateAutoCompleteAdapter(songs);
            
            List<MediaItem> mediaItems = new ArrayList<>();
            for (Cancion cancion : songs) {
                Uri songUri = getUriForSong(cancion);
                String mimeType = "video/mp4"; // Default
                try {
                    String type = getContentResolver().getType(songUri);
                    if (type != null) mimeType = type;
                } catch (Exception ignored) {}

                mediaItems.add(new MediaItem.Builder()
                        .setUri(songUri)
                        .setMimeType(mimeType) // CRÍTICO: El MIME type es obligatorio para Cast
                        .setMediaId(cancion.titulo)
                        .setMediaMetadata(new MediaMetadata.Builder().setTitle(cancion.titulo).build())
                        .build());
            }

            if (mediaController != null) {
                Log.d("DEBUG_REPRODUCTOR", "Actualizando lista de reproducción. Items: " + mediaItems.size());
                // Verificamos si la lista es idéntica para no recargar innecesariamente
                boolean isSameList = false;
                if (mediaController.getMediaItemCount() == mediaItems.size()) {
                    isSameList = true;
                    for (int i = 0; i < mediaItems.size(); i++) {
                        if (!mediaItems.get(i).mediaId.equals(mediaController.getMediaItemAt(i).mediaId)) {
                            isSameList = false;
                            break;
                        }
                    }
                }

                if (!isSameList) {
                    boolean hasItems = mediaController.getMediaItemCount() > 0;
                    mediaController.setMediaItems(mediaItems, /* resetPosition= */ !hasItems);
                    
                    if (mediaController.getPlaybackState() == Player.STATE_IDLE || mediaController.getPlaybackState() == Player.STATE_ENDED) {
                        mediaController.prepare();
                    }
                }

                if (songTitleToSeek != null) {
                    int index = findPositionByTitle(songTitleToSeek, songs);
                    if (index != -1) {
                        mediaController.seekTo(index, 0);
                        if (autoCompleteTextView != null) {
                            autoCompleteTextView.setText(songTitleToSeek, false);
                        }
                    }
                }
                
                if (autoPlay || mediaController.getPlayWhenReady()) {
                    mediaController.play();
                }
            }
        });
    }

    private void loadExtraSongsFromOtherPlaylist() {
        hasAutoLoadedNext = true; // Solo lo hacemos una vez por sesión de reproducción
        Log.d("DEBUG_REPRODUCTOR", "Playlist finalizada. Buscando sugerencias de otras listas...");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<String> allPlaylists = db.cancionDao().getAllPlaylists();
            if (allPlaylists == null || allPlaylists.size() <= 1) return;

            // Elegir una lista distinta a la actual al azar
            allPlaylists.remove(activePlaylistName);
            String randomPlaylist = allPlaylists.get(new java.util.Random().nextInt(allPlaylists.size()));

            List<Cancion> extraSongs = db.cancionDao().getSongsByPlaylist(randomPlaylist);
            if (extraSongs.isEmpty()) return;

            // Tomar máximo 3 canciones al azar de esa lista
            java.util.Collections.shuffle(extraSongs);
            int countToTake = Math.min(3, extraSongs.size());
            List<Cancion> selectedExtras = extraSongs.subList(0, countToTake);

            runOnUiThread(() -> {
                if (mediaController != null) {
                    List<MediaItem> extraItems = new ArrayList<>();
                    for (Cancion cancion : selectedExtras) {
                        Uri uri = getUriForSong(cancion);
                        String mimeType = "video/mp4";
                        try {
                            String type = getContentResolver().getType(uri);
                            if (type != null) mimeType = type;
                        } catch (Exception ignored) {}

                        extraItems.add(new MediaItem.Builder()
                                .setUri(uri)
                                .setMimeType(mimeType)
                                .setMediaId(cancion.titulo)
                                .setMediaMetadata(new MediaMetadata.Builder().setTitle(cancion.titulo).build())
                                .build());
                    }

                    Log.d("DEBUG_REPRODUCTOR", "Agregando " + extraItems.size() + " videos sugeridos de la lista: " + randomPlaylist);
                    mediaController.addMediaItems(extraItems);
                    mediaController.play();
                    
                    Toast.makeText(this, "Reproduciendo sugerencias de: " + randomPlaylist, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void loadSongsFromDatabase(String playlist, SongLoadCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<Cancion> songs;
            if (playlist == null || playlist.isEmpty()) {
                songs = db.cancionDao().getAll();
            } else {
                songs = db.cancionDao().getSongsByPlaylist(playlist);
            }
            runOnUiThread(() -> callback.onSongsLoaded(songs));
        });
    }

    private void updateAutoCompleteAdapter(List<Cancion> songs) {
        List<String> titles = new ArrayList<>();
        for (Cancion c : songs) titles.add(c.titulo);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, titles);
        if (autoCompleteTextView != null) {
            autoCompleteTextView.setAdapter(adapter);
        }
    }

    private int findPositionByTitle(String title, List<Cancion> songs) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).titulo.equalsIgnoreCase(title)) return i;
        }
        return -1;
    }

    private Uri getUriForSong(Cancion cancion) {
        if (cancion.videoUri != null) return Uri.parse(cancion.videoUri);
        return Uri.parse("android.resource://" + getPackageName() + "/" + cancion.videoResourceId);
    }

    private interface SongLoadCallback {
        void onSongsLoaded(List<Cancion> songs);
    }

    private void openFilePicker() {
        Log.d("MainActivity", "openFilePicker invocado");
        try {
            Toast.makeText(this, "Selecciona tus videos", Toast.LENGTH_SHORT).show();
            Intent intent;
            if (isWindows()) {
                // Modo PC: Usar el selector de documentos para navegar por discos reales (C:, etc.)
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
            } else {
                // Modo Celular: Usar el selector de contenido estándar para Galería
                intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
            }
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // Permitir selección múltiple en ambos
            openFileLauncher.launch(intent);
        } catch (Exception e) {
            Log.e("MainActivity", "Error en openFilePicker: " + e.getMessage());
            Toast.makeText(this, "Error al abrir: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void processNextPendingUri() {
        if (pendingUris.isEmpty()) {
            totalPendingCount = 0;
            currentPendingIndex = 0;
            return;
        }
        Uri uri = pendingUris.remove(0);
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        showAddSongDialog(uri);
        currentPendingIndex++;
    }

    private void showAddSongDialog(Uri uri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_song, null);
        builder.setView(dialogView);

        final ImageView thumbnailView = dialogView.findViewById(R.id.iv_dialog_thumbnail);
        final EditText titleInput = dialogView.findViewById(R.id.et_song_title);
        final AutoCompleteTextView playlistInput = dialogView.findViewById(R.id.et_playlist_name);

        // Sugerir un nombre relativo a la selección actual (Video 1, Video 2...)
        String suggestedName = "Video " + currentPendingIndex;
        titleInput.setText(suggestedName);
        titleInput.selectAll();
        titleInput.requestFocus();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<String> playlists = db.cancionDao().getAllPlaylists();
            runOnUiThread(() -> {
                if (playlists != null && !playlists.isEmpty()) {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, playlists);
                    playlistInput.setAdapter(adapter);
                }
            });
        });

        builder.setTitle("Agregar Video");
        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String title = titleInput.getText().toString();
            String playlist = playlistInput.getText().toString();
            if (playlist.isEmpty()) playlist = "General";
            saveNewSong(title, playlist, uri.toString(), generateThumbnail(this, uri));
            processNextPendingUri();
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> processNextPendingUri());
        builder.show();
    }

    private String generateThumbnail(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            Bitmap bitmap = retriever.getFrameAtTime(1000000);
            if (bitmap != null) {
                File file = new File(context.getFilesDir(), "thumb_" + System.currentTimeMillis() + ".jpg");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out);
                    return file.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { retriever.release(); } catch (IOException ignored) {}
        }
        return null;
    }

    private void saveNewSong(String title, String playlist, String uri, String thumb) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            // Validar si la canción ya existe en esa playlist
            Cancion existing = db.cancionDao().getSongByTitleAndPlaylist(title, playlist);
            if (existing != null) {
                runOnUiThread(() -> Toast.makeText(this, "El video '" + title + "' ya existe en esta playlist", Toast.LENGTH_SHORT).show());
                return;
            }

            Cancion cancion = new Cancion();
            cancion.titulo = title;
            cancion.playlist = playlist;
            cancion.videoUri = uri;
            cancion.thumbnailPath = thumb;
            db.cancionDao().insertSong(cancion);
            runOnUiThread(() -> {
                Toast.makeText(this, "Video guardado", Toast.LENGTH_SHORT).show();
                // Solo recargamos la lista si ya procesamos todos los videos seleccionados
                if (pendingUris.isEmpty()) {
                    loadAndSetPlaylist(activePlaylistName, null, false);
                }
            });
        });
    }

    private void deleteGhostSong(Cancion cancion) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            db.cancionDao().deleteSong(cancion);
            runOnUiThread(() -> loadAndSetPlaylist(null, null, false));
        });
    }

    public void Siguiente(View v) { if (mediaController != null) mediaController.seekToNext(); }
    public void Anterior(View v) { if (mediaController != null) mediaController.seekToPrevious(); }
    public void PlayPause(View v) { 
        if (mediaController != null) {
            if (mediaController.isPlaying()) mediaController.pause();
            else mediaController.play();
        }
    }
    public void Stop(View v) { if (mediaController != null) mediaController.stop(); }
    public void Repetir(View v) {
        if (mediaController == null) return;
        int nextMode = mediaController.getRepeatMode() == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF;
        mediaController.setRepeatMode(nextMode);
        if (btn_repetir != null) {
            btn_repetir.setBackgroundResource(nextMode == Player.REPEAT_MODE_ONE ? R.drawable.repetir : R.drawable.no_repetir);
        }
    }
    public void adelantar10s(View v) { if (mediaController != null) mediaController.seekTo(mediaController.getCurrentPosition() + 10000); }
    public void retroceder10s(View v) { if (mediaController != null) mediaController.seekTo(mediaController.getCurrentPosition() - 10000); }
    
    public void toggleAudioOnly(View v) {
        isAudioOnly = !isAudioOnly;
        if (videoContainer != null) videoContainer.setVisibility(isAudioOnly ? View.GONE : View.VISIBLE);
        if (btnSpeaker != null) {
            btnSpeaker.setBackgroundResource(isAudioOnly ? android.R.drawable.ic_lock_silent_mode : android.R.drawable.ic_lock_silent_mode_off);
        }
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Acerca de MyMusical")
                .setMessage("MyAdminPlayer v1.0\nReproductor multimedia compatible con Windows y Chromecast.")
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void updateWelcomeMessage() {
        String userName = sharedPreferences.getString("userName", "Usuario");
        if (userWelcomeText != null) {
            userWelcomeText.setText("Hola, " + userName);
        }
    }

    private void checkFirstRun() {
        if (sharedPreferences.getBoolean("isFirstRun", true)) showUserNameDialog();
    }

    private void showUserNameDialog() {
        final EditText input = new EditText(this);
        new AlertDialog.Builder(this)
                .setTitle("Bienvenido")
                .setMessage("¿Cómo te llamas?")
                .setView(input)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    String name = input.getText().toString();
                    sharedPreferences.edit().putBoolean("isFirstRun", false).putString("userName", name).apply();
                    updateWelcomeMessage();
                })
                .setCancelable(false)
                .show();
    }

    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Seleccionar Canción");

        // Inflar un layout que contenga el TextInputLayout y AutoCompleteTextView
        View view = getLayoutInflater().inflate(R.layout.dialog_search_song, null);
        final AutoCompleteTextView dialogSearch = view.findViewById(R.id.dialog_autoCompleteTextView);
        
        // Copiar los títulos de la lista actual
        List<String> titles = new ArrayList<>();
        for (Cancion c : currentPlaylist) titles.add(c.titulo);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, titles);
        dialogSearch.setAdapter(adapter);

        builder.setView(view);
        final AlertDialog dialog = builder.create();

        // Al hacer clic en una sugerencia, reproducir y cerrar el diálogo
        dialogSearch.setOnItemClickListener((parent, view1, position, id) -> {
            String selection = (String) parent.getItemAtPosition(position);
            playNewSongFromSelection(selection);
            dialog.dismiss();
        });

        // Hacer que el teclado aparezca automáticamente y mostrar la lista al tocar
        dialogSearch.setOnClickListener(v -> dialogSearch.showDropDown());

        dialog.show();
    }

    private void playNewSongFromSelection(String selection) {
        int index = findPositionByTitle(selection, currentPlaylist);
        Log.d("DEBUG_REPRODUCTOR", "Seleccionado: " + selection + " (Indice: " + index + ")");
        if (index != -1 && mediaController != null) {
            mediaController.seekTo(index, 0);
            mediaController.play();
        }
    }
    
    private void fixMissingThumbnails() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            if (db == null) return;
            List<Cancion> allSongs = db.cancionDao().getAll();
            for (Cancion c : allSongs) {
                if (c.videoUri != null && (c.thumbnailPath == null || !new File(c.thumbnailPath).exists())) {
                    c.thumbnailPath = generateThumbnail(this, Uri.parse(c.videoUri));
                    db.cancionDao().updateSong(c);
                }
            }
        });
    }
}
