package com.example.myadminplayer;

import android.net.Uri;
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
    private final OnPlaylistLongClickListener longClickListener;

    // Interfaz para comunicar los clics a la Activity
    public interface OnPlaylistClickListener {
        void onPlaylistClick(String playlistName);
    }

    public interface OnPlaylistLongClickListener {
        void onPlaylistLongClick(String playlistName);
    }

    public PlaylistsAdapter(List<Map.Entry<String, List<Cancion>>> playlistGroups, OnPlaylistClickListener listener, OnPlaylistLongClickListener longClickListener) {
        this.playlistGroups = playlistGroups;
        this.listener = listener;
        this.longClickListener = longClickListener;
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

        // Mostrar la imagen de la playlist si existe, si no, buscamos la primera miniatura disponible
        if (!songsInPlaylist.isEmpty()) {
            boolean imageLoaded = false;
            
            // 1. Intentar cargar la imagen personalizada de la playlist (si existe en algún video del grupo)
            for (Cancion s : songsInPlaylist) {
                if (s.playlistImageUri != null) {
                    Glide.with(holder.itemView.getContext())
                            .load(Uri.parse(s.playlistImageUri))
                            .placeholder(R.drawable.music)
                            .into(holder.thumbnail);
                    imageLoaded = true;
                    break;
                }
            }

            // 2. Si no hay imagen de playlist, buscar el primer video que tenga una miniatura generada
            if (!imageLoaded) {
                for (Cancion s : songsInPlaylist) {
                    if (s.thumbnailPath != null && new File(s.thumbnailPath).exists()) {
                        Glide.with(holder.itemView.getContext())
                                .load(new File(s.thumbnailPath))
                                .placeholder(R.drawable.music)
                                .into(holder.thumbnail);
                        imageLoaded = true;
                        break;
                    }
                }
            }

            // 3. Si aún no cargamos nada, intentamos sacar un frame del primer video real
            if (!imageLoaded) {
                Cancion firstSong = songsInPlaylist.get(0);
                Uri videoUri = null;
                if (firstSong.videoUri != null) {
                    videoUri = Uri.parse(firstSong.videoUri);
                } else if (firstSong.videoResourceId != null) {
                    videoUri = Uri.parse("android.resource://" + holder.itemView.getContext().getPackageName() + "/" + firstSong.videoResourceId);
                }

                if (videoUri != null) {
                    Glide.with(holder.itemView.getContext())
                            .asBitmap()
                            .load(videoUri)
                            .placeholder(R.drawable.music)
                            .into(holder.thumbnail);
                } else {
                    holder.thumbnail.setImageResource(R.drawable.music);
                }
            }
        } else {
            holder.thumbnail.setImageResource(R.drawable.music);
        }

        // Hacemos que toda la tarjeta sea clickeable
        holder.itemView.setOnClickListener(v -> listener.onPlaylistClick(playlistName));
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onPlaylistLongClick(playlistName);
                return true;
            }
            return false;
        });
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
