package com.example.streamapp.activity; // Hoặc package của bạn

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

import com.example.streamapp.R; // Đảm bảo import đúng R
import com.example.streamapp.databinding.ActivityUploadBinding;
import com.example.streamapp.model.MediaUploadRequest;
import com.example.streamapp.model.MessageResponse;
import com.example.streamapp.network.ApiClient;
import com.example.streamapp.network.ApiService;
import com.example.streamapp.utils.SessionManager; // Cần SessionManager để lấy token
import com.google.gson.Gson;

// Thêm import này
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UploadActivity extends AppCompatActivity {

    private ActivityUploadBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private Uri selectedFileUri; // Lưu URI của file được chọn
    private static final String TAG = "UploadActivity";

    // --- ActivityResultLaunchers ---
    private ActivityResultLauncher<String[]> filePickerLauncher; // Dùng mảng String cho nhiều loại MIME
    private ActivityResultLauncher<String> requestPermissionLauncher;

    // Xác định quyền cần xin dựa trên phiên bản Android
    private static final String READ_STORAGE_PERMISSION = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ? Manifest.permission.READ_MEDIA_VIDEO // Chỉ xin quyền video ví dụ, có thể cần cả READ_MEDIA_AUDIO
            : Manifest.permission.READ_EXTERNAL_STORAGE;

    // Thêm quyền đọc audio cho Android 13+ nếu cần upload cả nhạc
    private static final String READ_AUDIO_PERMISSION_TIRAMISU = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ? Manifest.permission.READ_MEDIA_AUDIO
            : null; // Không cần quyền riêng cho audio ở API < 33


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUploadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        // --- Setup ActionBar (nếu muốn có nút back) ---
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Upload Media");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Hiển thị nút back
        }

        setupLaunchers(); // Khởi tạo các launcher

        binding.btnSelectFile.setOnClickListener(v -> checkPermissionAndOpenFilePicker());
        // Thêm Log cho nút Upload để kiểm tra listener
        binding.btnUpload.setOnClickListener(v -> {
            Log.d(TAG, "Upload Button Clicked!");
            attemptUpload();
        });
    }

    // --- Setup ActivityResultLaunchers ---
    private void setupLaunchers() {
        // Launcher để yêu cầu quyền (ví dụ chỉ xin quyền Video trước)
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        // Nếu cần cả quyền Audio trên API 33+, kiểm tra và xin tiếp
                        if (READ_AUDIO_PERMISSION_TIRAMISU != null && ContextCompat.checkSelfPermission(this, READ_AUDIO_PERMISSION_TIRAMISU) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Có thể tạo một launcher riêng cho quyền audio hoặc xử lý logic phức tạp hơn
                            // Tạm thời vẫn mở picker, hy vọng người dùng chọn đúng loại file mà quyền đã cấp
                            Log.w(TAG, "Video permission granted, but Audio permission might be needed for music uploads on API 33+");
                            openFilePicker();
                        } else {
                            openFilePicker(); // Nếu đủ quyền hoặc API < 33 thì mở file picker
                        }
                    } else {
                        Toast.makeText(this, "Permission denied. Cannot select file.", Toast.LENGTH_SHORT).show();
                    }
                });

        // Launcher để chọn file
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), // Dùng OpenDocument để có URI ổn định hơn
                uri -> {
                    if (uri != null) {
                        // Lấy quyền truy cập URI lâu dài (quan trọng với OpenDocument)
                        try {
                            // Gọi takePersistableUriPermission trực tiếp
                            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                            getContentResolver().takePersistableUriPermission(uri, takeFlags);
                            Log.d(TAG, "Attempted to take persistable read permission for URI: " + uri);

                        } catch (SecurityException e) {
                            // Lỗi này xảy ra nếu URI không hỗ trợ quyền lâu dài hoặc có vấn đề khác
                            Log.e(TAG, "Failed to take persistable permission for URI: " + uri, e);
                            Toast.makeText(this, "Could not secure long-term access to the file. Access might be temporary.", Toast.LENGTH_LONG).show();
                            // Vẫn có thể tiếp tục với URI tạm thời, nhưng có thể mất quyền truy cập sau này
                        } catch (Exception e) { // Bắt lỗi chung khác
                            Log.e(TAG, "Error handling URI permissions for: " + uri, e);
                        }

                        selectedFileUri = uri;
                        String fileName = getFileNameFromUri(uri);
                        binding.tvSelectedFileName.setText("Selected: " + fileName);
                        Log.d(TAG, "File URI: " + selectedFileUri.toString());
                    } else {
                        selectedFileUri = null;
                        binding.tvSelectedFileName.setText("No file selected");
                    }
                });
    }


    // --- Permission Handling and File Picking ---
    private void checkPermissionAndOpenFilePicker() {
        // Kiểm tra quyền chính (Video hoặc Storage cũ)
        if (ContextCompat.checkSelfPermission(this, READ_STORAGE_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            // Nếu cần cả quyền Audio trên API 33+, kiểm tra tiếp
            if (READ_AUDIO_PERMISSION_TIRAMISU != null && ContextCompat.checkSelfPermission(this, READ_AUDIO_PERMISSION_TIRAMISU) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Xử lý xin thêm quyền Audio nếu cần thiết cho loại file MUSIC
                // Tạm thời vẫn mở picker
                Log.w(TAG, "Attempting to open picker without guaranteed Audio permission on API 33+");
                openFilePicker();
            } else {
                openFilePicker(); // Đã có đủ quyền cần thiết
            }
        } else {
            // Yêu cầu quyền chính trước
            Log.d(TAG, "Requesting permission: " + READ_STORAGE_PERMISSION);
            requestPermissionLauncher.launch(READ_STORAGE_PERMISSION);
        }
    }

    private void openFilePicker() {
        // Mở trình chọn file, cho phép chọn audio hoặc video
        Log.d(TAG, "Launching file picker for audio/* and video/*");
        filePickerLauncher.launch(new String[]{"audio/*", "video/*"});
    }

    // --- Upload Logic ---
    private void attemptUpload() {
        Log.d(TAG, "attemptUpload() started"); // LOG 1

        String title = binding.etUploadTitle.getText().toString().trim();
        String description = binding.etUploadDescription.getText().toString().trim(); // Có thể rỗng
        String mediaType = binding.rbMusic.isChecked() ? "MUSIC" : "VIDEO";
        boolean isPublic = binding.swPublic.isChecked();

        // --- Validation ---
        if (selectedFileUri == null) {
            Log.w(TAG, "attemptUpload() failed: No file selected"); // LOG 2a
            Toast.makeText(this, "Please select a media file first.", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "attemptUpload: File selected - " + selectedFileUri); // LOG 2b

        if (TextUtils.isEmpty(title)) {
            Log.w(TAG, "attemptUpload() failed: Title is empty"); // LOG 3a
            binding.etUploadTitle.setError("Title is required");
            binding.etUploadTitle.requestFocus();
            return;
        }
        Log.d(TAG, "attemptUpload: Title is valid - " + title); // LOG 3b
        // --- End Validation ---

        String token = sessionManager.getToken();
        if (token == null) {
            Log.w(TAG, "attemptUpload() failed: Token is null"); // LOG 4a
            // Toast đã có trong handleApiError
            handleApiError(null, "Session expired"); // Gọi hàm xử lý lỗi để điều hướng về Login
            return;
        }
        Log.d(TAG, "attemptUpload: Token found"); // LOG 4b

        // Hiển thị loading TRƯỚC KHI bắt đầu thread
        showLoading(true);

        Log.d(TAG, "attemptUpload: Starting background thread..."); // LOG 5
        // Chạy việc chuẩn bị file và gọi API trong background thread
        new Thread(() -> {
            Log.d(TAG, "attemptUpload: Inside background thread - run()"); // LOG 6
            RequestBody dataPart = null;
            RequestBody filePart = null;
            MultipartBody.Part fileMultiPart = null;
            boolean preparationSuccess = false;

            try {
                Log.d(TAG, "attemptUpload: Preparing dataPart..."); // LOG 7
                // 1. Tạo RequestBody cho metadata (JSON)
                MediaUploadRequest mediaData = new MediaUploadRequest(title, description, mediaType, isPublic);
                String mediaDataJson = new Gson().toJson(mediaData);
                dataPart = RequestBody.create(mediaDataJson, MediaType.parse("application/json; charset=utf-8"));
                Log.d(TAG, "attemptUpload: dataPart prepared."); // LOG 8

                Log.d(TAG, "attemptUpload: Preparing filePart..."); // LOG 9
                // 2. Tạo RequestBody cho file (trong try-catch riêng cho lỗi file)
                filePart = createFileRequestBody(selectedFileUri);
                if (filePart == null) {
                    Log.e(TAG, "attemptUpload: filePart is null after creation"); // LOG 10a
                    runOnUiThread(() -> { // Cập nhật UI từ background thread
                        showLoading(false);
                        Toast.makeText(UploadActivity.this, "Error preparing file for upload.", Toast.LENGTH_SHORT).show();
                    });
                    return; // Dừng thread
                }
                Log.d(TAG, "attemptUpload: filePart prepared."); // LOG 10b


                Log.d(TAG, "attemptUpload: Preparing fileMultiPart..."); // LOG 11
                // 3. Tạo MultipartBody.Part
                String fileName = getFileNameFromUri(selectedFileUri);
                fileMultiPart = MultipartBody.Part.createFormData("file", fileName, filePart);
                preparationSuccess = true;
                Log.d(TAG, "attemptUpload: fileMultiPart prepared. Preparation success: " + preparationSuccess); // LOG 12


            } catch (IOException e) {
                Log.e(TAG, "IOException during file preparation", e);
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(UploadActivity.this, "Error preparing file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) { // Bắt các lỗi khác có thể xảy ra
                Log.e(TAG, "Exception during preparation", e);
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(UploadActivity.this, "An unexpected error occurred during preparation.", Toast.LENGTH_SHORT).show();
                });
            }

            // Chỉ gọi API nếu chuẩn bị thành công
            if (preparationSuccess && fileMultiPart != null && dataPart != null) {
                final MultipartBody.Part finalFileMultiPart = fileMultiPart;
                final RequestBody finalDataPart = dataPart;

                Log.d(TAG, "attemptUpload: Calling API..."); // LOG 13
                // Gọi API (Retrofit tự xử lý callback trên Main thread)
                apiService.uploadMedia("Bearer " + token, finalFileMultiPart, finalDataPart).enqueue(new Callback<MessageResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<MessageResponse> call, @NonNull Response<MessageResponse> response) {
                        Log.d(TAG, "attemptUpload: API onResponse - Code: " + response.code()); // LOG 14
                        runOnUiThread(() -> { // Đảm bảo cập nhật UI trên Main thread
                            // Luôn tắt loading dù thành công hay lỗi
                            showLoading(false);
                            if (response.isSuccessful() && response.body() != null) {
                                Toast.makeText(UploadActivity.this, response.body().getMessage(), Toast.LENGTH_LONG).show();
                                Log.i(TAG, "Upload successful. URL: " + response.body().getUrl());
                                // Gửi kết quả thành công về MainActivity nếu cần refresh
                                setResult(RESULT_OK); // Đặt kết quả là OK
                                finish(); // Đóng màn hình upload
                            } else {
                                handleApiError(response, "Upload failed");
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull Call<MessageResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "attemptUpload: API onFailure", t); // LOG 15
                        runOnUiThread(() -> { // Đảm bảo cập nhật UI trên Main thread
                            // Luôn tắt loading khi có lỗi
                            showLoading(false);
                            // Kiểm tra lỗi cụ thể
                            if (t instanceof java.net.SocketTimeoutException) {
                                Toast.makeText(UploadActivity.this, "Upload failed: Connection timed out. Please try again.", Toast.LENGTH_LONG).show();
                            } else if (t instanceof IOException) {
                                Toast.makeText(UploadActivity.this, "Upload failed: Network error. Please check your connection.", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(UploadActivity.this, "Upload failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
            } else if (!preparationSuccess) {
                Log.w(TAG, "attemptUpload: API call skipped because preparation failed."); // LOG 16
                // Đảm bảo loading tắt nếu lỗi xảy ra trong quá trình chuẩn bị
                runOnUiThread(() -> showLoading(false));
            }
        }).start(); // Bắt đầu background thread
    }


    // Helper để tạo RequestBody từ Uri (sử dụng ByteArrayOutputStream) - Đã sửa lỗi API level
    private RequestBody createFileRequestBody(Uri uri) throws IOException {
        InputStream inputStream = null;
        ByteArrayOutputStream byteBuffer = null; // Khai báo ngoài để đảm bảo đóng nếu cần
        try {
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "Unable to open InputStream for URI: " + uri);
                return null;
            }

            // Lấy kiểu MIME
            String mimeType = getContentResolver().getType(uri);
            if (mimeType == null) {
                String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
            }
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }
            Log.d(TAG, "File MIME Type: " + mimeType);

            // Sử dụng ByteArrayOutputStream để đọc InputStream tương thích API cũ
            byteBuffer = new ByteArrayOutputStream();
            int bufferSize = 1024 * 4; // 4KB buffer
            byte[] buffer = new byte[bufferSize];

            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            byte[] fileBytes = byteBuffer.toByteArray();

            return RequestBody.create(fileBytes, MediaType.parse(mimeType));

        } finally {
            // Đảm bảo cả hai stream đều được đóng
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing InputStream", e);
                }
            }
            if (byteBuffer != null) {
                try {
                    byteBuffer.close(); // ByteArrayOutputStream cũng nên được đóng
                } catch (IOException e) {
                    Log.e(TAG, "Error closing ByteArrayOutputStream", e);
                }
            }
        }
    }


    // Helper để lấy tên file từ Content URI (giữ nguyên)
    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            Cursor cursor = null; // Khai báo ngoài try
            try {
                // Chỉ yêu cầu cột DISPLAY_NAME
                cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error querying filename from URI: " + uri, e);
                // Có thể thử lấy từ path nếu query lỗi
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        // Nếu không lấy được từ ContentResolver hoặc scheme khác
        if (fileName == null) {
            fileName = uri.getPath();
            if (fileName != null) {
                int cut = fileName.lastIndexOf('/');
                if (cut != -1) {
                    fileName = fileName.substring(cut + 1);
                }
            }
        }
        // Xử lý trường hợp tên file vẫn null hoặc rỗng
        return (fileName != null && !fileName.isEmpty()) ? fileName : "unknown_file";
    }

    // Hàm xử lý lỗi API chung (giữ nguyên, đã có điều hướng về Login)
    private void handleApiError(Response<?> response, String defaultMessage) {
        String errorMessage = defaultMessage;
        int code = response != null ? response.code() : -1; // Kiểm tra null cho response
        Log.e(TAG, defaultMessage + " - Code: " + code);

        // Xử lý session hết hạn hoặc không có token
        if (response == null || code == 401 || code == 403) {
            // Đảm bảo Toast và Intent chạy trên UI thread nếu hàm này được gọi từ background
            runOnUiThread(() -> {
                Toast.makeText(this, "Session expired or invalid. Please login again.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
            return;
        }

        // Xử lý các lỗi khác từ server
        if (response.errorBody() != null) {
            try {
                // Cố gắng parse lỗi cụ thể
                MessageResponse errorMsg = new Gson().fromJson(response.errorBody().charStream(), MessageResponse.class);
                if (errorMsg != null && !TextUtils.isEmpty(errorMsg.getMessage())) {
                    errorMessage = errorMsg.getMessage();
                } else {
                    // Nếu không parse được hoặc không có message, dùng thông tin chung
                    errorMessage += ": " + code + " " + response.message();
                }
                Log.e(TAG, "API Error Body: " + (errorMsg != null ? new Gson().toJson(errorMsg) : "Could not parse as MessageResponse"));
            } catch (Exception e) {
                Log.e(TAG, "Error reading/parsing error body", e);
                errorMessage += ": " + code + " " + response.message(); // Lỗi khi đọc error body
            }
        } else {
            // Trường hợp không có error body nhưng isSuccessful là false
            errorMessage += " (Code: " + code + ")";
        }
        // Hiển thị Toast trên UI thread
        final String finalErrorMessage = errorMessage;
        runOnUiThread(() -> Toast.makeText(this, finalErrorMessage, Toast.LENGTH_LONG).show());
    }

    // Hàm showLoading cần đảm bảo chạy trên UI thread nếu được gọi từ background
    private void showLoading(boolean isLoading) {
        runOnUiThread(() -> {
            binding.progressBarUpload.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnSelectFile.setEnabled(!isLoading);
            binding.etUploadTitle.setEnabled(!isLoading);
            binding.etUploadDescription.setEnabled(!isLoading);
            binding.rbMusic.setEnabled(!isLoading);
            binding.rbVideo.setEnabled(!isLoading);
            binding.swPublic.setEnabled(!isLoading);
            binding.btnUpload.setEnabled(!isLoading);
        });
    }


    // Xử lý nút back trên ActionBar (giữ nguyên)
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Xem xét hành vi khi nhấn back: hủy upload hay chỉ đơn giản là quay lại?
            // Nếu đang upload, có thể hỏi người dùng xác nhận.
            // Hiện tại chỉ đơn giản là quay lại.
            onBackPressed(); // Gọi hành vi back mặc định
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}