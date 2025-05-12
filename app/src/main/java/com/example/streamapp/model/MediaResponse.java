package com.example.streamapp.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class MediaResponse implements Serializable {
    private Long id;
    private String title;
    private String description;
    private String type; // "VIDEO" hoặc "MUSIC" (Quan trọng để phân biệt)
    private String url; // URL để phát
    private String ownerUsername;

    @SerializedName("public")
    private boolean isPublic;

    // --- Thêm các trường mới ---
    private String thumbnailUrl; // Dùng cho video thumbnail hoặc music artwork
    private String duration;     // Định dạng "HH:MM:SS" hoặc "MM:SS"
    private String artist;       // Tên nghệ sĩ (cho music)
    private String album;        // Tên album (cho music)
    private String channelName;  // Tên kênh (cho video, có thể dùng ownerUsername nếu không có kênh riêng)
    private String channelAvatarUrl; // URL avatar của kênh/người đăng
    private long viewCount;      // Số lượt xem
    private String uploadDate;   // Ngày đăng (có thể là String "3 days ago", "2024-07-20")
    // hoặc long timestamp để định dạng sau

    // --- Constructors (nếu cần, nhưng Gson có thể không cần) ---

    // --- Getters and Setters cho TẤT CẢ các trường ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getOwnerUsername() { return ownerUsername; }
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean aPublic) { isPublic = aPublic; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }

    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }

    public String getChannelAvatarUrl() { return channelAvatarUrl; }
    public void setChannelAvatarUrl(String channelAvatarUrl) { this.channelAvatarUrl = channelAvatarUrl; }

    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }

    public String getUploadDate() { return uploadDate; }
    public void setUploadDate(String uploadDate) { this.uploadDate = uploadDate; }
}