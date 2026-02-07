package com.example.mymusical;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CancionDao {

    @Query("SELECT * FROM canciones")
    List<Cancion> getAll();

    @Insert
    void insertAll(List<Cancion> canciones);

    @Insert
    void insertSong(Cancion cancion); // Método que faltaba

    @Update
    void updateSong(Cancion cancion); // ¡Nuevo!

    @Delete
    void deleteSong(Cancion cancion); // ¡Nuevo!

    @Query("SELECT COUNT(*) FROM canciones")
    int count();
}
