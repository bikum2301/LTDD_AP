// File: src/main/java/com/example/streamapp/activity/ProfileActivity.java
package com.example.streamapp.activity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.example.streamapp.R;
import com.example.streamapp.databinding.ActivityProfileBinding;
import com.example.streamapp.model.MessageResponse;
import com.example.streamapp.model.ProfileUpdateRequest;
import com.example.streamapp.model.UserProfileResponse; // Model của client
// Import ErrorResponse của client nếu bạn đã tạo
import com.example.streamapp.model.ErrorResponse; // << THÊM IMPORT NÀY

import com.example.streamapp.network.ApiClient;
import com.example.streamapp.network.ApiService;
import com.example.streamapp.utils.SessionManager;
import com.google.gson.Gson; // Gson để parse lỗi nếu cần

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private Uri selectedImageUri;
    private static final String TAG = "ProfileActivity";

    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> requestImagePermissionLauncher;

    private static final String READ_IMAGES_PERMISSION = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ? Manifest.permission.READ_MEDIA_IMAGES
            : Manifest.permission.READ_EXTERNAL_STORAGE;

    // Các biến này sẽ được cập nhật từ API response
    private String currentUsername = "";
    private String currentEmail = "";
    private String currentFullName = "";
    private String currentBio = "";
    private String currentProfilePicUrl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(getApplicationContext());
        sessionManager = new SessionManager(getApplicationContext());

        setSupportActionBar(binding.toolbarProfile);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Profile"); // Đảm bảo tiêu đề được set
        }

        setupLaunchers();

        binding.btnChangeProfilePic.setOnClickListener(v -> checkPermissionAndOpenImagePicker());
        binding.btnSaveProfile.setOnClickListener(v -> attemptSaveProfile());

        // Gọi API để lấy thông tin profile khi màn hình được tạo
        fetchUserProfile();
    }

    private void setupLaunchers() {
        requestImagePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openImagePicker();
                    } else {
                        Toast.makeText(this, "Permission denied. Cannot select image.", Toast.LENGTH_SHORT).show();
                    }
                });

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        // Hiển thị ảnh đã chọn tạm thời lên ImageView
                        Glide.with(this)
                                .load(uri)
                                .apply(RequestOptions.circleCropTransform()
                                        .placeholder(R.drawable.ic_default_avatar)
                                        .error(R.drawable.ic_default_avatar))
                                .into(binding.ivProfilePic);
                        // Sau đó mới gọi upload
                        attemptUploadProfilePicture();
                    } else {
                        Log.d(TAG, "Image selection cancelled.");
                    }
                });
    }

    private void checkPermissionAndOpenImagePicker() {
        if (ContextCompat.checkSelfPermission(this, READ_IMAGES_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        } else {
            requestImagePermissionLauncher.launch(READ_IMAGES_PERMISSION);
        }
    }

    private void openImagePicker() {
        imagePickerLauncher.launch("image/*");
    }

    private void fetchUserProfile() {
        String token = sessionManager.getToken();
        if (token == null) { // Không cần kiểm tra dummy token nữa nếu AuthInterceptor hoạt động đúng
            Log.w(TAG, "No token found. Navigating to login.");
            navigateToLoginAndFinish();
            return;
        }

        showLoading(true);
        // AuthInterceptor sẽ tự động thêm "Bearer " + token vào header
        apiService.getUserProfile().enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserProfileResponse> call, @NonNull Response<UserProfileResponse> response) {
                showLoading(false);
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null) {
                    UserProfileResponse userProfile = response.body();
                    currentUsername = userProfile.getUsername();
                    currentEmail = userProfile.getEmail();
                    currentFullName = userProfile.getFullName();
                    currentBio = userProfile.getBio();
                    currentProfilePicUrl = userProfile.getProfilePictureUrl();

                    populateUiWithProfileData();
                    Log.i(TAG, "User profile fetched successfully for: " + currentUsername);
                } else {
                    Log.e(TAG, "Failed to fetch profile data. Code: " + response.code());
                    handleApiError(response, "Failed to load profile", true);
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserProfileResponse> call, @NonNull Throwable t) {
                showLoading(false);
                if (isFinishing() || isDestroyed()) return;
                Log.e(TAG, "API Call Failed (getUserProfile): ", t);
                Toast.makeText(ProfileActivity.this, "Error fetching profile: " + t.getMessage(), Toast.LENGTH_LONG).show();
                // Có thể hiển thị nút retry hoặc cho phép người dùng thử lại
            }
        });
    }

    private void populateUiWithProfileData() {
        runOnUiThread(() -> {
            binding.tvProfileUsername.setText(TextUtils.isEmpty(currentUsername) ? "N/A" : currentUsername);
            binding.tvProfileEmail.setText(TextUtils.isEmpty(currentEmail) ? "N/A" : currentEmail);
            binding.etProfileFullName.setText(currentFullName != null ? currentFullName : "");
            binding.etProfileBio.setText(currentBio != null ? currentBio : "");
            loadProfilePicture(currentProfilePicUrl);
        });
    }

    private void loadProfilePicture(String imageUrl) {
        runOnUiThread(() -> {
            Log.d(TAG, "Loading profile picture from URL: " + imageUrl);
            RequestOptions requestOptions = RequestOptions.circleCropTransform()
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // Quan trọng nếu URL có thể thay đổi (ví dụ SAS token)
                    .skipMemoryCache(true);                  // hoặc nếu bạn muốn luôn lấy ảnh mới nhất

            Glide.with(ProfileActivity.this) // Sử dụng Context của Activity
                    .load(imageUrl) // Glide tự xử lý nếu imageUrl null hoặc rỗng
                    .apply(requestOptions)
                    .into(binding.ivProfilePic);
        });
    }

    private void attemptSaveProfile() {
        String newFullName = binding.etProfileFullName.getText().toString().trim();
        String newBio = binding.etProfileBio.getText().toString().trim();

        // Email được lấy từ currentEmail (đã fetch từ API và không cho sửa trên UI)
        // Backend sẽ dùng username từ token để xác định user, email trong request body
        // có thể dùng để kiểm tra hoặc nếu backend cho phép cập nhật email (hiện tại không)
        if (TextUtils.isEmpty(currentEmail) || "N/A".equals(currentEmail)) {
            Toast.makeText(this, "Email information is missing. Please refresh profile.", Toast.LENGTH_LONG).show();
            fetchUserProfile(); // Thử fetch lại
            return;
        }

        boolean hasChanges = !TextUtils.equals(newFullName, currentFullName == null ? "" : currentFullName) ||
                !TextUtils.equals(newBio, currentBio == null ? "" : currentBio);

        if (!hasChanges) {
            Toast.makeText(this, "No changes to save.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        // Backend DTO ProfileUpdateRequest(String fullName, String email)
        // Nếu backend không cần email trong body khi update, bạn có thể tạo ProfileUpdateRequest chỉ với fullName (và bio nếu có)
        // Giả sử backend vẫn nhận email (nhưng không dùng để update mà chỉ là một phần của DTO)
        ProfileUpdateRequest updateRequest = new ProfileUpdateRequest(newFullName, currentEmail);
        // Nếu bạn muốn cập nhật cả bio, và backend hỗ trợ:
        // updateRequest.setBio(newBio); // Cần thêm setter/constructor cho ProfileUpdateRequest

        // AuthInterceptor sẽ tự thêm token
        apiService.updateProfile(updateRequest).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(@NonNull Call<MessageResponse> call, @NonNull Response<MessageResponse> response) {
                showLoading(false);
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(ProfileActivity.this, response.body().getMessage(), Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "Profile updated successfully.");
                    // Cập nhật lại các biến local với giá trị mới
                    currentFullName = newFullName;
                    currentBio = newBio;
                    // Không cần populateUiWithProfileData() lại nếu chỉ thay đổi text,
                    // nhưng nếu backend trả về UserProfileResponse mới thì nên dùng nó.
                    // Hoặc gọi fetchUserProfile() để đảm bảo dữ liệu đồng bộ hoàn toàn.
                    // fetchUserProfile(); // Tùy chọn: fetch lại để chắc chắn
                } else {
                    handleApiError(response, "Failed to update profile", false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<MessageResponse> call, @NonNull Throwable t) {
                showLoading(false);
                if (isFinishing() || isDestroyed()) return;
                Log.e(TAG, "API Call Failed (updateProfile): ", t);
                Toast.makeText(ProfileActivity.this, "Error updating profile: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void attemptUploadProfilePicture() {
        if (selectedImageUri == null) {
            Log.w(TAG, "selectedImageUri is null, cannot upload.");
            return;
        }
        showLoading(true);

        new Thread(() -> {
            try {
                RequestBody requestFile = createImageRequestBody(selectedImageUri);
                if (requestFile == null) {
                    throw new IOException("Could not create RequestBody for image.");
                }
                String originalFileName = getFileNameFromUri(selectedImageUri);
                // Server mong đợi phần "file"
                MultipartBody.Part body = MultipartBody.Part.createFormData("file", originalFileName, requestFile);

                // AuthInterceptor sẽ tự thêm token
                apiService.uploadProfilePicture(body).enqueue(new Callback<MessageResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<MessageResponse> call, @NonNull Response<MessageResponse> response) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            if (response.isSuccessful() && response.body() != null && response.body().getUrl() != null) {
                                Toast.makeText(ProfileActivity.this, response.body().getMessage(), Toast.LENGTH_SHORT).show();
                                String newImageUrl = response.body().getUrl();
                                Log.i(TAG, "Profile picture uploaded. New URL: " + newImageUrl);
                                currentProfilePicUrl = newImageUrl; // Cập nhật URL ảnh đại diện hiện tại
                                loadProfilePicture(currentProfilePicUrl); // Hiển thị ảnh mới
                                selectedImageUri = null; // Reset sau khi upload thành công
                            } else {
                                Log.e(TAG, "Upload profile picture API call not successful or body/URL is null.");
                                handleApiError(response, "Failed to upload profile picture", false);
                                loadProfilePicture(currentProfilePicUrl); // Load lại ảnh cũ (từ biến currentProfilePicUrl) khi lỗi
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull Call<MessageResponse> call, @NonNull Throwable t) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            Log.e(TAG, "API Call Failed (uploadProfilePicture): ", t);
                            Toast.makeText(ProfileActivity.this, "Error uploading picture: " + t.getMessage(), Toast.LENGTH_LONG).show();
                            loadProfilePicture(currentProfilePicUrl); // Load lại ảnh cũ
                        });
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "IOException preparing image for upload: ", e);
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(ProfileActivity.this, "Error preparing image: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    loadProfilePicture(currentProfilePicUrl); // Load lại ảnh cũ
                });
            }
        }).start();
    }

    private RequestBody createImageRequestBody(Uri uri) throws IOException {
        InputStream inputStream = null;
        ByteArrayOutputStream byteBuffer = null;
        try {
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                throw new IOException("Unable to open InputStream for URI: " + uri);
            }

            String mimeType = getContentResolver().getType(uri);
            if (mimeType == null) {
                String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
            }
            if (mimeType == null || !mimeType.startsWith("image/")) {
                Log.w(TAG, "Could not determine MIME type for image, defaulting to image/jpeg. URI: " + uri);
                mimeType = "image/jpeg"; // Mặc định an toàn
            }
            Log.d(TAG, "Image MIME Type: " + mimeType);

            byteBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            byte[] fileBytes = byteBuffer.toByteArray();
            return RequestBody.create(fileBytes, MediaType.parse(mimeType));
        } finally {
            if (inputStream != null) {
                try { inputStream.close(); } catch (IOException e) { Log.e(TAG, "Error closing InputStream", e); }
            }
            if (byteBuffer != null) {
                try { byteBuffer.close(); } catch (IOException e) { Log.e(TAG, "Error closing ByteArrayOutputStream", e); }
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting filename from content URI", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return (result != null && !result.isEmpty()) ? result : "unknown_image_file";
    }

    private void handleApiError(Response<?> response, String defaultMessage, boolean logoutOnError) {
        if (isFinishing() || isDestroyed()) return;

        String errorMessage = defaultMessage;
        int responseCode = -1;
        String errorBodyContent = null;

        if (response != null) {
            responseCode = response.code();
            if (response.errorBody() != null) {
                try {
                    errorBodyContent = response.errorBody().string();
                } catch (IOException e) {
                    Log.e(TAG, "Error reading error body: ", e);
                }
            }
        }
        Log.e(TAG, defaultMessage + " - Code: " + responseCode + ", ErrorBody: " + errorBodyContent);


        if ((response == null || responseCode == 401 || responseCode == 403) && logoutOnError) {
            navigateToLoginAndFinish();
            return;
        }

        if (errorBodyContent != null && !errorBodyContent.isEmpty()) {
            try {
                // Thử parse theo cấu trúc ErrorResponse của backend trước
                ErrorResponse backendError = new Gson().fromJson(errorBodyContent, ErrorResponse.class);
                if (backendError != null && !TextUtils.isEmpty(backendError.getMessage())) {
                    errorMessage = backendError.getMessage();
                } else if (backendError != null && !TextUtils.isEmpty(backendError.getError())) {
                    errorMessage = backendError.getError() + " (Code: " + responseCode + ")";
                } else {
                    // Nếu không có message hoặc error cụ thể từ ErrorResponse
                    errorMessage = defaultMessage + " (Code: " + responseCode + ")";
                }
            } catch (Exception e) {
                // Nếu không parse được thành ErrorResponse, thử parse thành MessageResponse
                Log.w(TAG, "Could not parse error body as Backend ErrorResponse, trying MessageResponse. Error: " + e.getMessage());
                try {
                    MessageResponse msgResponse = new Gson().fromJson(errorBodyContent, MessageResponse.class);
                    if (msgResponse != null && !TextUtils.isEmpty(msgResponse.getMessage())) {
                        errorMessage = msgResponse.getMessage();
                    } else {
                        errorMessage = defaultMessage + " (Code: " + responseCode + ")";
                    }
                } catch (Exception e2) {
                    // Nếu vẫn không parse được, dùng default message và code
                    Log.w(TAG, "Could not parse error body as MessageResponse either. Error: " + e2.getMessage());
                    errorMessage = defaultMessage + " (Code: " + responseCode + ")";
                }
            }
        } else if (response != null) { // Có response nhưng không có error body (hoặc rỗng)
            errorMessage = defaultMessage + " (Code: " + responseCode + ")";
        }
        // Nếu response là null, errorMessage sẽ là defaultMessage (đã có "No response" từ trước)

        final String finalErrorMessage = errorMessage;
        runOnUiThread(() -> Toast.makeText(ProfileActivity.this, finalErrorMessage, Toast.LENGTH_LONG).show());
    }


    private void showLoading(boolean isLoading) {
        runOnUiThread(() -> {
            binding.progressBarProfile.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnChangeProfilePic.setEnabled(!isLoading);
            binding.etProfileFullName.setEnabled(!isLoading); // TextInputLayout sẽ tự disable EditText bên trong
            binding.etProfileBio.setEnabled(!isLoading);
            binding.btnSaveProfile.setEnabled(!isLoading);
        });
    }

    private void navigateToLoginAndFinish() {
        Toast.makeText(this, "Session expired or your credentials are no longer valid. Please login again.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity(); // Đóng tất cả activity của app và finish activity này
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}