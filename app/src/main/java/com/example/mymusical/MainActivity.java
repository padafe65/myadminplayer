package com.example.mymusical;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerView;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuInflater;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;

    Button play_pause, btn_repetir, btnStop, btnAnterior, btnSiguiente, btnRetroceder10s, btnAdelantar10s;
    AutoCompleteTextView autoCompleteTextView;
    FloatingActionButton fabAddSong;
    ArrayAdapter<String> adapter;
    LottieAnimationView animationView;
    TextInputLayout menu_canciones;
    FrameLayout videoContainer;
    ImageView videoBackground;

    private List<Cancion> cancionesList = new ArrayList<>();
    private AppDatabase db;
    int posicionActual = -1;
    private int autoplayCounter = 0;

    private final ActivityResultLauncher<Intent> openFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
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

        playerView.setVisibility(View.INVISIBLE);
        fabAddSong.setOnClickListener(v -> openFilePicker());

        // Limpiar el campo de texto al tocarlo
        autoCompleteTextView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                autoCompleteTextView.setText("");
            }
            return false;
        });

        autoCompleteTextView.setOnItemClickListener((parent, view, position, id) -> {
            String selection = (String) parent.getItemAtPosition(position);
            int nuevaPosicion = findPositionByTitle(selection);
            if (nuevaPosicion != -1) {
                autoplayCounter = 0; 
                reproducirNuevaCancion(nuevaPosicion);
                InputMethodManager in = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (in != null) {
                    in.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0);
                }
                autoCompleteTextView.clearFocus();
            }
        });
    }

    private void initializeMediaController() {
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                playerView.setPlayer(mediaController);
                setupPlayerWithPlaylist(); // Cargamos la playlist cuando el controlador está listo
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void setupPlayerWithPlaylist() {
        loadSongsFromDatabase(() -> {
            if (mediaController != null && !cancionesList.isEmpty()) {
                List<MediaItem> mediaItems = new ArrayList<>();
                for (Cancion cancion : cancionesList) {
                    mediaItems.add(MediaItem.fromUri(getUriForSong(cancion)));
                }
                mediaController.setMediaItems(mediaItems);
                mediaController.prepare();

                // Añadimos el listener DESPUÉS de preparar la lista
                mediaController.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        play_pause.setBackgroundResource(isPlaying ? R.drawable.pausa : R.drawable.reproducir);
                    }

                    @Override
                    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                        if (mediaController == null) return;
                        posicionActual = mediaController.getCurrentMediaItemIndex();
                        if (posicionActual != -1) {
                            autoCompleteTextView.setText(cancionesList.get(posicionActual).titulo, false);
                        }
                    }

                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        if (playbackState == Player.STATE_ENDED) {
                            if (mediaController.hasNextMediaItem() && autoplayCounter < 5) {
                                autoplayCounter++;
                                mediaController.seekToNextMediaItem();
                            } else {
                                if (autoplayCounter >= 5) {
                                    Toast.makeText(MainActivity.this, "Fin de la reproducción automática", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }
                });
            }
        });
    }

    private void releaseMediaController() {
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
        mediaController = null;
        controllerFuture = null;
    }

    private void reproducirNuevaCancion(int nuevaPosicion) {
        if (mediaController == null) return;
        videoBackground.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        mediaController.seekTo(nuevaPosicion, 0);
        mediaController.play();
        Toast.makeText(this, "Reproduciendo: " + cancionesList.get(nuevaPosicion).titulo, Toast.LENGTH_SHORT).show();
    }

    public void Siguiente(View view) {
        if (mediaController == null) return;
        if (view != null) { 
            autoplayCounter = 0;
        }
        if (mediaController.hasNextMediaItem()) {
            mediaController.seekToNextMediaItem();
        } else {
            Toast.makeText(this, "Última canción de la lista", Toast.LENGTH_SHORT).show();
        }
    }

    public void Anterior(View view) {
        if (mediaController == null) return;
        autoplayCounter = 0;
        if (mediaController.hasPreviousMediaItem()) {
            mediaController.seekToPreviousMediaItem();
        } else {
            Toast.makeText(this, "Primera canción de la lista", Toast.LENGTH_SHORT).show();
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
        autoplayCounter = 0;
        playerView.setVisibility(View.INVISIBLE);
        videoBackground.setVisibility(View.VISIBLE);
        posicionActual = -1;
        autoCompleteTextView.setText("", false);
        Toast.makeText(this, "Reproducción detenida", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_manage_songs) {
            Intent intent = new Intent(this, ManageSongsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    public void Repetir(View view) {
        if (mediaController == null) return;
        boolean repetir = mediaController.getRepeatMode() == Player.REPEAT_MODE_ONE;
        if (repetir) {
            mediaController.setRepeatMode(Player.REPEAT_MODE_OFF);
            btn_repetir.setBackgroundResource(R.drawable.no_repetir);
            Toast.makeText(this, "Repetir desactivado", Toast.LENGTH_SHORT).show();
        } else {
            mediaController.setRepeatMode(Player.REPEAT_MODE_ONE);
            btn_repetir.setBackgroundResource(R.drawable.repetir);
            Toast.makeText(this, "Repetir activado", Toast.LENGTH_SHORT).show();
        }
    }
    
    public void adelantar10s(View view) {
        if (mediaController != null && mediaController.isCurrentMediaItemSeekable()) {
            mediaController.seekForward();
        }
    }

    public void retroceder10s(View view) {
        if (mediaController != null && mediaController.isCurrentMediaItemSeekable()) {
            mediaController.seekBack();
        }
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
        videoBackground.setVisibility(View.GONE);
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

    private void loadSongsFromDatabase(Runnable onFinished) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            if (db.cancionDao().count() == 0) {
                db.cancionDao().insertAll(getInitialSongs());
            }
            cancionesList = db.cancionDao().getAll();
            runOnUiThread(() -> {
                List<String> titulos = new ArrayList<>();
                for (Cancion cancion : cancionesList) {
                    titulos.add(cancion.titulo);
                }
                adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titulos);
                autoCompleteTextView.setAdapter(adapter);
                if (onFinished != null) {
                    onFinished.run();
                }
            });
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        openFileLauncher.launch(intent);
    }

    private void showAddSongDialog(Uri uri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Añadir Título");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String title = input.getText().toString();
            if (!title.isEmpty()) {
                saveNewSong(title, uri.toString());
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void saveNewSong(String title, String uriString) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Cancion newSong = new Cancion(title, uriString);
            db.cancionDao().insertSong(newSong);
            // Recargamos la lista y la playlist en el reproductor
            runOnUiThread(this::setupPlayerWithPlaylist);
        });
    }

    private int findPositionByTitle(String title) {
        for (int i = 0; i < cancionesList.size(); i++) {
            if (cancionesList.get(i).titulo.equals(title)) {
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
