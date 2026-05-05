package com.example.myadminplayer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaylistSongsActivity extends AppCompatActivity implements SongsAdapter.OnSongListener {

    private RecyclerView recyclerView;
    private SongsAdapter adapter;
    private List<Cancion> songList = new ArrayList<>();
    private AppDatabase db;
    private String playlistName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_songs);

        playlistName = getIntent().getStringExtra("PLAYLIST_NAME");

        Toolbar toolbar = findViewById(R.id.toolbar_playlist_songs);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(playlistName);
        }

        recyclerView = findViewById(R.id.rv_playlist_songs);
        db = AppDatabase.getDatabase(getApplicationContext());

        setupRecyclerView();
        loadSongs();
    }

    private void setupRecyclerView() {
        adapter = new SongsAdapter(songList, this, false); // False para ocultar los controles de admin
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.setAdapter(adapter);
    }

    private void loadSongs() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            if (playlistName != null) {
                songList = db.cancionDao().getSongsByPlaylist(playlistName);
                runOnUiThread(() -> adapter.setSongs(songList));
            }
        });
    }

    @Override
    public void onSongClick(Cancion cancion) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("PLAYLIST_TO_PLAY", playlistName);
        intent.putExtra("SONG_TO_PLAY_TITLE", cancion.titulo);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public void onEditClick(Cancion cancion) {
        // No se usa en esta pantalla
    }

    @Override
    public void onDeleteClick(Cancion cancion) {
        // No se usa en esta pantalla
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
