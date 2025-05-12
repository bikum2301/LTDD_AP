// File: src/main/java/com/example/streamapp/adapter/MusicAdapter.java
package com.example.streamapp.adapter;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.example.streamapp.R;
import com.example.streamapp.model.MediaResponse;

import java.util.Objects;

public class MusicAdapter extends ListAdapter<MediaResponse, MusicAdapter.MusicViewHolder> {

    private OnMusicItemClickListener clickListener;
    private OnMusicItemOptionsListener optionsListener;
    private String currentUsername;
    private static final String TAG = "MusicAdapter";

    public interface OnMusicItemClickListener {
        void onMusicItemClick(MediaResponse musicItem);
    }

    public interface OnMusicItemOptionsListener {
        void onShareClick(MediaResponse musicItem);
        void onDeleteClick(MediaResponse musicItem, int position);
    }

    public MusicAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    public void setOnMusicItemClickListener(OnMusicItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnMusicItemOptionsListener(OnMusicItemOptionsListener listener) {
        this.optionsListener = listener;
    }

    @NonNull
    @Override
    public MusicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_music, parent, false);
        return new MusicViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MusicViewHolder holder, int position) {
        MediaResponse musicItem = getItem(position);
        if (musicItem != null) {
            holder.bind(musicItem, currentUsername, clickListener, optionsListener);
        }
    }

    public static class MusicViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivMusicArtwork;
        private final TextView tvMusicTitle;
        private final TextView tvMusicArtistAndAlbum;
        private final ImageButton ibMusicOptions;
        private final Context context;
        private static final String TAG_VH = "MusicVH";

        public MusicViewHolder(@NonNull View itemView) {
            super(itemView);
            context = itemView.getContext();
            ivMusicArtwork = itemView.findViewById(R.id.ivMusicArtwork);
            tvMusicTitle = itemView.findViewById(R.id.tvMusicTitle);
            tvMusicArtistAndAlbum = itemView.findViewById(R.id.tvMusicArtistAndAlbum);
            ibMusicOptions = itemView.findViewById(R.id.ibMusicOptions);
        }

        public void bind(final MediaResponse musicItem,
                         final String currentUsername,
                         final OnMusicItemClickListener clickListener,
                         final OnMusicItemOptionsListener optionsListener) {

            // 1. Load Artwork
            RequestOptions artworkOptions = new RequestOptions()
                    .placeholder(R.drawable.ic_default_music_artwork)
                    .error(R.drawable.ic_default_music_artwork)
                    .centerCrop(); // Hoặc fitCenter
            Glide.with(context)
                    .load(musicItem.getThumbnailUrl()) // Giả sử thumbnail Url cũng là artwork
                    .apply(artworkOptions)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(ivMusicArtwork);

            // 2. Title
            tvMusicTitle.setText(musicItem.getTitle());

            // 3. Artist and Album
            String artist = !TextUtils.isEmpty(musicItem.getArtist()) ? musicItem.getArtist() : "Unknown Artist";
            String album = !TextUtils.isEmpty(musicItem.getAlbum()) ? musicItem.getAlbum() : ""; // Để trống nếu không có album
            String artistAlbumInfo = artist;
            if (!album.isEmpty()) {
                artistAlbumInfo += " • " + album;
            }
            tvMusicArtistAndAlbum.setText(artistAlbumInfo);

            // Item Click Listener
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onMusicItemClick(musicItem);
                }
            });

            // Options Button Click Listener
            ibMusicOptions.setOnClickListener(v -> {
                if (optionsListener != null) {
                    showOptionsMenu(v, musicItem, currentUsername, optionsListener, getBindingAdapterPosition());
                }
            });
        }

        private void showOptionsMenu(View anchor, final MediaResponse musicItem, String currentUsername,
                                     final OnMusicItemOptionsListener optionsListener, final int position) {
            PopupMenu popup = new PopupMenu(context, anchor);
            popup.getMenuInflater().inflate(R.menu.media_item_options_menu, popup.getMenu()); // Dùng chung menu

            MenuItem deleteAction = popup.getMenu().findItem(R.id.action_media_delete);
            if (deleteAction != null) {
                boolean isOwner = currentUsername != null &&
                        musicItem.getOwnerUsername() != null &&
                        currentUsername.equals(musicItem.getOwnerUsername());
                deleteAction.setVisible(isOwner);
            }

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_media_share) {
                    optionsListener.onShareClick(musicItem);
                    return true;
                } else if (itemId == R.id.action_media_delete) {
                    optionsListener.onDeleteClick(musicItem, position);
                    return true;
                }
                return false;
            });
            popup.show();
        }
    }

    private static final DiffUtil.ItemCallback<MediaResponse> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<MediaResponse>() {
                @Override
                public boolean areItemsTheSame(@NonNull MediaResponse oldItem, @NonNull MediaResponse newItem) {
                    return Objects.equals(oldItem.getId(), newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull MediaResponse oldItem, @NonNull MediaResponse newItem) {
                    return TextUtils.equals(oldItem.getTitle(), newItem.getTitle()) &&
                            TextUtils.equals(oldItem.getThumbnailUrl(), newItem.getThumbnailUrl()) && // Artwork
                            TextUtils.equals(oldItem.getArtist(), newItem.getArtist()) &&
                            TextUtils.equals(oldItem.getAlbum(), newItem.getAlbum()) &&
                            TextUtils.equals(oldItem.getOwnerUsername(), newItem.getOwnerUsername()) &&
                            oldItem.isPublic() == newItem.isPublic();
                }
            };
}