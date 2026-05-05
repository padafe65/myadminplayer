package com.example.myadminplayer;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.android.gms.cast.framework.CastButtonFactory;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;
    private Player.Listener playerListener;

    Button play_pause, btn_repetir, btnStop, btnAnterior, btnSiguiente, btnRetroceder10s, btnAdelantar10s;
    AutoCompleteTextView autoCompleteTextView;
    FloatingActionButton fabAddSong;
    LottieAnimationView animationView;
    TextInputLayout menu_canciones;
    FrameLayout videoContainer;
    ImageView videoBackground;
    TextView userWelcomeText;

    private List<Cancion> currentPlaylist = new ArrayList<>();
    private AppDatabase db;
    private SharedPreferences sharedPreferences;

    private final ActivityResultLauncher<Intent> openFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            // Otorgar permiso persistente para que el servidor pueda leer el archivo luego
                            getContentResolver().takePersistableUriPermission(uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) {
                            e.printStackTrace();
                        }
                        showAddSongDialog(uri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        initializeViews();
        db = AppDatabase.getDatabase(getApplicationContext());
        sharedPreferences = getSharedPreferences("MyMusicalPrefs", MODE_PRIVATE);

        updateWelcomeMessage();
        checkFirstRun();
    }

    private void checkFirstRun() {
        boolean isFirstRun = sharedPreferences.getBoolean("isFirstRun", true);
        if (isFirstRun) {
            showUserNameDialog();
        }
    }

    private void showUserNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Bienvenido a MyMusical");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Ingresa tu nombre");
        builder.setView(input);

        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String userName = input.getText().toString();
            if (!userName.isEmpty()) {
                sharedPreferences.edit()
                        .putBoolean("isFirstRun", false)
                        .putString("userName", userName)
                        .apply();
                Toast.makeText(this, "¡Hola, " + userName + "!", Toast.LENGTH_SHORT).show();
                updateWelcomeMessage();
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    private void updateWelcomeMessage() {
        String userName = sharedPreferences.getString("userName", "");
        if (!userName.isEmpty()) {
            userWelcomeText.setText("Hola, " + userName);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("MyAdminPlayer");
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

    @Override
    protected void onStart() {
        super.onStart();
        initializeMediaController();
    }

    @Override
    protected void onStop() {
        super.onStop();
        releaseMediaController();
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
        autoCompleteTextView = findViewById(R.id.autoCompleteTextView);
        fabAddSong = findViewById(R.id.fab_add_song);
        animationView = findViewById(R.id.animationView);
        menu_canciones = findViewById(R.id.menu_canciones);
        videoContainer = findViewById(R.id.videoContainer);
        videoBackground = findViewById(R.id.videoBackground);
        userWelcomeText = findViewById(R.id.user_welcome_text);

        playerView.setVisibility(View.VISIBLE);
        fabAddSong.setOnClickListener(v -> openFilePicker());

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

        playerListener = new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    playerView.setVisibility(View.INVISIBLE);
                    videoBackground.setVisibility(View.VISIBLE);
                } else {
                    playerView.setVisibility(View.VISIBLE);
                    videoBackground.setVisibility(View.GONE);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                play_pause.setBackgroundResource(isPlaying ? R.drawable.pausa : R.drawable.reproducir);
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
                    autoCompleteTextView.setText(currentPlaylist.get(newIndex).titulo, false);
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
                    Toast.makeText(MainActivity.this, "Archivo no encontrado. Pudo haber sido eliminado.", Toast.LENGTH_LONG).show();
                    int errorPosition = mediaController.getCurrentMediaItemIndex();
                    if (errorPosition != -1 && errorPosition < currentPlaylist.size()) {
                        deleteGhostSong(currentPlaylist.get(errorPosition));
                    }
                }
            }
        };
    }

    private void initializeMediaController() {
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                playerView.setPlayer(mediaController);
                addPlayerListener();
                handleIntent(getIntent());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void handleIntent(Intent intent) {
        String playlistToPlay = intent.getStringExtra("PLAYLIST_TO_PLAY");
        String songToPlay = intent.getStringExtra("SONG_TO_PLAY_TITLE");

        if (playlistToPlay != null) {
            loadAndSetPlaylist(playlistToPlay, songToPlay, true);
        } else if (mediaController.getMediaItemCount() == 0) {
            loadAndSetPlaylist(null, null, false);
        }

        if (intent.hasExtra("PLAYLIST_TO_PLAY")) {
            setIntent(new Intent());
        }
    }

    private void playNewSongFromSelection(String selection) {
        int pos = findPositionByTitle(selection, this.currentPlaylist);
        if (pos != -1) {
            mediaController.seekTo(pos, 0);
            mediaController.play();
        } else {
            // Si no está en la lista actual, recargamos todo
            loadAndSetPlaylist(null, selection, true);
        }

        InputMethodManager in = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (in != null) {
            in.hideSoftInputFromWindow(autoCompleteTextView.getWindowToken(), 0);
        }
        autoCompleteTextView.clearFocus();
    }

    private void loadAndSetPlaylist(String playlistName, String songToPlayTitle, boolean shouldPlay) {
        loadSongsFromDatabase(playlistName, songs -> {
            this.currentPlaylist = songs;
            updateAutoCompleteAdapter(songs);

            if (mediaController != null) {
                List<MediaItem> mediaItems = new ArrayList<>();
                if (!currentPlaylist.isEmpty()) {
                    for (Cancion cancion : currentPlaylist) {
                        MediaMetadata metadata = new MediaMetadata.Builder()
                                .setTitle(cancion.titulo)
                                .setArtist("MyAdminPlayer")
                                .build();

                        mediaItems.add(new MediaItem.Builder()
                                .setUri(getUriForSong(cancion))
                                .setMimeType(MimeTypes.VIDEO_MP4)
                                .setMediaMetadata(metadata)
                                .build());
                    }
                }

                int startIndex = 0;
                if (songToPlayTitle != null) {
                    startIndex = findPositionByTitle(songToPlayTitle, currentPlaylist);
                }
                if (startIndex == -1) startIndex = 0;

                mediaController.setMediaItems(mediaItems, startIndex, 0);
                mediaController.prepare();
                mediaController.setPlayWhenReady(shouldPlay);
            }
        });
    }

    private void addPlayerListener() {
        if (mediaController != null && playerListener != null) {
            mediaController.removeListener(playerListener);
            mediaController.addListener(playerListener);
        }
    }

    private void releaseMediaController() {
        if (mediaController != null && playerListener != null) {
            mediaController.removeListener(playerListener);
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
        mediaController = null;
        controllerFuture = null;
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        openFileLauncher.launch(intent);
    }

    private void showAddSongDialog(Uri uri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_song, null);
        builder.setView(dialogView);

        final EditText titleInput = dialogView.findViewById(R.id.et_song_title);
        final EditText playlistInput = dialogView.findViewById(R.id.et_playlist_name);

        builder.setTitle("Añadir Video");
        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String title = titleInput.getText().toString();
            String playlistName = playlistInput.getText().toString();

            if (playlistName.isEmpty()) {
                playlistName = "General";
            }

            if (!title.isEmpty()) {
                String finalPlaylistName = playlistName;
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    String thumbnailPath = generateThumbnail(this, uri);
                    saveNewSong(title, uri.toString(), thumbnailPath, finalPlaylistName);
                });
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private String generateThumbnail(Context context, Uri videoUri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, videoUri);
            Bitmap bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

            if (bitmap != null) {
                File internal_storage_path = new File(context.getFilesDir(), "thumbnails");
                if (!internal_storage_path.exists()) {
                    internal_storage_path.mkdirs();
                }
                File file = new File(internal_storage_path, System.currentTimeMillis() + ".jpg");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    int width = 240;
                    int height = (int) (bitmap.getHeight() * ((float) width / bitmap.getWidth()));
                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false);
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
                    return file.getAbsolutePath();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void saveNewSong(String title, String uriString, String thumbnailPath, String playlist) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Cancion newSong = new Cancion(title, uriString, thumbnailPath, playlist);
            db.cancionDao().insertSong(newSong);
            loadSongsFromDatabase(null, songs -> {
                this.currentPlaylist = songs;
                updateAutoCompleteAdapter(songs);
                MediaMetadata metadata = new MediaMetadata.Builder()
                        .setTitle(newSong.titulo)
                        .setArtist("MyAdminPlayer")
                        .build();

                mediaController.addMediaItem(new MediaItem.Builder()
                        .setUri(getUriForSong(newSong))
                        .setMimeType(MimeTypes.VIDEO_MP4)
                        .setMediaMetadata(metadata)
                        .build());
            });
        });
    }

    private void deleteGhostSong(Cancion cancion) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            if (cancion.thumbnailPath != null) {
                new File(cancion.thumbnailPath).delete();
            }
            db.cancionDao().deleteSong(cancion);
            String currentPlaylistName = getIntent().getStringExtra("PLAYLIST_TO_PLAY");
            runOnUiThread(() -> loadAndSetPlaylist(currentPlaylistName, null, false));
        });
    }

    public void Siguiente(View view) {
        if (mediaController != null && mediaController.hasNextMediaItem()) {
            mediaController.seekToNextMediaItem();
        }
    }

    public void Anterior(View view) {
        if (mediaController != null && mediaController.hasPreviousMediaItem()) {
            mediaController.seekToPreviousMediaItem();
        }
    }

    public void PlayPause(View view) {
        if (mediaController == null || mediaController.getMediaItemCount() == 0) {
            Toast.makeText(this, "Selecciona una canción", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mediaController.isPlaying()) {
            mediaController.pause();
        } else {
            mediaController.play();
        }
    }

    public void Stop(View view) {
        if (mediaController != null) {
            mediaController.stop();
        }
        autoCompleteTextView.setText("", false);
        Toast.makeText(this, "Reproducción detenida", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        // Vincula el botón del XML con el sistema de búsqueda de dispositivos (Smart TV/Chromecast)
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_manage_songs) {
            startActivity(new Intent(this, ManageSongsActivity.class));
            return true;
        } else if (itemId == R.id.action_playlists) {
            startActivity(new Intent(this, PlaylistsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void Repetir(View view) {
        if (mediaController == null) return;
        int newMode = mediaController.getRepeatMode() == Player.REPEAT_MODE_ONE ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ONE;
        mediaController.setRepeatMode(newMode);
        btn_repetir.setBackgroundResource(newMode == Player.REPEAT_MODE_ONE ? R.drawable.repetir : R.drawable.no_repetir);
        Toast.makeText(this, newMode == Player.REPEAT_MODE_ONE ? "Repetir activado" : "Repetir desactivado", Toast.LENGTH_SHORT).show();
    }

    public void adelantar10s(View view) {
        if (mediaController != null) mediaController.seekForward();
    }

    public void retroceder10s(View view) {
        if (mediaController != null) mediaController.seekBack();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            enterFullScreen();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            exitFullScreen();
        }
    }

    private void enterFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        hideSystemUI();
        findViewById(R.id.toolbar).setVisibility(View.GONE);
        menu_canciones.setVisibility(View.GONE);
        animationView.setVisibility(View.GONE);
        btnAnterior.setVisibility(View.GONE);
        btnSiguiente.setVisibility(View.GONE);
        play_pause.setVisibility(View.GONE);
        btnStop.setVisibility(View.GONE);
        btn_repetir.setVisibility(View.GONE);
        btnRetroceder10s.setVisibility(View.GONE);
        btnAdelantar10s.setVisibility(View.GONE);
        fabAddSong.setVisibility(View.GONE);
        ViewGroup.LayoutParams params = videoContainer.getLayoutParams();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        videoContainer.setLayoutParams(params);
    }

    private void exitFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        }
        showSystemUI();
        videoBackground.setVisibility(View.VISIBLE);
        findViewById(R.id.toolbar).setVisibility(View.VISIBLE);
        menu_canciones.setVisibility(View.VISIBLE);
        animationView.setVisibility(View.VISIBLE);
        btnAnterior.setVisibility(View.VISIBLE);
        btnSiguiente.setVisibility(View.VISIBLE);
        play_pause.setVisibility(View.VISIBLE);
        btnStop.setVisibility(View.VISIBLE);
        btn_repetir.setVisibility(View.VISIBLE);
        btnRetroceder10s.setVisibility(View.VISIBLE);
        btnAdelantar10s.setVisibility(View.VISIBLE);
        fabAddSong.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams params = videoContainer.getLayoutParams();
        params.height = (int) (250 * getResources().getDisplayMetrics().density);
        videoContainer.setLayoutParams(params);
    }

    private void hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), playerView);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void showSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), playerView);
        controller.show(WindowInsetsCompat.Type.systemBars());
    }

    interface SongLoadCallback {
        void onSongsLoaded(List<Cancion> songs);
    }

    private void loadSongsFromDatabase(String playlistName, SongLoadCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<Cancion> loadedSongs;
            if (playlistName != null) {
                loadedSongs = db.cancionDao().getSongsByPlaylist(playlistName);
            } else {
                if (db.cancionDao().count() == 0) {
                    db.cancionDao().insertAll(getInitialSongs());
                }
                loadedSongs = db.cancionDao().getAll();
            }
            if (callback != null) {
                runOnUiThread(() -> callback.onSongsLoaded(loadedSongs));
            }
        });
    }

    private void updateAutoCompleteAdapter(List<Cancion> songs) {
        List<String> titulos = new ArrayList<>();
        for (Cancion cancion : songs) {
            titulos.add(cancion.titulo);
        }
        ArrayAdapter<String> autoCompleteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titulos);
        autoCompleteTextView.setAdapter(autoCompleteAdapter);
    }

    private int findPositionByTitle(String title, List<Cancion> listToSearch) {
        for (int i = 0; i < listToSearch.size(); i++) {
            if (listToSearch.get(i).titulo.equals(title)) {
                return i;
            }
        }
        return -1;
    }

    private List<Cancion> getInitialSongs() {
        List<Cancion> songs = new ArrayList<>();
        String[] opciones = {"Nodal_botella", "Arelis_pasado", "Cari Leon_tu", "Gaga_shallow", "Kany_confieso", "Ricardo_fuiste_tu", "Angela_Llorona", "Antonio_HijoDesobediente", "Lupe y Polo_Con llorar nada remedio", "Binomio de oro_Un recuerdo que mata", "Lupe y Polo dos pasajes", "Los balcanes Le hace falta un beso", "Vicente_Hermoso cariño", "Enrique_Por Amarte", "Marcos Barrientos_Dios incomparable", "Pepe Aguilar_Por muejeres como tú", "Aline Barrios_Resucitame"};
        int[] videosRaw = {R.raw.christiannodal_botellatrasbotella, R.raw.arelyshenaolopasadopisado, R.raw.carinleontu, R.raw.shallow, R.raw.kanygarcia_confieso, R.raw.ricardoarjonafuistetu, R.raw.angela_aguilarlallorona, R.raw.el_hijodesobediente, R.raw.conllorarnadaremedio, R.raw.unrecuerdoquemata, R.raw.dospasajes, R.raw.lehacefaltaunbeso, R.raw.hermosocarino, R.raw.poramarte, R.raw.diosincomparable, R.raw.pormujerescomotu, R.raw.resucitame};
        for (int i = 0; i < opciones.length; i++) {
            songs.add(new Cancion(opciones[i], videosRaw[i]));
        }
        return songs;
    }

    private Uri getUriForSong(Cancion cancion) {
        if (cancion.videoUri != null) {
            return Uri.parse(cancion.videoUri);
        } else {
            return Uri.parse("android.resource://" + getPackageName() + "/" + cancion.videoResourceId);
        }
    }
}
