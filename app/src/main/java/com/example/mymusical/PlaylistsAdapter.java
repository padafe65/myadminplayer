package com.example.mymusical;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;
import java.util.Map;

// Adaptador para mostrar la lista de Playlists
public class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistsAdapter.PlaylistViewHolder> {

    private final List<Map.Entry<String, List<Cancion>>> playlistGroups;
    private final OnPlaylistClickListener listener;

    // Interfaz para comunicar los clics a la Activity
    public interface OnPlaylistClickListener {
        void onPlaylistClick(String playlistName);
    }

    public PlaylistsAdapter(List<Map.Entry<String, List<Cancion>>> playlistGroups, OnPlaylistClickListener listener) {
        this.playlistGroups = playlistGroups;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.playlist_item, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        Map.Entry<String, List<Cancion>> playlistEntry = playlistGroups.get(position);
        String playlistName = playlistEntry.getKey();
        List<Cancion> songsInPlaylist = playlistEntry.getValue();
        int songCount = songsInPlaylist.size();

        holder.playlistName.setText(playlistName);
        holder.songCount.setText(songCount + " Videos");

        if (!songsInPlaylist.isEmpty() && songsInPlaylist.get(0).thumbnailPath != null) {
            File thumbnailFile = new File(songsInPlaylist.get(0).thumbnailPath);
            if (thumbnailFile.exists()) {
                Glide.with(holder.itemView.getContext())
                        .load(thumbnailFile)
                        .into(holder.thumbnail);
            }
        } else {
            holder.thumbnail.setImageResource(R.drawable.music);
        }

        // Hacemos que toda la tarjeta sea clickeable
        holder.itemView.setOnClickListener(v -> listener.onPlaylistClick(playlistName));
    }

    @Override
    public int getItemCount() {
        return playlistGroups.size();
    }

    public static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView playlistName, songCount;

        public PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.iv_playlist_thumbnail);
            playlistName = itemView.findViewById(R.id.tv_playlist_name);
            songCount = itemView.findViewById(R.id.tv_song_count);
        }
    }
}
