package com.example.streamapp.model;

public class SignupRequest {
    private String username;
    private String email;
    private String password;
    private String fullName;

    public SignupRequest(String username, String email, String password, String fullName) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }

    // Getters
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getFullName() { return fullName; }
}