package com.example.myadminplayer;

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
    List<Cancion> getSongsByPlaylist(String playlistName);

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

    @Query("UPDATE canciones SET playlist = :newPlaylistName WHERE playlist = :oldPlaylistName")
    void renamePlaylist(String oldPlaylistName, String newPlaylistName);

    @Query("UPDATE canciones SET playlist_image_uri = :imageUri WHERE playlist = :playlistName")
    void updatePlaylistImage(String playlistName, String imageUri);

    @Query("SELECT DISTINCT playlist FROM canciones WHERE playlist IS NOT NULL AND playlist != ''")
    List<String> getAllPlaylists();

    @Query("DELETE FROM canciones WHERE playlist = :playlistName AND (titulo IS NULL OR titulo = '')")
    void deleteDummySongsFromPlaylist(String playlistName);
}
