package com.example.mymusical;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SongsAdapter extends RecyclerView.Adapter<SongsAdapter.SongViewHolder> {

    private List<Cancion> songList;
    private final OnSongListener listener;

    // Interfaz para comunicar los clics a la Activity
    public interface OnSongListener {
        void onEditClick(Cancion cancion);
        void onDeleteClick(Cancion cancion);
    }

    public SongsAdapter(List<Cancion> songList, OnSongListener listener) {
        this.songList = songList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.song_item, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Cancion currentSong = songList.get(position);
        holder.title.setText(currentSong.titulo);

        // Asignamos los listeners a los botones
        holder.editButton.setOnClickListener(v -> listener.onEditClick(currentSong));
        holder.deleteButton.setOnClickListener(v -> listener.onDeleteClick(currentSong));
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    // Método para actualizar la lista de canciones desde la Activity
    public void setSongs(List<Cancion> songs) {
        this.songList = songs;
        notifyDataSetChanged();
    }

    // ViewHolder que representa cada fila
    public static class SongViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        ImageButton editButton, deleteButton;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_song_title);
            editButton = itemView.findViewById(R.id.btn_edit_song);
            deleteButton = itemView.findViewById(R.id.btn_delete_song);
        }
    }
}
