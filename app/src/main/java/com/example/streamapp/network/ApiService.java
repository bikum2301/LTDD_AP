package com.example.streamapp.network;

import com.example.streamapp.model.*; // Import các model đã tạo

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
    Call<MessageResponse> uploadMedia(
            @Header("Authorization") String authToken, // Token tự thêm bởi Interceptor, nhưng để đây cho rõ
            @Part MultipartBody.Part file,
            @Part("data") RequestBody mediaData
    );

    @GET("api/media")
    Call<List<MediaResponse>> getUserMedia(@Header("Authorization") String authToken); // Cần token

    @GET("api/media/public")
    Call<List<MediaResponse>> getPublicMedia(); // Có thể cần hoặc không cần token

    @GET("api/media/{id}")
    Call<MediaResponse> getMediaDetails(
            @Header("Authorization") String authToken, // Cần token
            @Path("id") Long mediaId
    );

    @DELETE("api/media/{id}")
    Call<MessageResponse> deleteMedia(
            @Header("Authorization") String authToken, // Cần token
            @Path("id") Long mediaId
    );

    // --- User Profile ---

    // **** THÊM API GET PROFILE ****
    // Giả sử API trả về đối tượng UserProfileResponse (cần tạo model này)
    @GET("api/user/profile")
    Call<UserProfileResponse> getUserProfile(@Header("Authorization") String authToken); // Cần token
    // ******************************

    @PUT("api/user/profile")
    Call<MessageResponse> updateProfile(
            @Header("Authorization") String authToken, // Cần token
            @Body ProfileUpdateRequest profileUpdateRequest
    );

    @Multipart
    @POST("api/user/upload-profile-picture")
    Call<MessageResponse> uploadProfilePicture(
            @Header("Authorization") String authToken, // Cần token
            @Part MultipartBody.Part file
    );
}