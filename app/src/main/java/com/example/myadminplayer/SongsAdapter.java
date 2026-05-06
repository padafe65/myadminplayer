package com.example.myadminplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

public class SongsAdapter extends RecyclerView.Adapter<SongsAdapter.SongViewHolder> {

    private List<Cancion> songList;
    private final OnSongListener listener;
    private final boolean showAdminControls;

    public interface OnSongListener {
        void onSongClick(Cancion cancion);
        void onEditClick(Cancion cancion);
        void onDeleteClick(Cancion cancion);
    }

    public SongsAdapter(List<Cancion> songList, OnSongListener listener, boolean showAdminControls) {
        this.songList = songList;
        this.listener = listener;
        this.showAdminControls = showAdminControls;
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
        holder.playlist.setText("Playlist: " + currentSong.playlist);

        if (currentSong.thumbnailPath != null && new File(currentSong.thumbnailPath).exists()) {
            Glide.with(holder.itemView.getContext())
                    .load(new File(currentSong.thumbnailPath))
                    .into(holder.thumbnail);
        } else {
            // Si no hay miniatura guardada, intentamos cargar un frame del video usando Glide
            android.net.Uri videoUri = null;
            if (currentSong.videoUri != null) {
                videoUri = android.net.Uri.parse(currentSong.videoUri);
            } else if (currentSong.videoResourceId != null) {
                videoUri = android.net.Uri.parse("android.resource://" + holder.itemView.getContext().getPackageName() + "/" + currentSong.videoResourceId);
            }

            if (videoUri != null) {
                Glide.with(holder.itemView.getContext())
                        .asBitmap()
                        .load(videoUri)
                        .placeholder(R.drawable.music)
                        .error(R.drawable.music)
                        .into(holder.thumbnail);
            } else {
                holder.thumbnail.setImageResource(R.drawable.music);
            }
        }

        if (showAdminControls) {
            holder.editButton.setVisibility(View.VISIBLE);
            holder.deleteButton.setVisibility(View.VISIBLE);
            holder.editButton.setOnClickListener(v -> listener.onEditClick(currentSong));
            holder.deleteButton.setOnClickListener(v -> listener.onDeleteClick(currentSong));
            holder.itemView.setOnClickListener(null); // No hacer nada en el clic normal
        } else {
            holder.editButton.setVisibility(View.GONE);
            holder.deleteButton.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> listener.onSongClick(currentSong));
        }
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    public void setSongs(List<Cancion> songs) {
        this.songList = songs;
        notifyDataSetChanged();
    }

    public static class SongViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView title, playlist;
        ImageButton editButton, deleteButton;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.iv_thumbnail);
            title = itemView.findViewById(R.id.tv_song_title);
            playlist = itemView.findViewById(R.id.tv_playlist_name);
            editButton = itemView.findViewById(R.id.btn_edit_song);
            deleteButton = itemView.findViewById(R.id.btn_delete_song);
        }
    }
}
