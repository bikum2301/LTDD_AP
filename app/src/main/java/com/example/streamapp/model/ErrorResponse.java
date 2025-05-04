package com.example.streamapp.model;

// Class này dùng để parse body của response khi API trả về lỗi (status != 2xx)
public class ErrorResponse {
    private String timestamp; // Hoặc LocalDateTime nếu cấu hình Gson phù hợp
    private int status;
    private String error;
    private String message; // Thông điệp lỗi chính
    private String path;

    // Getters
    public String getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
}