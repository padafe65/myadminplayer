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

    // Constructor vacío (Room lo necesita)
    public Cancion() {}

    // Constructor para canciones INTERNAS
    public Cancion(String titulo, int videoResourceId) {
        this.titulo = titulo;
        this.videoResourceId = videoResourceId;
        this.videoUri = null; // Nos aseguramos de que el otro campo sea nulo
    }
    
    // Constructor para canciones EXTERNAS
    public Cancion(String titulo, String videoUri) {
        this.titulo = titulo;
        this.videoResourceId = null; // Nos aseguramos de que el otro campo sea nulo
        this.videoUri = videoUri;
    }
}
