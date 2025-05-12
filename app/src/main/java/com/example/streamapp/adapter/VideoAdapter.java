// File: src/main/java/com/example/streamapp/adapter/VideoAdapter.java
package com.example.streamapp.adapter;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton; // Import ImageButton
import android.widget.ImageView;  // Import ImageView
import android.widget.PopupMenu;   // Import PopupMenu
import android.widget.TextView;    // Import TextView
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter; // Sử dụng ListAdapter cho DiffUtil tiện lợi hơn
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.example.streamapp.R;
// Sử dụng ListItemVideoBinding nếu bạn bật viewBinding cho list_item_video.xml
// Nếu không, dùng findViewById như bên dưới.
// import com.example.streamapp.databinding.ListItemVideoBinding;
import com.example.streamapp.model.MediaResponse;

import java.util.Locale;
import java.util.Objects;


// Sử dụng ListAdapter sẽ giúp quản lý danh sách và DiffUtil dễ dàng hơn
public class VideoAdapter extends ListAdapter<MediaResponse, VideoAdapter.VideoViewHolder> {

    private OnVideoItemClickListener clickListener;
    private OnVideoItemOptionsListener optionsListener;
    private String currentUsername;
    private static final String TAG = "VideoAdapter";

    public interface OnVideoItemClickListener {
        void onVideoItemClick(MediaResponse videoItem);
    }

    public interface OnVideoItemOptionsListener {
        void onShareClick(MediaResponse videoItem);
        void onDeleteClick(MediaResponse videoItem, int position); // Vẫn cần position để xóa khỏi list
    }

    public VideoAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
        // Không cần notify vì ListAdapter sẽ tự xử lý khi list thay đổi
    }

    public void setOnVideoItemClickListener(OnVideoItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnVideoItemOptionsListener(OnVideoItemOptionsListener listener) {
        this.optionsListener = listener;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        MediaResponse videoItem = getItem(position);
        if (videoItem != null) {
            holder.bind(videoItem, currentUsername, clickListener, optionsListener);
        }
    }

    // ViewHolder Class
    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivVideoThumbnail;
        private final TextView tvVideoDuration;
        private final ImageView ivChannelAvatar;
        private final TextView tvVideoTitle;
        private final TextView tvVideoChannelAndStats;
        private final ImageButton ibVideoOptions;
        private final Context context;
        private static final String TAG_VH = "VideoVH";


        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            context = itemView.getContext();
            ivVideoThumbnail = itemView.findViewById(R.id.ivVideoThumbnail);
            tvVideoDuration = itemView.findViewById(R.id.tvVideoDuration);
            ivChannelAvatar = itemView.findViewById(R.id.ivChannelAvatar_video);
            tvVideoTitle = itemView.findViewById(R.id.tvVideoTitle);
            tvVideoChannelAndStats = itemView.findViewById(R.id.tvVideoChannelAndStats);
            ibVideoOptions = itemView.findViewById(R.id.ibVideoOptions);
        }

        public void bind(final MediaResponse videoItem,
                         final String currentUsername,
                         final OnVideoItemClickListener clickListener,
                         final OnVideoItemOptionsListener optionsListener) {

            // 1. Load Thumbnail
            RequestOptions thumbnailOptions = new RequestOptions()
                    .placeholder(R.color.placeholder_color)
                    .error(R.drawable.ic_default_media)
                    .centerCrop();
            Glide.with(context)
                    .load(videoItem.getThumbnailUrl())
                    .apply(thumbnailOptions)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(ivVideoThumbnail);

            // 2. Duration
            if (!TextUtils.isEmpty(videoItem.getDuration()) && !"00:00".equals(videoItem.getDuration())) {
                tvVideoDuration.setText(videoItem.getDuration());
                tvVideoDuration.setVisibility(View.VISIBLE);
            } else {
                tvVideoDuration.setVisibility(View.GONE);
            }

            // 3. Channel Avatar
            RequestOptions avatarOptions = new RequestOptions()
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .circleCrop();
            Glide.with(context)
                    .load(videoItem.getChannelAvatarUrl())
                    .apply(avatarOptions)
                    .into(ivChannelAvatar);

            // 4. Title
            tvVideoTitle.setText(videoItem.getTitle());

            // 5. Channel Name & Stats
            String channelName = !TextUtils.isEmpty(videoItem.getChannelName()) ?
                    videoItem.getChannelName() :
                    (!TextUtils.isEmpty(videoItem.getOwnerUsername()) ? videoItem.getOwnerUsername() : "Unknown Channel");
            String statsText = channelName;
            if (videoItem.getViewCount() >= 0) {
                statsText += " • " + formatViewCount(videoItem.getViewCount()) + " views";
            }
            if (!TextUtils.isEmpty(videoItem.getUploadDate())) {
                statsText += " • " + videoItem.getUploadDate();
            }
            tvVideoChannelAndStats.setText(statsText.replaceAll("^\\s*•\\s*", "")); // Loại bỏ dấu • ở đầu nếu channelName rỗng


            // Item Click Listener
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onVideoItemClick(videoItem);
                }
            });

            // Options Button Click Listener
            ibVideoOptions.setOnClickListener(v -> {
                if (optionsListener != null) {
                    showOptionsMenu(v, videoItem, currentUsername, optionsListener, getBindingAdapterPosition());
                }
            });
        }

        private void showOptionsMenu(View anchor, final MediaResponse videoItem, String currentUsername,
                                     final OnVideoItemOptionsListener optionsListener, final int position) {
            PopupMenu popup = new PopupMenu(context, anchor);
            popup.getMenuInflater().inflate(R.menu.media_item_options_menu, popup.getMenu());

            MenuItem deleteAction = popup.getMenu().findItem(R.id.action_media_delete);
            if (deleteAction != null) {
                boolean isOwner = currentUsername != null &&
                        videoItem.getOwnerUsername() != null &&
                        currentUsername.equals(videoItem.getOwnerUsername());
                deleteAction.setVisible(isOwner);
            }

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_media_share) {
                    optionsListener.onShareClick(videoItem);
                    return true;
                } else if (itemId == R.id.action_media_delete) {
                    optionsListener.onDeleteClick(videoItem, position);
                    return true;
                }
                return false;
            });
            popup.show();
        }

        private String formatViewCount(long count) {
            if (count < 1000) return String.valueOf(count);
            int exp = (int) (Math.log(count) / Math.log(1000));
            return String.format(Locale.US, "%.1f%c", count / Math.pow(1000, exp), "KMBTPE".charAt(exp - 1));
        }
    }

    // DiffUtil.ItemCallback cho ListAdapter
    private static final DiffUtil.ItemCallback<MediaResponse> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<MediaResponse>() {
                @Override
                public boolean areItemsTheSame(@NonNull MediaResponse oldItem, @NonNull MediaResponse newItem) {
                    // ID là duy nhất
                    return Objects.equals(oldItem.getId(), newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull MediaResponse oldItem, @NonNull MediaResponse newItem) {
                    // Kiểm tra các trường có thể thay đổi và ảnh hưởng đến UI
                    return TextUtils.equals(oldItem.getTitle(), newItem.getTitle()) &&
                            TextUtils.equals(oldItem.getThumbnailUrl(), newItem.getThumbnailUrl()) &&
                            TextUtils.equals(oldItem.getDuration(), newItem.getDuration()) &&
                            TextUtils.equals(oldItem.getChannelName(), newItem.getChannelName()) &&
                            TextUtils.equals(oldItem.getChannelAvatarUrl(), newItem.getChannelAvatarUrl()) &&
                            oldItem.getViewCount() == newItem.getViewCount() &&
                            TextUtils.equals(oldItem.getUploadDate(), newItem.getUploadDate()) &&
                            TextUtils.equals(oldItem.getOwnerUsername(), newItem.getOwnerUsername()) && // Cho nút options
                            oldItem.isPublic() == newItem.isPublic();
                }
            };
}