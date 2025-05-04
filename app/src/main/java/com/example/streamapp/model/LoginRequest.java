package com.example.streamapp.model;

public class LoginRequest {
    private String username;
    private String password;

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // Getters (Không cần setters nếu chỉ dùng để gửi đi)
    public String getUsername() { return username; }
    public String getPassword() { return password; }
}