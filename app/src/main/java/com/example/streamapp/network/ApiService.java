// File: src/main/java/com/example/streamapp/network/ApiService.java
package com.example.streamapp.network;

import com.example.streamapp.model.*; // Import các model của client

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    // --- Authentication ---
    @POST("api/auth/register")
    Call<MessageResponse> registerUser(@Body SignupRequest signupRequest);

    @POST("api/auth/verify")
    Call<MessageResponse> verifyUser(@Query("code") String otpCode);

    @POST("api/auth/login")
    Call<MessageResponse> loginUser(@Body LoginRequest loginRequest);

    // --- Media ---
    @Multipart
    @POST("api/media/upload")
    Call<MediaResponse> uploadMedia( // << SỬA TỪ Call<MessageResponse> THÀNH Call<MediaResponse>
                                     @Part MultipartBody.Part file,
                                     @Part("data") RequestBody mediaData
    );

    @GET("api/media")
    Call<List<MediaResponse>> getUserMedia(); // BỎ authToken

    @GET("api/media/public")
    Call<List<MediaResponse>> getPublicMedia(@Query("type") String mediaType); // Thêm tham số type

    @GET("api/media/public") // Overload nếu không muốn truyền type
    Call<List<MediaResponse>> getPublicMedia();

    @GET("api/media/{id}")
    Call<MediaResponse> getMediaDetails(
            // BỎ authToken
            @Path("id") Long mediaId
    );

    @DELETE("api/media/{id}")
    Call<MessageResponse> deleteMedia(
            // BỎ authToken
            @Path("id") Long mediaId
    );

    // --- User Profile ---
    @GET("api/user/profile") // Đã thêm ở backend controller
    Call<UserProfileResponse> getUserProfile(); // BỎ authToken

    @PUT("api/user/profile")
    Call<MessageResponse> updateProfile(
            // BỎ authToken
            @Body ProfileUpdateRequest profileUpdateRequest
    );

    @Multipart
    @POST("api/user/upload-profile-picture")
    Call<MessageResponse> uploadProfilePicture(
            // BỎ authToken
            @Part MultipartBody.Part file
    );
}