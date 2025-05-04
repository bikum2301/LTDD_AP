package com.example.streamapp.adapter; // Hoặc util

import androidx.recyclerview.widget.DiffUtil;
import com.example.streamapp.model.MediaResponse;
import java.util.Collections; // Import Collections
import java.util.List;
import java.util.Objects;

public class MediaDiffCallback extends DiffUtil.Callback {

    private final List<MediaResponse> oldList;
    private final List<MediaResponse> newList;

    public MediaDiffCallback(List<MediaResponse> oldList, List<MediaResponse> newList) {
        // Xử lý null bằng Collections.emptyList() để an toàn hơn
        this.oldList = (oldList != null) ? oldList : Collections.emptyList();
        this.newList = (newList != null) ? newList : Collections.emptyList();
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        // So sánh bằng ID (đảm bảo ID không null)
        Long oldId = oldList.get(oldItemPosition).getId();
        Long newId = newList.get(newItemPosition).getId();
        // Nếu một trong hai ID là null thì không thể là cùng item (trừ khi cả hai null và không có ID)
        if (oldId == null || newId == null) {
            return false; // Hoặc xử lý logic khác nếu ID có thể null
        }
        return oldId.equals(newId);
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        MediaResponse oldItem = oldList.get(oldItemPosition);
        MediaResponse newItem = newList.get(newItemPosition);

        // So sánh nội dung các trường hiển thị
        return Objects.equals(oldItem.getTitle(), newItem.getTitle()) &&
                // Objects.equals(oldItem.getDescription(), newItem.getDescription()) && // Bỏ qua nếu desc không hiển thị trực tiếp trên list item
                Objects.equals(oldItem.getType(), newItem.getType()) &&
                oldItem.isPublic() == newItem.isPublic() &&
                // Objects.equals(oldItem.getUrl(), newItem.getUrl()) && // Không cần thiết nếu URL không thay đổi hoặc không hiển thị
                Objects.equals(oldItem.getOwnerUsername(), newItem.getOwnerUsername()); // Quan trọng cho nút xóa
    }
}