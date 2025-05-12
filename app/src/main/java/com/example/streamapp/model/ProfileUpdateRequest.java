// File: src/main/java/com/example/streamapp/model/ProfileUpdateRequest.java (ANDROID)
package com.example.streamapp.model;

public class ProfileUpdateRequest {
    private String fullName;
    private String email; // Vẫn giữ nếu DTO backend yêu cầu, dù không dùng để cập nhật email
    private String bio;   // << THÊM TRƯỜNG NÀY

    // Constructor rỗng để dễ tạo và set giá trị
    public ProfileUpdateRequest() {
    }

    // Constructor cũ có thể giữ lại nếu bạn vẫn dùng ở đâu đó
    public ProfileUpdateRequest(String fullName, String email) {
        this.fullName = fullName;
        this.email = email;
    }

    // Getters
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getBio() { return bio; } // << THÊM GETTER

    // Setters
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setEmail(String email) { this.email = email; }
    public void setBio(String bio) { this.bio = bio; } // << THÊM SETTER
}