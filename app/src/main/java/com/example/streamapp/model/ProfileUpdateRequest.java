package com.example.streamapp.model;

public class ProfileUpdateRequest {
    private String fullName;
    private String email;
    // Thêm các field khác nếu có trong backend (ví dụ: bio)

    public ProfileUpdateRequest(String fullName, String email) {
        this.fullName = fullName;
        this.email = email;
    }

    // Getters
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
}