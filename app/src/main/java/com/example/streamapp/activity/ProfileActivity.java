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
import com.example.streamapp.R; // Đảm bảo import R
import com.example.streamapp.databinding.ActivityProfileBinding; // Đảm bảo tên Binding đúng
import com.example.streamapp.model.MessageResponse;
import com.example.streamapp.model.ProfileUpdateRequest;
import com.example.streamapp.model.UserProfileResponse; // Vẫn import để dùng nếu API GET có sau này
import com.example.streamapp.network.ApiClient;
import com.example.streamapp.network.ApiService;
import com.example.streamapp.utils.SessionManager;
import com.google.gson.Gson;

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
    private Uri selectedImageUri; // Lưu URI ảnh được chọn để upload
    private static final String TAG = "ProfileActivity";

    // --- ActivityResultLaunchers ---
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> requestImagePermissionLauncher;

    // Quyền đọc ảnh (xử lý cho Android 13+)
    private static final String READ_IMAGES_PERMISSION = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ? Manifest.permission.READ_MEDIA_IMAGES
            : Manifest.permission.READ_EXTERNAL_STORAGE;

    // Biến tạm để lưu thông tin lấy từ SharedPreferences hoặc decode từ token
    private String currentUsername = "N/A";
    private String currentEmail = "N/A";
    private String currentFullName = ""; // Khởi tạo rỗng
    private String currentBio = "";       // Khởi tạo rỗng
    private String currentProfilePicUrl = null; // Khởi tạo null

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate layout sử dụng ViewBinding
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // --- Initialize ---
        apiService = ApiClient.getApiService(getApplicationContext());
        sessionManager = new SessionManager(getApplicationContext());

        // --- Setup Toolbar ---
        setSupportActionBar(binding.toolbarProfile); // Sử dụng ID từ layout mới
        if (getSupportActionBar() != null) {
            // getSupportActionBar().setTitle("Profile"); // Tiêu đề đã có trong layout
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Hiển thị nút back
            getSupportActionBar().setDisplayShowTitleEnabled(true); // Hiện tiêu đề "Profile" trên toolbar
        }

        // Khởi tạo các launchers
        setupLaunchers();

        // --- Set Listeners ---
        binding.btnChangeProfilePic.setOnClickListener(v -> checkPermissionAndOpenImagePicker());
        binding.btnSaveProfile.setOnClickListener(v -> attemptSaveProfile());

        // --- Load Initial Data ---
        // Không gọi API GET vì không tồn tại, thay vào đó lấy từ nguồn khác
        loadInitialDataFromStorageOrToken();
    }

    /**
     * Khởi tạo các ActivityResultLaunchers để xử lý xin quyền và chọn ảnh.
     */
    private void setupLaunchers() {
        // Launcher xin quyền đọc ảnh
        requestImagePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openImagePicker(); // Mở trình chọn ảnh nếu được cấp quyền
                    } else {
                        Toast.makeText(this, "Permission denied. Cannot select image.", Toast.LENGTH_SHORT).show();
                    }
                });

        // Launcher chọn ảnh từ thư viện
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri; // Lưu URI ảnh đã chọn
                        Log.d(TAG, "Image selected: " + selectedImageUri.toString());
                        // Hiển thị ảnh mới và bắt đầu upload
                        loadProfilePicture(selectedImageUri.toString()); // Hiển thị ảnh tạm thời
                        attemptUploadProfilePicture(); // Gọi hàm upload
                    } else {
                        Log.d(TAG, "Image selection cancelled.");
                    }
                });
    }

    /**
     * Kiểm tra quyền đọc ảnh, nếu chưa có thì yêu cầu, nếu có thì mở trình chọn ảnh.
     */
    private void checkPermissionAndOpenImagePicker() {
        if (ContextCompat.checkSelfPermission(this, READ_IMAGES_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        } else {
            Log.d(TAG, "Requesting image permission: " + READ_IMAGES_PERMISSION);
            requestImagePermissionLauncher.launch(READ_IMAGES_PERMISSION);
        }
    }

    /**
     * Mở trình chọn ảnh của hệ thống.
     */
    private void openImagePicker() {
        Log.d(TAG, "Launching image picker.");
        imagePickerLauncher.launch("image/*"); // Chỉ cho phép chọn ảnh
    }


    /**
     * Tải và hiển thị dữ liệu profile ban đầu từ SharedPreferences hoặc Token.
     */
    private void loadInitialDataFromStorageOrToken() {
        Log.d(TAG, "Loading initial profile data...");
        String token = sessionManager.getToken();

        // --- Ưu tiên lấy từ SharedPreferences (Cần thêm hàm vào SessionManager) ---
        // currentUsername = sessionManager.getUsername("N/A");
        // currentEmail = sessionManager.getEmail("N/A");
        // currentFullName = sessionManager.getFullName("");
        // currentBio = sessionManager.getBio("");
        // currentProfilePicUrl = sessionManager.getProfilePicUrl(null);

        // --- Fallback: Lấy từ Token (Nếu chưa lưu vào SharedPreferences) ---
        if (currentUsername.equals("N/A") && token != null && !token.startsWith("dummy-test-token")) {
            try {
                String[] chunks = token.split("\\.");
                if (chunks.length >= 2) {
                    byte[] data = android.util.Base64.decode(chunks[1], android.util.Base64.URL_SAFE);
                    String decodedPayload = new String(data, "UTF-8");
                    com.google.gson.JsonObject payloadJson = new Gson().fromJson(decodedPayload, com.google.gson.JsonObject.class);
                    if (payloadJson != null) {
                        if(payloadJson.has("sub")) currentUsername = payloadJson.get("sub").getAsString();
                        if(payloadJson.has("email")) currentEmail = payloadJson.get("email").getAsString();
                        // Giả sử token có thể chứa thêm thông tin (ít phổ biến)
                        // if(payloadJson.has("name")) currentFullName = payloadJson.get("name").getAsString();
                        // if(payloadJson.has("picture")) currentProfilePicUrl = payloadJson.get("picture").getAsString();
                        Log.d(TAG,"Data decoded from token - User: " + currentUsername + ", Email: " + currentEmail);
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Error decoding token for initial data", e); }
        }

        // --- Cập nhật UI ---
        runOnUiThread(() -> {
            binding.tvProfileUsername.setText(currentUsername);
            binding.tvProfileEmail.setText(currentEmail);
            binding.etProfileFullName.setText(currentFullName); // Hiển thị tên đã lưu (nếu có)
            binding.etProfileBio.setText(currentBio);          // Hiển thị bio đã lưu (nếu có)
            loadProfilePicture(currentProfilePicUrl); // Load ảnh đại diện đã lưu
        });
    }

    /**
     * Load ảnh đại diện vào ImageView bằng Glide.
     * @param imageUrl URL của ảnh (có thể null).
     */
    private void loadProfilePicture(String imageUrl) {
        runOnUiThread(() -> {
            Log.d(TAG, "Loading profile picture from URL: " + imageUrl);
            RequestOptions requestOptions = RequestOptions.circleCropTransform()
                    .placeholder(R.drawable.ic_default_avatar) // Ảnh mặc định
                    .error(R.drawable.ic_default_avatar)       // Ảnh khi lỗi
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // Không cache nếu URL thay đổi (SAS token)
                    .skipMemoryCache(true);                    // Bỏ qua cache memory

            Glide.with(this)
                    .load(imageUrl) // Glide tự xử lý nếu imageUrl là null
                    .apply(requestOptions)
                    .into(binding.ivProfilePic); // Load vào CircleImageView
        });
    }

    /**
     * Xử lý sự kiện nhấn nút "Save Changes".
     */
    private void attemptSaveProfile() {
        // Lấy dữ liệu mới từ các EditText
        String newFullName = binding.etProfileFullName.getText().toString().trim();
        String newBio = binding.etProfileBio.getText().toString().trim();
        // Email lấy từ TextView (không cho sửa)
        String email = binding.tvProfileEmail.getText().toString();

        // Kiểm tra xem có thực sự thay đổi không (so với dữ liệu hiện tại)
        if (TextUtils.equals(newFullName, currentFullName) && TextUtils.equals(newBio, currentBio)) {
            Toast.makeText(this, "No changes detected.", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("N/A".equals(email) || TextUtils.isEmpty(email)) { // Kiểm tra email hợp lệ
            Toast.makeText(this, "Cannot save profile without a valid email.", Toast.LENGTH_SHORT).show();
            return;
        }


        String token = sessionManager.getToken();
        if (token == null || token.startsWith("dummy-test-token")) {
            handleApiError(null, "Session expired. Please login again.", true);
            return;
        }

        showLoading(true);

        // **QUAN TRỌNG:** ProfileUpdateRequest hiện chỉ có fullName, email.
        // Nếu backend chỉ xử lý 2 trường này thì giữ nguyên.
        // Nếu backend xử lý cả bio, cần sửa Model và API.
        ProfileUpdateRequest updateRequest = new ProfileUpdateRequest(newFullName, email);
        // ProfileUpdateRequest updateRequest = new ProfileUpdateRequest(newFullName, email, newBio); // Nếu có bio

        Log.d(TAG, "Attempting to update profile...");
        apiService.updateProfile("Bearer " + token, updateRequest).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(@NonNull Call<MessageResponse> call, @NonNull Response<MessageResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(ProfileActivity.this, response.body().getMessage(), Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Profile updated successfully.");
                    // Cập nhật lại các biến tạm sau khi lưu thành công
                    currentFullName = newFullName;
                    currentBio = newBio;
                    // TODO: Lưu giá trị mới vào SharedPreferences
                    // sessionManager.saveFullName(currentFullName);
                    // sessionManager.saveBio(currentBio);
                } else {
                    handleApiError(response, "Failed to update profile", false);
                    // Không khôi phục lại EditText vì người dùng có thể muốn sửa lại lỗi
                }
            }

            @Override
            public void onFailure(@NonNull Call<MessageResponse> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                showLoading(false);
                Log.e(TAG, "API Call Failed (updateProfile): ", t);
                Toast.makeText(ProfileActivity.this, "Error updating profile: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Xử lý việc upload ảnh đại diện mới.
     */
    private void attemptUploadProfilePicture() {
        if (selectedImageUri == null) {
            Log.w(TAG, "attemptUploadProfilePicture called but selectedImageUri is null");
            return;
        }
        String token = sessionManager.getToken();
        if (token == null || token.startsWith("dummy-test-token")) {
            handleApiError(null, "Session expired. Please login again.", true);
            // Khôi phục ảnh cũ nếu có
            loadProfilePicture(currentProfilePicUrl);
            return;
        }

        showLoading(true);

        // Chạy trên background thread để chuẩn bị file
        new Thread(() -> {
            RequestBody filePart = null;
            MultipartBody.Part imageMultiPart = null;
            boolean prepSuccess = false;
            try {
                filePart = createImageRequestBody(selectedImageUri);
                if (filePart == null) throw new IOException("Could not create RequestBody for image");
                String fileName = getFileNameFromUri(selectedImageUri);
                imageMultiPart = MultipartBody.Part.createFormData("file", fileName.equals("unknown_file") ? "profile.jpg" : fileName, filePart);
                prepSuccess = true;
            } catch (Exception e) { // Bắt cả IOException và lỗi khác
                Log.e(TAG, "Error during image preparation", e);
                runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Error preparing image: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            // Gọi API nếu chuẩn bị thành công
            if (prepSuccess && imageMultiPart != null) {
                final MultipartBody.Part finalImageMultiPart = imageMultiPart;
                Log.d(TAG, "Calling uploadProfilePicture API...");
                apiService.uploadProfilePicture("Bearer " + token, finalImageMultiPart).enqueue(new Callback<MessageResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<MessageResponse> call, @NonNull Response<MessageResponse> response) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            if (response.isSuccessful() && response.body() != null) {
                                Toast.makeText(ProfileActivity.this, response.body().getMessage(), Toast.LENGTH_SHORT).show();
                                String newImageUrl = response.body().getUrl();
                                Log.i(TAG, "Profile picture uploaded. New URL: " + newImageUrl);
                                currentProfilePicUrl = newImageUrl; // Cập nhật URL
                                // TODO: Lưu newImageUrl vào SharedPreferences
                                // sessionManager.saveProfilePicUrl(newImageUrl);
                                loadProfilePicture(currentProfilePicUrl); // Load ảnh mới nhất
                                selectedImageUri = null; // Reset sau khi upload thành công
                            } else {
                                handleApiError(response, "Failed to upload profile picture", false);
                                loadProfilePicture(currentProfilePicUrl); // Load lại ảnh cũ (từ biến tạm) khi lỗi
                            }
                        });
                    }
                    @Override
                    public void onFailure(@NonNull Call<MessageResponse> call, @NonNull Throwable t) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            Log.e(TAG, "API Call Failed (uploadProfilePicture): ", t);
                            Toast.makeText(ProfileActivity.this, "Error uploading picture: " + t.getMessage(), Toast.LENGTH_LONG).show();
                            loadProfilePicture(currentProfilePicUrl); // Load lại ảnh cũ (từ biến tạm) khi lỗi
                        });
                    }
                });
            } else {
                // Lỗi chuẩn bị, tắt loading trên UI thread
                runOnUiThread(() -> showLoading(false));
            }
        }).start();
    }

    /**
     * Helper tạo RequestBody cho file ảnh.
     */
    private RequestBody createImageRequestBody(Uri uri) throws IOException {
        InputStream inputStream = null;
        ByteArrayOutputStream byteBuffer = null;
        try {
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) throw new IOException("Unable to open InputStream for URI: " + uri);

            String mimeType = getContentResolver().getType(uri);
            if (mimeType == null) {
                String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
            }
            if (mimeType == null || !mimeType.startsWith("image/")) {
                mimeType = "image/jpeg"; // Mặc định nếu không xác định được
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
            // Đóng các stream
            if (inputStream != null) try { inputStream.close(); } catch (IOException e) { Log.e(TAG, "Error closing image InputStream", e); }
            if (byteBuffer != null) try { byteBuffer.close(); } catch (IOException e) { Log.e(TAG, "Error closing image ByteArrayOutputStream", e); }
        }
    }

    /**
     * Helper lấy tên file từ Content URI.
     */
    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex > -1) fileName = cursor.getString(nameIndex);
                }
            } catch (Exception e) { Log.e(TAG, "Error querying filename", e); }
            finally { if (cursor != null) cursor.close(); }
        }
        if (fileName == null) {
            fileName = uri.getPath();
            if (fileName != null) {
                int cut = fileName.lastIndexOf('/');
                if (cut != -1) fileName = fileName.substring(cut + 1);
            }
        }
        return (fileName != null && !fileName.isEmpty()) ? fileName : "unknown_file";
    }

    /**
     * Xử lý lỗi API chung.
     */
    private void handleApiError(Response<?> response, String defaultMessage, boolean logoutOnError) {
        if (isFinishing() || isDestroyed()) return;
        String errorMessage = defaultMessage;
        int code = response != null ? response.code() : -1;
        Log.e(TAG, defaultMessage + " - Code: " + code);

        if ((response == null || code == 401 || code == 403) && logoutOnError) {
            runOnUiThread(()->{
                Toast.makeText(this, "Session expired or invalid. Please login again.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
            return;
        }

        String errorBodyContent = "";
        if (response != null && response.errorBody() != null) {
            try { errorBodyContent = response.errorBody().string(); Log.e(TAG, "API Error Body Raw: " + errorBodyContent); }
            catch (Exception e) { Log.e(TAG, "Error reading error body", e); }
        }

        if (!errorBodyContent.isEmpty()) {
            try {
                MessageResponse errorMsg = new Gson().fromJson(errorBodyContent, MessageResponse.class);
                if (errorMsg != null && !TextUtils.isEmpty(errorMsg.getMessage())) { errorMessage = errorMsg.getMessage(); }
                else { errorMessage += " (Code: " + code + ")"; }
            } catch (Exception jsonError){
                Log.w(TAG,"Could not parse error body as JSON: " + jsonError.getMessage());
                errorMessage += " (Code: " + code + ")";
            }
        } else if (response != null) { errorMessage += " (Code: " + code + ")"; }
        else { errorMessage += " (No response)"; }

        final String finalErrorMessage = errorMessage;
        runOnUiThread(() -> Toast.makeText(this, finalErrorMessage, Toast.LENGTH_LONG).show());
    }

    /**
     * Hiển thị/Ẩn ProgressBar và quản lý trạng thái enable của các View.
     */
    private void showLoading(boolean isLoading) {
        runOnUiThread(() -> {
            binding.progressBarProfile.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnChangeProfilePic.setEnabled(!isLoading);
            binding.tilFullName.setEnabled(!isLoading); // Disable/enable TextInputLayout
            binding.tilBio.setEnabled(!isLoading);
            binding.btnSaveProfile.setEnabled(!isLoading);
            // Giữ ImageView enable để có thể nhấn Change Picture
            // binding.ivProfilePic.setEnabled(!isLoading);
        });
    }

    /**
     * Xử lý sự kiện nhấn nút back trên Toolbar.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Đóng Activity Profile
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}