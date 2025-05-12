package com.example.streamapp.model;

public class ErrorResponse {
    // Các trường phải khớp với ErrorResponse của backend
    private String timestamp;
    private int status;
    private String error;   // Tên lỗi chung
    private String message; // Message chi tiết
    private String path;

    // Getters
    public String getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
}