package com.example.mymusical;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "canciones")
public class Cancion {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "titulo")
    public String titulo;

    // Para canciones internas de la app (de res/raw)
    @ColumnInfo(name = "video_resource_id")
    public Integer videoResourceId;

    // Para canciones externas (elegidas por el usuario)
    @ColumnInfo(name = "video_uri")
    public String videoUri;

    // ¡NUEVO! Para la ruta de la miniatura
    @ColumnInfo(name = "thumbnail_path")
    public String thumbnailPath;

    // ¡NUEVO! Para la lista de reproducción
    @ColumnInfo(name = "playlist")
    public String playlist;


    public Cancion() {}

    // Constructor para canciones INTERNAS (de fábrica)
    public Cancion(String titulo, int videoResourceId) {
        this.titulo = titulo;
        this.videoResourceId = videoResourceId;
        this.playlist = "General"; // Asignamos una playlist por defecto
    }
    
    // Constructor para canciones EXTERNAS (del usuario)
    public Cancion(String titulo, String videoUri, String thumbnailPath, String playlist) {
        this.titulo = titulo;
        this.videoUri = videoUri;
        this.thumbnailPath = thumbnailPath;
        this.playlist = playlist;
    }
}
