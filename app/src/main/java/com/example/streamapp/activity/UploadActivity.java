// File: src/main/java/com/example/streamapp/activity/UploadActivity.java
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

import com.example.streamapp.R;
import com.example.streamapp.databinding.ActivityUploadBinding;
import com.example.streamapp.model.MediaUploadRequest;
import com.example.streamapp.model.MessageResponse;
import com.example.streamapp.model.ErrorResponse;
import com.example.streamapp.model.MediaResponse; // Model MediaResponse của client
import com.example.streamapp.network.ApiClient;
import com.example.streamapp.network.ApiService;
import com.example.streamapp.network.InputStreamRequestBody; // Import lớp mới
import com.example.streamapp.utils.SessionManager;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException; // Thêm import này

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
    private Uri selectedFileUri;
    private static final String TAG = "UploadActivity";

    private ActivityResultLauncher<String[]> filePickerLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private static final String READ_MEDIA_VIDEO_PERMISSION = Manifest.permission.READ_MEDIA_VIDEO;
    private static final String READ_MEDIA_AUDIO_PERMISSION = Manifest.permission.READ_MEDIA_AUDIO;
    private static final String READ_EXTERNAL_STORAGE_PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE;

    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{READ_MEDIA_VIDEO_PERMISSION, READ_MEDIA_AUDIO_PERMISSION};
        } else {
            return new String[]{READ_EXTERNAL_STORAGE_PERMISSION};
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUploadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Upload Media");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        setupLaunchers();
        binding.btnSelectFile.setOnClickListener(v -> checkPermissionsAndOpenFilePicker());
        binding.btnUpload.setOnClickListener(v -> {
            Log.d(TAG, "Upload Button Clicked!");
            attemptUploadWithClientSideSizeCheck(); // Gọi hàm kiểm tra size trước
        });
    }

    private void setupLaunchers() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Log.d(TAG, "A storage/media permission granted.");
                        checkPermissionsAndOpenFilePicker(); // Kiểm tra lại tất cả các quyền
                    } else {
                        Toast.makeText(this, "Permission denied. Cannot select file.", Toast.LENGTH_SHORT).show();
                    }
                });

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        try {
                            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                            getContentResolver().takePersistableUriPermission(uri, takeFlags);
                        } catch (SecurityException e) {
                            Log.e(TAG, "Failed to take persistable read/write permission for URI: " + uri, e);
                        }
                        selectedFileUri = uri;
                        String fileName = getFileNameFromUri(uri);
                        binding.tvSelectedFileName.setText(fileName != null ? "Selected: " + fileName : "File selected (name unknown)");
                        Log.d(TAG, "File selected: " + selectedFileUri.toString());
                    } else {
                        selectedFileUri = null;
                        binding.tvSelectedFileName.setText("No file selected");
                        Log.d(TAG, "File selection cancelled.");
                    }
                });
    }

    private void checkPermissionsAndOpenFilePicker() {
        String[] permissionsToRequest = getRequiredPermissions();
        boolean allPermissionsGranted = true;
        String firstUngrantedPermission = null;

        for (String permission : permissionsToRequest) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                firstUngrantedPermission = permission;
                break;
            }
        }

        if (allPermissionsGranted) {
            openFilePicker();
        } else if (firstUngrantedPermission != null) {
            Log.d(TAG, "Requesting permission: " + firstUngrantedPermission);
            requestPermissionLauncher.launch(firstUngrantedPermission);
        }
    }

    private void openFilePicker() {
        Log.d(TAG, "Launching file picker for audio/*, video/*");
        filePickerLauncher.launch(new String[]{"audio/*", "video/*"});
    }

    private void attemptUploadWithClientSideSizeCheck() {
        if (selectedFileUri == null) {
            Toast.makeText(this, "Please select a media file first.", Toast.LENGTH_SHORT).show();
            return;
        }

        long fileSize = -1;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(selectedFileUri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    fileSize = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not get file size from URI", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (fileSize != -1) {
            Log.d(TAG, "Selected file size: " + fileSize + " bytes (" + fileSize / (1024.0 * 1024.0) + " MB)");
            String mediaType = binding.rbMusic.isChecked() ? "MUSIC" : "VIDEO";
            long maxSizeVideo = 500L * 1024 * 1024; // 500MB
            long maxSizeMusic = 20L * 1024 * 1024; // 20MB (Tăng lên một chút)

            if ("VIDEO".equals(mediaType) && fileSize > maxSizeVideo) {
                Toast.makeText(this, "Video file is too large. Maximum size is 500MB.", Toast.LENGTH_LONG).show();
                return;
            } else if ("MUSIC".equals(mediaType) && fileSize > maxSizeMusic) {
                Toast.makeText(this, "Music file is too large. Maximum size is 20MB.", Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            Log.w(TAG, "Could not determine file size. Upload will proceed without client-side size check.");
        }

        // Nếu qua được kiểm tra size (hoặc không lấy được size) thì mới gọi attemptUpload
        attemptUpload();
    }


    private void attemptUpload() {
        String title = binding.etUploadTitle.getText().toString().trim();
        String description = binding.etUploadDescription.getText().toString().trim();
        String mediaType = binding.rbMusic.isChecked() ? "MUSIC" : "VIDEO";
        boolean isPublic = binding.swPublic.isChecked();

        if (selectedFileUri == null) { // Kiểm tra lại lần nữa, dù attemptUploadWithClientSideSizeCheck đã làm
            Toast.makeText(this, "Please select a media file.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(title)) {
            binding.etUploadTitle.setError("Title is required");
            binding.etUploadTitle.requestFocus();
            return;
        }

        String token = sessionManager.getToken();
        if (token == null) {
            handleApiError(null, "Session expired. Please login again.", true);
            return;
        }
        showLoading(true);
        Log.d(TAG, "Starting upload thread for: " + title);

        new Thread(() -> {
            try {
                MediaUploadRequest mediaData = new MediaUploadRequest(title, description, mediaType, isPublic);
                String mediaDataJson = new Gson().toJson(mediaData);
                RequestBody currentDataPart = RequestBody.create(mediaDataJson, MediaType.parse("application/json; charset=utf-8"));

                RequestBody currentFilePart = createStreamingRequestBody(selectedFileUri); // Sử dụng hàm mới
                // createStreamingRequestBody sẽ throw IOException nếu có lỗi, không cần kiểm tra null

                String fileName = getFileNameFromUri(selectedFileUri);
                MultipartBody.Part currentFileMultiPart = MultipartBody.Part.createFormData("file", fileName, currentFilePart);

                Log.d(TAG, "Preparation successful for " + fileName + ". Calling upload API...");
                // AuthInterceptor sẽ tự thêm token
                apiService.uploadMedia(currentFileMultiPart, currentDataPart).enqueue(new Callback<MediaResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<MediaResponse> call, @NonNull Response<MediaResponse> response) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            if (response.isSuccessful() && response.body() != null) {
                                MediaResponse uploadedMedia = response.body();
                                String successMessage = "Media '" + (uploadedMedia.getTitle() != null ? uploadedMedia.getTitle() : fileName) + "' uploaded successfully!";
                                Toast.makeText(UploadActivity.this, successMessage, Toast.LENGTH_LONG).show();
                                Log.i(TAG, "Upload successful. Server returned MediaResponse for title: " + uploadedMedia.getTitle() + ", URL: " + uploadedMedia.getUrl());
                                setResult(RESULT_OK);
                                finish();
                            } else {
                                Log.e(TAG, "Upload API call not successful. Code: " + response.code());
                                handleApiError(response, "Upload failed (Server)", false);
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull Call<MediaResponse> call, @NonNull Throwable t) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            Log.e(TAG, "Upload API Call Failed: ", t);
                            String errorMsg = "Upload failed: " + t.getMessage();
                            if (t instanceof SocketTimeoutException) {
                                errorMsg = "Upload timed out. Please check your connection.";
                            } else if (t instanceof IOException) {
                                errorMsg = "A network error occurred during upload. Please try again.";
                            }
                            Toast.makeText(UploadActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                        });
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "IOException during file preparation in upload thread: ", e);
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(UploadActivity.this, "Error preparing file for upload: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) { // Bắt các lỗi không mong muốn khác
                Log.e(TAG, "Unexpected error during file preparation or upload thread: ", e);
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(UploadActivity.this, "An unexpected error occurred during upload process.", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private RequestBody createStreamingRequestBody(Uri uri) throws IOException {
        if (uri == null) {
            throw new IOException("URI cannot be null for creating request body.");
        }

        String mimeTypeString = getContentResolver().getType(uri);
        if (mimeTypeString == null) {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (fileExtension != null) {
                mimeTypeString = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
            }
        }
        if (mimeTypeString == null) {
            Log.w(TAG, "MIME type is null for URI: " + uri + ", defaulting to application/octet-stream");
            mimeTypeString = "application/octet-stream";
        }
        Log.d(TAG, "Determined MIME Type for streaming upload: " + mimeTypeString);

        MediaType contentType = MediaType.parse(mimeTypeString);
        if (contentType == null) {
            Log.e(TAG, "Could not parse MIME type string: " + mimeTypeString + ". Defaulting to application/octet-stream.");
            contentType = MediaType.parse("application/octet-stream");
        }

        return new InputStreamRequestBody(contentType, getContentResolver(), uri);
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting filename from content URI: " + e.getMessage(), e);
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
        return (result != null && !result.isEmpty()) ? result : "unknown_media_file";
    }

    private void handleApiError(Response<?> response, String defaultMessage) {
        handleApiError(response, defaultMessage, true);
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
        Log.e(TAG, defaultMessage + " - Code: " + responseCode + ", RawErrorBody: " + errorBodyContent);

        if ((response == null || responseCode == 401 || responseCode == 403) && logoutOnError) {
            navigateToLoginAndFinish();
            return;
        }

        if (errorBodyContent != null && !errorBodyContent.isEmpty()) {
            try {
                ErrorResponse backendError = new Gson().fromJson(errorBodyContent, ErrorResponse.class);
                if (backendError != null && !TextUtils.isEmpty(backendError.getMessage())) {
                    errorMessage = backendError.getMessage();
                } else if (backendError != null && !TextUtils.isEmpty(backendError.getError())) {
                    errorMessage = backendError.getError() + " (Code: " + responseCode + ")";
                } else {
                    errorMessage = defaultMessage + " (Code: " + responseCode + ")";
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not parse error body as Backend ErrorResponse, trying MessageResponse. Error: " + e.getMessage());
                try {
                    MessageResponse msgResponse = new Gson().fromJson(errorBodyContent, MessageResponse.class);
                    if (msgResponse != null && !TextUtils.isEmpty(msgResponse.getMessage())) {
                        errorMessage = msgResponse.getMessage();
                    } else {
                        errorMessage = defaultMessage + " (Code: " + responseCode + ")";
                    }
                } catch (Exception e2) {
                    Log.w(TAG, "Could not parse error body as MessageResponse either. Error: " + e2.getMessage());
                    errorMessage = defaultMessage + " (Raw: " + errorBodyContent.substring(0, Math.min(100, errorBodyContent.length())) + "... Code: " + responseCode + ")";
                }
            }
        } else if (response != null) {
            errorMessage = defaultMessage + " (Code: " + responseCode + ")";
        } else {
            errorMessage = defaultMessage + " (No response from server)";
        }

        final String finalErrorMessage = errorMessage;
        runOnUiThread(() -> Toast.makeText(UploadActivity.this, finalErrorMessage, Toast.LENGTH_LONG).show());
    }

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

    private void navigateToLoginAndFinish() {
        Toast.makeText(this, "Session expired or invalid. Please login again.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (binding.progressBarUpload.getVisibility() == View.VISIBLE) {
                // TODO: Show confirmation dialog to cancel upload if in progress
                Log.w(TAG, "Back pressed during upload, consider adding cancel confirmation.");
            }
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}