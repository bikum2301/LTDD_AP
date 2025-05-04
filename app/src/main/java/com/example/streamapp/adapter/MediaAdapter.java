package com.example.streamapp.adapter;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil; // Import DiffUtil
import androidx.recyclerview.widget.RecyclerView;

import com.example.streamapp.R;
import com.example.streamapp.databinding.ListItemMediaBinding;
import com.example.streamapp.model.MediaResponse;

import java.util.ArrayList;
import java.util.Collections; // Import Collections
import java.util.List;
import java.util.Objects;

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
        if (!TextUtils.equals(this.currentUsername, username)) {
            this.currentUsername = username;
            // Không cần notify vì onBind sẽ tự kiểm tra lại khi scroll hoặc DiffUtil chạy
        }
    }

    // --- SỬA HÀM SETDATA ĐỂ DÙNG DIFFUTIL ---
    public void setData(List<MediaResponse> newMediaList) {
        final List<MediaResponse> oldListCopy = new ArrayList<>(this.mediaList); // Tạo bản sao list cũ
        final List<MediaResponse> newListValidated = (newMediaList != null) ? newMediaList : Collections.emptyList();

        // Tạo DiffCallback và tính toán khác biệt
        final MediaDiffCallback diffCallback = new MediaDiffCallback(oldListCopy, newListValidated);
        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        // Cập nhật danh sách nội bộ
        this.mediaList.clear();
        this.mediaList.addAll(newListValidated);

        // Áp dụng thay đổi vào RecyclerView
        diffResult.dispatchUpdatesTo(this);
        Log.d(TAG, "Data updated using DiffUtil. Old size: " + oldListCopy.size() + ", New size: " + this.mediaList.size());
    }
    // ------------------------------------------

    // Hàm removeItem giữ nguyên cách dùng notifyItemRemoved (đơn giản nhất khi xóa 1 item)
    public void removeItem(int position) {
        if (position >= 0 && position < mediaList.size()) {
            mediaList.remove(position);
            notifyItemRemoved(position);
            // Có thể gọi notifyItemRangeChanged nếu việc xóa ảnh hưởng đến vị trí các item khác mà UI cần biết
            // notifyItemRangeChanged(position, mediaList.size());
            Log.d(TAG, "Item removed at position: " + position);
        } else {
            Log.w(TAG, "Attempted to remove item at invalid position: " + position);
        }
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ListItemMediaBinding binding = ListItemMediaBinding.inflate(inflater, parent, false);
        return new MediaViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        MediaResponse currentItem = mediaList.get(position);
        Log.d(TAG, "onBindViewHolder pos: " + position + ", Title: " + currentItem.getTitle());
        holder.bind(currentItem, clickListener, deleteListener, currentUsername);
    }

    @Override
    public int getItemCount() {
        return mediaList.size();
    }

    // --- ViewHolder Class ---
    // (Không thay đổi so với phiên bản trước)
    public static class MediaViewHolder extends RecyclerView.ViewHolder {
        private final ListItemMediaBinding binding;
        private static final String TAG_VH = "MediaViewHolder";

        public MediaViewHolder(@NonNull ListItemMediaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(final MediaResponse mediaItem,
                         final OnItemClickListener clickListener,
                         final OnItemDeleteListener deleteListener,
                         final String currentUsername) {

            binding.tvMediaTitle.setText(mediaItem.getTitle());
            String type = mediaItem.getType() != null ? mediaItem.getType().toUpperCase() : "UNKNOWN";
            String visibility = mediaItem.isPublic() ? "Public" : "Private";
            binding.tvMediaDescription.setText(String.format("%s - %s", type, visibility));

            // Set icon
            int iconResId;
            if ("MUSIC".equals(type)) iconResId = android.R.drawable.ic_media_play;
            else if ("VIDEO".equals(type)) iconResId = android.R.drawable.presence_video_online;
            else iconResId = android.R.drawable.ic_menu_gallery;
            binding.ivMediaIcon.setImageResource(iconResId);

            // --- Xử lý hiển thị và click nút Xóa ---
            String ownerUsername = mediaItem.getOwnerUsername();
            boolean isOwner = currentUsername != null && currentUsername.equals(ownerUsername);

            if (isOwner && deleteListener != null) {
                binding.ibDeleteItem.setVisibility(View.VISIBLE);
                binding.ibDeleteItem.setOnClickListener(v -> {
                    int currentPosition = getBindingAdapterPosition();
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        Log.d(TAG_VH, "Delete button clicked for item: " + mediaItem.getTitle() + " at position: " + currentPosition);
                        deleteListener.onDeleteClick(mediaItem, currentPosition);
                    }
                });
            } else {
                binding.ibDeleteItem.setVisibility(View.GONE);
                binding.ibDeleteItem.setOnClickListener(null);
            }
            // -------------------------------------

            // --- Xử lý click vào toàn bộ item ---
            itemView.setOnClickListener(v -> {
                int currentPosition = getBindingAdapterPosition();
                if (clickListener != null && currentPosition != RecyclerView.NO_POSITION) {
                    Log.d(TAG_VH, "Item clicked! Title: " + mediaItem.getTitle());
                    clickListener.onItemClick(mediaItem);
                }
            });
            // -----------------------------------
        }
    }
}