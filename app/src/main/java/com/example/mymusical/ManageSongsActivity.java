package com.example.mymusical;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ManageSongsActivity extends AppCompatActivity implements SongsAdapter.OnSongListener {

    private RecyclerView recyclerView;
    private SongsAdapter adapter;
    private List<Cancion> songList = new ArrayList<>();
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_songs);

        // Habilitar el botón de "Atrás" en la barra de acción
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.rv_songs);
        db = AppDatabase.getDatabase(getApplicationContext());

        setupRecyclerView();
        loadSongs();
    }

    private void setupRecyclerView() {
        adapter = new SongsAdapter(songList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadSongs() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            songList = db.cancionDao().getAll();
            runOnUiThread(() -> adapter.setSongs(songList));
        });
    }

    @Override
    public void onEditClick(Cancion cancion) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Editar Título del Video");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(cancion.titulo);
        builder.setView(input);

        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String newTitle = input.getText().toString();
            if (!newTitle.isEmpty()) {
                cancion.titulo = newTitle;
                updateSongInDb(cancion);
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    @Override
    public void onDeleteClick(Cancion cancion) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar Video")
                .setMessage("¿Desea eliminar este video?")
                .setPositiveButton("Sí, Eliminar", (dialog, which) -> deleteSongFromDb(cancion))
                .setNegativeButton("No", null)
                .show();
    }

    private void updateSongInDb(Cancion cancion) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            db.cancionDao().updateSong(cancion);
            // Recargamos la lista para reflejar el cambio
            loadSongs();
            runOnUiThread(() -> Toast.makeText(ManageSongsActivity.this, "Video actualizado", Toast.LENGTH_SHORT).show());
        });
    }

    private void deleteSongFromDb(Cancion cancion) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            db.cancionDao().deleteSong(cancion);
            // Recargamos la lista para reflejar el cambio
            loadSongs();
            runOnUiThread(() -> Toast.makeText(ManageSongsActivity.this, "Video eliminado", Toast.LENGTH_SHORT).show());
        });
    }

    // Para que el botón de "Atrás" de la barra funcione
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
