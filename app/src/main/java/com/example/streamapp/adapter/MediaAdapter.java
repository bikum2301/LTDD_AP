// File: src/main/java/com/example/streamapp/adapter/MediaAdapter.java
package com.example.streamapp.adapter;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem; // Đảm bảo import MenuItem
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu; // Đảm bảo import PopupMenu
import android.widget.Toast;  // Thêm Toast để thông báo khi share

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.example.streamapp.R;
import com.example.streamapp.databinding.ListItemMediaBinding; // Binding này trỏ đến list_item_media.xml
import com.example.streamapp.model.MediaResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
// java.util.Objects; // Không cần thiết nếu không dùng trực tiếp trong file này

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.MediaViewHolder> {

    private List<MediaResponse> mediaList = new ArrayList<>();
    private OnItemClickListener clickListener;
    private OnItemDeleteListener deleteListener;
    private String currentUsername;
    private static final String TAG = "MediaAdapter";

    // Interfaces
    public interface OnItemClickListener { void onItemClick(MediaResponse mediaItem); }
    public interface OnItemDeleteListener { void onDeleteClick(MediaResponse mediaItem, int position); }

    // Constructor
    public MediaAdapter() {}

    // Setters
    public void setOnItemClickListener(OnItemClickListener listener) { this.clickListener = listener; }
    public void setOnItemDeleteListener(OnItemDeleteListener listener) { this.deleteListener = listener; }
    public void setCurrentUsername(String username) {
        // So sánh để tránh re-assignment không cần thiết, dù DiffUtil và onBind sẽ xử lý UI
        if (!TextUtils.equals(this.currentUsername, username)) {
            this.currentUsername = username;
            // Không cần gọi notifyDataSetChanged() ở đây nếu dùng DiffUtil,
            // onBindViewHolder sẽ tự kiểm tra lại quyền sở hữu khi item được bind hoặc rebind.
        }
    }

    public void setData(List<MediaResponse> newMediaList) {
        final List<MediaResponse> oldListCopy = new ArrayList<>(this.mediaList);
        final List<MediaResponse> newListValidated = (newMediaList != null) ? newMediaList : Collections.emptyList();

        final MediaDiffCallback diffCallback = new MediaDiffCallback(oldListCopy, newListValidated);
        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.mediaList.clear();
        this.mediaList.addAll(newListValidated);

        diffResult.dispatchUpdatesTo(this); // Áp dụng thay đổi hiệu quả
        Log.d(TAG, "Data updated using DiffUtil. Old size: " + oldListCopy.size() + ", New size: " + this.mediaList.size());
    }

    public void removeItem(int position) {
        if (position >= 0 && position < mediaList.size()) {
            mediaList.remove(position);
            notifyItemRemoved(position);
            // Nếu việc xóa ảnh hưởng đến các item khác, có thể cần notifyItemRangeChanged
            // notifyItemRangeChanged(position, getItemCount());
            Log.d(TAG, "Item removed at position: " + position);
        } else {
            Log.w(TAG, "Attempted to remove item at invalid position: " + position);
        }
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        // Đảm bảo ListItemMediaBinding được tạo từ file layout list_item_media.xml
        ListItemMediaBinding binding = ListItemMediaBinding.inflate(inflater, parent, false);
        return new MediaViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        MediaResponse currentItem = mediaList.get(position);
        if (currentItem == null) {
            Log.e(TAG, "onBindViewHolder called with null item at position: " + position);
            // Có thể ẩn view hoặc hiển thị placeholder lỗi tại đây nếu cần
            holder.itemView.setVisibility(View.GONE); // Ví dụ ẩn item nếu data null
            return;
        }
        holder.itemView.setVisibility(View.VISIBLE); // Đảm bảo item hiển thị nếu data không null
        Log.d(TAG, "onBindViewHolder pos: " + position + ", Title: " + currentItem.getTitle() + ", ID: " + currentItem.getId());
        holder.bind(currentItem, clickListener, deleteListener, currentUsername);
    }

    @Override
    public int getItemCount() {
        return mediaList.size();
    }

    // --- ViewHolder Class ---
    public static class MediaViewHolder extends RecyclerView.ViewHolder {
        private final ListItemMediaBinding binding; // Binding này phải khớp với list_item_media.xml
        private final Context context; // Context để dùng cho Glide, PopupMenu, Intent
        private static final String TAG_VH = "MediaViewHolder";

        public MediaViewHolder(@NonNull ListItemMediaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.context = binding.getRoot().getContext(); // Lấy context từ itemView
        }

        public void bind(final MediaResponse mediaItem,
                         final OnItemClickListener clickListener,
                         final OnItemDeleteListener deleteListener,
                         final String currentUsername) {

            if (mediaItem == null) {
                Log.e(TAG_VH, "bind() called with null mediaItem for ViewHolder.");
                itemView.setVisibility(View.GONE); // Ẩn view nếu item null
                return;
            }
            itemView.setVisibility(View.VISIBLE); // Đảm bảo view hiển thị

            // 1. Load Thumbnail (Sử dụng ID: ivMediaThumbnail từ list_item_media.xml)
            RequestOptions thumbnailOptions = new RequestOptions()
                    .placeholder(R.color.placeholder_color) // Màu placeholder từ layout
                    .error(R.drawable.ic_default_media)    // Ảnh lỗi mặc định
                    .centerCrop();

            Glide.with(context)
                    .load(mediaItem.getThumbnailUrl()) // MediaResponse cần có getThumbnailUrl()
                    .apply(thumbnailOptions)
                    .transition(DrawableTransitionOptions.withCrossFade()) // Hiệu ứng mờ dần
                    .into(binding.ivMediaThumbnail);

            // 2. Media Type Icon và Duration (Sử dụng ID: ivMediaTypeIcon, tvMediaDuration)
            String mediaType = mediaItem.getType() != null ? mediaItem.getType().toUpperCase() : "UNKNOWN";
            if ("VIDEO".equals(mediaType)) {
                binding.ivMediaTypeIcon.setImageResource(android.R.drawable.presence_video_online);
                if (!TextUtils.isEmpty(mediaItem.getDuration())) {
                    binding.tvMediaDuration.setText(mediaItem.getDuration());
                    binding.tvMediaDuration.setVisibility(View.VISIBLE);
                } else {
                    binding.tvMediaDuration.setVisibility(View.GONE);
                }
            } else if ("MUSIC".equals(mediaType)) {
                binding.ivMediaTypeIcon.setImageResource(android.R.drawable.ic_media_play);
                binding.tvMediaDuration.setVisibility(View.GONE); // Nhạc thường không hiển thị duration ở đây
            } else {
                binding.ivMediaTypeIcon.setImageResource(R.drawable.ic_default_media); // Icon mặc định khác
                binding.tvMediaDuration.setVisibility(View.GONE);
            }
            binding.ivMediaTypeIcon.setVisibility(View.VISIBLE); // Đảm bảo icon type luôn hiện

            // 3. Load Channel/User Avatar (Sử dụng ID: ivChannelAvatar)
            RequestOptions avatarOptions = new RequestOptions()
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .circleCrop();

            Glide.with(context)
                    .load(mediaItem.getChannelAvatarUrl()) // MediaResponse cần có getChannelAvatarUrl()
                    .apply(avatarOptions)
                    .into(binding.ivChannelAvatar);

            // 4. Title (Sử dụng ID: tvMediaTitle)
            binding.tvMediaTitle.setText(mediaItem.getTitle());

            // 5. Owner Info (Channel Name / Artist - Sử dụng ID: tvMediaOwnerInfo)
            if ("VIDEO".equals(mediaType)) {
                binding.tvMediaOwnerInfo.setText(!TextUtils.isEmpty(mediaItem.getChannelName()) ? mediaItem.getChannelName() : mediaItem.getOwnerUsername());
            } else if ("MUSIC".equals(mediaType)) {
                binding.tvMediaOwnerInfo.setText(!TextUtils.isEmpty(mediaItem.getArtist()) ? mediaItem.getArtist() : mediaItem.getOwnerUsername());
            } else {
                binding.tvMediaOwnerInfo.setText(mediaItem.getOwnerUsername());
            }

            // 6. Stats (View Count & Upload Date for VIDEO - Sử dụng ID: tvMediaStats)
            if ("VIDEO".equals(mediaType)) {
                String statsText = "";
                if (mediaItem.getViewCount() >= 0) { // Hiển thị cả khi view count là 0
                    statsText += formatViewCount(mediaItem.getViewCount()) + " views";
                }
                if (!TextUtils.isEmpty(mediaItem.getUploadDate())) {
                    if (!statsText.isEmpty()) statsText += " • ";
                    statsText += mediaItem.getUploadDate(); // Giả sử đã là string dạng "2 weeks ago"
                }
                if (!statsText.isEmpty()) {
                    binding.tvMediaStats.setText(statsText);
                    binding.tvMediaStats.setVisibility(View.VISIBLE);
                } else {
                    binding.tvMediaStats.setVisibility(View.GONE);
                }
                binding.tvMediaAlbum.setVisibility(View.GONE); // Ẩn album cho video
            }
            // 7. Album (for MUSIC - Sử dụng ID: tvMediaAlbum)
            else if ("MUSIC".equals(mediaType)) {
                if (!TextUtils.isEmpty(mediaItem.getAlbum())) {
                    binding.tvMediaAlbum.setText(mediaItem.getAlbum());
                    binding.tvMediaAlbum.setVisibility(View.VISIBLE);
                } else {
                    binding.tvMediaAlbum.setVisibility(View.GONE);
                }
                binding.tvMediaStats.setVisibility(View.GONE); // Ẩn stats cho music
            } else {
                // Loại khác thì ẩn cả hai
                binding.tvMediaStats.setVisibility(View.GONE);
                binding.tvMediaAlbum.setVisibility(View.GONE);
            }

            // 8. Click vào toàn bộ item
            itemView.setOnClickListener(v -> {
                int currentPosition = getBindingAdapterPosition(); // Vẫn lấy currentPosition để check NO_POSITION
                if (clickListener != null && currentPosition != RecyclerView.NO_POSITION) {
                    Log.d(TAG_VH, "Item clicked! Title: " + mediaItem.getTitle() + " ID: " + mediaItem.getId());
                    // SỬA Ở ĐÂY: Dùng trực tiếp mediaItem đã được truyền vào
                    clickListener.onItemClick(mediaItem);
                }
            });

            // 9. Click vào nút Options (Sử dụng ID: ibMediaOptions) -> Hiển thị PopupMenu
            boolean isOwner = currentUsername != null && mediaItem.getOwnerUsername() != null && currentUsername.equals(mediaItem.getOwnerUsername());
            binding.ibMediaOptions.setVisibility(View.VISIBLE);
            binding.ibMediaOptions.setOnClickListener(v -> {
                int currentPosition = getBindingAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION) {
                    // SỬA Ở ĐÂY: Dùng trực tiếp mediaItem đã được truyền vào
                    showOptionsMenu(v, mediaItem, currentPosition, isOwner, deleteListener);
                }
            });
        }

        // --- Helper để hiển thị PopupMenu ---
        private void showOptionsMenu(View anchorView, final MediaResponse mediaItem, final int position, boolean isOwner, final OnItemDeleteListener deleteListener) {
            PopupMenu popup = new PopupMenu(context, anchorView);
            popup.getMenuInflater().inflate(R.menu.media_item_options_menu, popup.getMenu());

            // Ẩn/hiện nút Delete tùy theo quyền sở hữu
            MenuItem deleteMenuItem = popup.getMenu().findItem(R.id.action_media_delete);
            if (deleteMenuItem != null) {
                deleteMenuItem.setVisible(isOwner && deleteListener != null);
            }

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_media_delete) {
                    if (isOwner && deleteListener != null) {
                        Log.d(TAG_VH, "Delete option selected for: " + mediaItem.getTitle() + " at position: " + position);
                        deleteListener.onDeleteClick(mediaItem, position); // Dùng mediaItem và position ở đây
                    }
                    return true;
                } else if (itemId == R.id.action_media_share) {
                    Log.d(TAG_VH, "Share option selected for: " + mediaItem.getTitle());
                    shareMedia(mediaItem); // Dùng mediaItem ở đây
                    return true;
                }
                return false;
            });
            popup.show();
        }

        // --- Helper để share media ---
        private void shareMedia(MediaResponse mediaItem) {
            if (mediaItem.getUrl() == null || mediaItem.getUrl().isEmpty()) {
                Toast.makeText(context, "Cannot share: Media URL is missing.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            String mediaTypeText = mediaItem.getType() != null ? mediaItem.getType().toLowerCase() : "media";
            String shareBody = "Check out this " + mediaTypeText + ": "
                    + mediaItem.getTitle() + "\n" + mediaItem.getUrl();
            // Ví dụ thêm link app:
            // String appSpecificLink = "yourapp://media/" + mediaItem.getId();
            // shareBody += "\n\nOpen in StreamApp: " + appSpecificLink;

            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Sharing: " + mediaItem.getTitle());
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
            try {
                context.startActivity(Intent.createChooser(shareIntent, "Share " + mediaTypeText + " via"));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(context, "No app found to handle sharing.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG_VH, "Error starting share intent", e);
                Toast.makeText(context, "Could not open share options.", Toast.LENGTH_SHORT).show();
            }
        }

        // --- Helper để format ViewCount (ví dụ) ---
        private String formatViewCount(long count) {
            if (count < 1000) return String.valueOf(count);
            int exp = (int) (Math.log(count) / Math.log(1000));
            // Sử dụng Locale.US để đảm bảo dấu chấm thập phân
            return String.format(Locale.US, "%.1f%c", count / Math.pow(1000, exp), "KMBTPE".charAt(exp - 1));
        }
    }
}