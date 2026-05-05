package com.example.myadminplayer;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class PlaylistsActivity extends AppCompatActivity implements PlaylistsAdapter.OnPlaylistClickListener, PlaylistsAdapter.OnPlaylistLongClickListener {

    private RecyclerView recyclerView;
    private PlaylistsAdapter adapter;
    private AppDatabase db;
    private String currentPlaylistName;

    private final ActivityResultLauncher<Intent> openImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        // Persist permission to read the URI
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        ExecutorService executor = Executors.newSingleThreadExecutor();
                        executor.execute(() -> {
                            db.cancionDao().updatePlaylistImage(currentPlaylistName, uri.toString());
                            runOnUiThread(this::loadPlaylists);
                        });
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlists);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.rv_playlists);
        FloatingActionButton fabAddPlaylist = findViewById(R.id.fab_add_playlist);
        db = AppDatabase.getDatabase(getApplicationContext());

        setupRecyclerView();
        loadPlaylists();

        fabAddPlaylist.setOnClickListener(v -> showAddPlaylistDialog());
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
    }

    private void loadPlaylists() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<Cancion> allSongs = db.cancionDao().getAll();
            List<String> allPlaylists = db.cancionDao().getAllPlaylists();

            Map<String, List<Cancion>> playlistMap = allSongs.stream()
                    .filter(c -> c.playlist != null && !c.playlist.isEmpty() && c.titulo != null && !c.titulo.isEmpty())
                    .collect(Collectors.groupingBy(c -> c.playlist, 
                                                    HashMap::new, 
                                                    Collectors.toCollection(ArrayList::new)));

            for (String playlistName : allPlaylists) {
                playlistMap.putIfAbsent(playlistName, new ArrayList<>());
            }

            List<Map.Entry<String, List<Cancion>>> playlistGroups = new ArrayList<>(playlistMap.entrySet());

            runOnUiThread(() -> {
                adapter = new PlaylistsAdapter(playlistGroups, this, this);
                recyclerView.setAdapter(adapter);
            });
        });
    }

    @Override
    public void onPlaylistClick(String playlistName) {
        Intent intent = new Intent(this, PlaylistSongsActivity.class);
        intent.putExtra("PLAYLIST_NAME", playlistName);
        startActivity(intent);
    }

    private void showAddPlaylistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_playlist, null);
        builder.setView(dialogView);

        final AutoCompleteTextView input = dialogView.findViewById(R.id.et_playlist_name);

        // Cargar géneros existentes
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<String> playlists = db.cancionDao().getAllPlaylists();
            runOnUiThread(() -> {
                if (playlists != null && !playlists.isEmpty()) {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_dropdown_item_1line, playlists);
                    input.setAdapter(adapter);
                    
                    input.setOnClickListener(v -> input.showDropDown());
                    input.setOnFocusChangeListener((v, hasFocus) -> {
                        if (hasFocus) input.showDropDown();
                    });
                }
            });
        });

        builder.setTitle("Crear Nueva Playlist");
        builder.setPositiveButton("Crear", (dialog, which) -> {
            String playlistName = input.getText().toString().trim();
            if (!playlistName.isEmpty()) {
                executor.execute(() -> {
                    // Insert a dummy song to create the playlist
                    Cancion dummySong = new Cancion();
                    dummySong.playlist = playlistName;
                    dummySong.titulo = ""; //Empty title for dummy song
                    db.cancionDao().insertSong(dummySong);
                    runOnUiThread(this::loadPlaylists);
                });
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onPlaylistLongClick(String playlistName) {
        this.currentPlaylistName = playlistName;
        final CharSequence[] items = {"Renombrar", "Eliminar", "Cambiar Imagen"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(playlistName);
        builder.setItems(items, (dialog, item) -> {
            if (items[item].equals("Renombrar")) {
                showRenamePlaylistDialog(playlistName);
            } else if (items[item].equals("Eliminar")) {
                showDeletePlaylistDialog(playlistName);
            } else if (items[item].equals("Cambiar Imagen")) {
                openImagePicker();
            }
        });
        builder.show();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        openImageLauncher.launch(intent);
    }

    private void showRenamePlaylistDialog(String oldPlaylistName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Renombrar Playlist");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(oldPlaylistName);
        builder.setView(input);

        builder.setPositiveButton("Renombrar", (dialog, which) -> {
            String newPlaylistName = input.getText().toString().trim();
            if (!newPlaylistName.isEmpty() && !newPlaylistName.equals(oldPlaylistName)) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    db.cancionDao().renamePlaylist(oldPlaylistName, newPlaylistName);
                    runOnUiThread(this::loadPlaylists);
                });
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showDeletePlaylistDialog(String playlistName) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar Playlist")
                .setMessage("¿Estás seguro de que quieres eliminar la playlist '" + playlistName + "'? Las canciones no se borrarán.")
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.execute(() -> {
                        db.cancionDao().deleteDummySongsFromPlaylist(playlistName);
                        db.cancionDao().renamePlaylist(playlistName, "General");
                        runOnUiThread(this::loadPlaylists);
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
