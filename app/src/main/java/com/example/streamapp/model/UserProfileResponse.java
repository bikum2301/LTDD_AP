package com.example.streamapp.model;

import com.google.gson.annotations.SerializedName;

public class UserProfileResponse {
    private Long id;
    private String username;
    private String email;
    @SerializedName("full_name") // Khớp với tên cột trong DB/API
    private String fullName;
    @SerializedName("profile_picture_url")
    private String profilePictureUrl;
    private String bio;
    // Thêm các trường khác nếu API trả về

    // --- Getters ---
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public String getBio() { return bio; }

    // --- THÊM SETTERS ---
    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setFullName(String fullName) { this.fullName = fullName; } // Setter cần thiết
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; } // Setter cần thiết
    public void setBio(String bio) { this.bio = bio; }
    // --------------------

    // (Tùy chọn) Thêm Constructor nếu cần
    public UserProfileResponse() {}
}