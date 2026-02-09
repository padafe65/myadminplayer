package com.example.mymusical;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class PlaylistsActivity extends AppCompatActivity implements PlaylistsAdapter.OnPlaylistClickListener {

    private RecyclerView recyclerView;
    private PlaylistsAdapter adapter;
    private AppDatabase db;

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

            Map<String, List<Cancion>> playlistMap = allSongs.stream()
                    .filter(c -> c.playlist != null && !c.playlist.isEmpty())
                    .collect(Collectors.groupingBy(c -> c.playlist));

            List<Map.Entry<String, List<Cancion>>> playlistGroups = new ArrayList<>(playlistMap.entrySet());

            runOnUiThread(() -> {
                adapter = new PlaylistsAdapter(playlistGroups, this);
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
        builder.setTitle("Crear Nueva Playlist");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Nombre de la playlist");
        builder.setView(input);

        builder.setPositiveButton("Crear", (dialog, which) -> {
            String playlistName = input.getText().toString();
            if (!playlistName.isEmpty()) {
                loadPlaylists();
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
}
