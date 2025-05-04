package com.example.streamapp.model;

import com.google.gson.annotations.SerializedName;

public class MediaUploadRequest {
    private String title;
    private String description;
    private String type; // "MUSIC" or "VIDEO"

    @SerializedName("public") // Ánh xạ field isPublic với key "public" trong JSON
    private boolean isPublic;

    public MediaUploadRequest(String title, String description, String type, boolean isPublic) {
        this.title = title;
        this.description = description;
        this.type = type;
        this.isPublic = isPublic;
    }

    // Getters
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public boolean isPublic() { return isPublic; }
}