package com.example.myadminplayer;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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
import android.util.Log;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

        Toolbar toolbar = findViewById(R.id.toolbar_playlists);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Mis Playlists");
        }

        recyclerView = findViewById(R.id.rv_playlists);
        db = AppDatabase.getDatabase(getApplicationContext());

        setupRecyclerView();
        loadPlaylists();

        FloatingActionButton fabAddPlaylist = findViewById(R.id.fab_add_playlist);
        if (fabAddPlaylist != null) {
            fabAddPlaylist.setOnClickListener(v -> showAddPlaylistDialog());
        }
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
    }

    private void loadPlaylists() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                List<Cancion> allSongs = db.cancionDao().getAll();
                List<String> allPlaylists = db.cancionDao().getAllPlaylists();

                Map<String, List<Cancion>> playlistMap = new HashMap<>();
                if (allPlaylists != null) {
                    for (String name : allPlaylists) {
                        playlistMap.put(name, new ArrayList<>());
                    }
                }

                if (allSongs != null) {
                    for (Cancion c : allSongs) {
                        if (c.playlist != null && !c.playlist.isEmpty() && c.titulo != null && !c.titulo.isEmpty()) {
                            List<Cancion> list = playlistMap.get(c.playlist);
                            if (list != null) {
                                list.add(c);
                            }
                        }
                    }
                }

                List<Map.Entry<String, List<Cancion>>> playlistGroups = new ArrayList<>(playlistMap.entrySet());

                runOnUiThread(() -> {
                    adapter = new PlaylistsAdapter(playlistGroups, this, this);
                    recyclerView.setAdapter(adapter);
                });
            } catch (Exception e) {
                Log.e("PlaylistsActivity", "Error al cargar playlists", e);
            }
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

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<String> playlists = db.cancionDao().getAllPlaylists();
            runOnUiThread(() -> {
                if (playlists != null && !playlists.isEmpty()) {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_dropdown_item_1line, playlists);
                    input.setAdapter(adapter);
                }
            });
        });

        builder.setTitle("Nueva Playlist")
                .setPositiveButton("Crear", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        executor.execute(() -> {
                            Cancion dummy = new Cancion();
                            dummy.playlist = name;
                            dummy.titulo = "";
                            db.cancionDao().insertSong(dummy);
                            runOnUiThread(this::loadPlaylists);
                        });
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onPlaylistLongClick(String playlistName) {
        this.currentPlaylistName = playlistName;
        new AlertDialog.Builder(this)
                .setTitle(playlistName)
                .setItems(new String[]{"Renombrar", "Eliminar", "Cambiar Imagen"}, (dialog, item) -> {
                    if (item == 0) showRenamePlaylistDialog(playlistName);
                    else if (item == 1) showDeletePlaylistDialog(playlistName);
                    else if (item == 2) openImagePicker();
                }).show();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        openImageLauncher.launch(intent);
    }

    private void showRenamePlaylistDialog(String oldName) {
        final EditText input = new EditText(this);
        input.setText(oldName);
        new AlertDialog.Builder(this)
                .setTitle("Renombrar")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            db.cancionDao().renamePlaylist(oldName, newName);
                            runOnUiThread(this::loadPlaylists);
                        });
                    }
                }).show();
    }

    private void showDeletePlaylistDialog(String name) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar")
                .setMessage("¿Eliminar '" + name + "'? Las canciones no se borrarán.")
                .setPositiveButton("Eliminar", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        db.cancionDao().deleteDummySongsFromPlaylist(name);
                        db.cancionDao().renamePlaylist(name, "General");
                        runOnUiThread(this::loadPlaylists);
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
