package com.example.streamapp.model;

public class MessageResponse {
    private String message;
    private String token; // Dùng cho login response
    private String url;   // Dùng cho upload response

    // Getters (Cần setters hoặc constructor để Gson tạo object)
    public String getMessage() { return message; }
    public String getToken() { return token; }
    public String getUrl() { return url; }

    // Setters (Hoặc dùng @SerializedName nếu tên biến khác JSON key)
    public void setMessage(String message) { this.message = message; }
    public void setToken(String token) { this.token = token; }
    public void setUrl(String url) { this.url = url; }
}