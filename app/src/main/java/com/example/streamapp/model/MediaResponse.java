package com.example.streamapp.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class MediaResponse implements Serializable {
    private Long id;
    private String title;
    private String description;
    private String type;
    private String url;
    private String ownerUsername; // <<<--- ĐẢM BẢO CÓ TRƯỜNG NÀY

    @SerializedName("public")
    private boolean isPublic;

    // Getters and Setters
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
    public String getOwnerUsername() { return ownerUsername; } // <<<--- ĐẢM BẢO CÓ GETTER
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; } // <<<--- ĐẢM BẢO CÓ SETTER
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean aPublic) { isPublic = aPublic; }
}