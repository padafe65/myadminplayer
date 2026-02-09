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

    @Query("SELECT * FROM canciones WHERE playlist = :playlistName")
    List<Cancion> getSongsByPlaylist(String playlistName); // ¡Nuevo!

    @Insert
    void insertAll(List<Cancion> canciones);

    @Insert
    void insertSong(Cancion cancion);

    @Update
    void updateSong(Cancion cancion);

    @Delete
    void deleteSong(Cancion cancion);

    @Query("SELECT COUNT(*) FROM canciones")
    int count();
}
