package com.example.myadminplayer;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ManageSongsActivity extends AppCompatActivity implements SongsAdapter.OnSongListener {

    private RecyclerView recyclerView;
    private SongsAdapter adapter;
    private List<Cancion> songList = new ArrayList<>();
    private AppDatabase db;

    private Cancion currentSongForEdit;
    private ImageView currentDialogThumbnail;
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_songs);

        // Inicializar el launcher para el selector de imágenes
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && currentSongForEdit != null && currentDialogThumbnail != null) {
                        // Copiar la imagen seleccionada al almacenamiento interno y obtener la nueva ruta
                        String newThumbnailPath = copyImageToInternalStorage(uri);
                        if(newThumbnailPath != null) {
                            currentSongForEdit.thumbnailPath = newThumbnailPath;
                            // Actualizar la vista previa de la imagen en el diálogo
                            Glide.with(this).load(new File(newThumbnailPath)).into(currentDialogThumbnail);
                        }
                    }
                });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.rv_songs);
        db = AppDatabase.getDatabase(getApplicationContext());

        setupRecyclerView();
        loadSongs();
    }

    private void setupRecyclerView() {
        adapter = new SongsAdapter(songList, this, true); // True para mostrar los controles de admin
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
    public void onSongClick(Cancion cancion) {
        // En esta pantalla, un clic en la canción no hace nada. 
    }

    @Override
    public void onEditClick(Cancion cancion) {
        currentSongForEdit = cancion; // Guardamos la canción que se está editando

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_song, null);
        builder.setView(dialogView);

        final ImageView thumbnailView = dialogView.findViewById(R.id.iv_dialog_thumbnail);
        currentDialogThumbnail = thumbnailView; // Guardamos la referencia al ImageView del diálogo

        final EditText titleInput = dialogView.findViewById(R.id.et_song_title);
        final AutoCompleteTextView playlistInput = dialogView.findViewById(R.id.et_playlist_name);

        // Cargar géneros existentes de la base de datos
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<String> playlists = db.cancionDao().getAllPlaylists();
            runOnUiThread(() -> {
                if (playlists != null && !playlists.isEmpty()) {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_dropdown_item_1line, playlists);
                    playlistInput.setAdapter(adapter);
                    
                    // Mostrar sugerencias al tocar el campo
                    playlistInput.setOnClickListener(v -> playlistInput.showDropDown());
                    playlistInput.setOnFocusChangeListener((v, hasFocus) -> {
                        if (hasFocus) playlistInput.showDropDown();
                    });
                }
            });
        });

        // Hacemos la miniatura clickeable para cambiarla
        thumbnailView.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        if (cancion.thumbnailPath != null && new File(cancion.thumbnailPath).exists()) {
            Glide.with(this).load(new File(cancion.thumbnailPath)).into(thumbnailView);
        } else {
            // Si no hay miniatura, intentar cargar un frame en el diálogo
            Uri videoUri = null;
            if (cancion.videoUri != null) {
                videoUri = Uri.parse(cancion.videoUri);
            } else if (cancion.videoResourceId != null) {
                videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + cancion.videoResourceId);
            }
            
            if (videoUri != null) {
                Glide.with(this).asBitmap().load(videoUri).placeholder(R.drawable.music).into(thumbnailView);
            } else {
                thumbnailView.setImageResource(R.drawable.music);
            }
        }

        titleInput.setText(cancion.titulo);
        playlistInput.setText(cancion.playlist);
        
        builder.setTitle("Editar Video");
        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String newTitle = titleInput.getText().toString();
            String newPlaylist = playlistInput.getText().toString();

            if (newPlaylist.isEmpty()) {
                newPlaylist = "General";
            }

            if (!newTitle.isEmpty()) {
                currentSongForEdit.titulo = newTitle;
                currentSongForEdit.playlist = newPlaylist;
                updateSongInDb(currentSongForEdit);
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
            loadSongs();
            runOnUiThread(() -> Toast.makeText(ManageSongsActivity.this, "Video actualizado", Toast.LENGTH_SHORT).show());
        });
    }

    private void deleteSongFromDb(Cancion cancion) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            if (cancion.thumbnailPath != null) {
                new File(cancion.thumbnailPath).delete();
            }
            db.cancionDao().deleteSong(cancion);
            loadSongs();
            runOnUiThread(() -> Toast.makeText(ManageSongsActivity.this, "Video eliminado", Toast.LENGTH_SHORT).show());
        });
    }

    private String copyImageToInternalStorage(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            File internal_storage_path = new File(getFilesDir(), "thumbnails");
            if (!internal_storage_path.exists()) {
                internal_storage_path.mkdirs();
            }

            File file = new File(internal_storage_path, System.currentTimeMillis() + ".jpg");

            try (OutputStream outputStream = new FileOutputStream(file)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = inputStream.read(buf)) > 0) {
                    outputStream.write(buf, 0, len);
                }
            }
            inputStream.close();
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
