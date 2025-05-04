package com.example.streamapp.activity; // Đảm bảo package này đúng

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri; // Giữ lại nếu cần trong tương lai, hiện không dùng trực tiếp
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.example.streamapp.R;
// Import các Activity khác
import com.example.streamapp.activity.LoginActivity;
import com.example.streamapp.activity.UploadActivity;
import com.example.streamapp.activity.PlayerActivity; // Vẫn cần nếu Player được gọi từ Detail
import com.example.streamapp.activity.ProfileActivity;
import com.example.streamapp.activity.MediaDetailActivity; // Import MediaDetailActivity

// Import Adapter, Binding, Models, Network, Utils
import com.example.streamapp.adapter.MediaAdapter; // Đảm bảo đã cập nhật Adapter
import com.example.streamapp.databinding.ActivityMainBinding;
import com.example.streamapp.model.MediaResponse;
import com.example.streamapp.model.MessageResponse;
import com.example.streamapp.network.ApiClient;
import com.example.streamapp.network.ApiService;
import com.example.streamapp.utils.SessionManager;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private MediaAdapter mediaAdapter;
    private String currentUsername; // Lưu username để kiểm tra quyền sở hữu
    private static final String TAG = "MainActivity";

    // Launcher để nhận kết quả từ UploadActivity
    private ActivityResultLauncher<Intent> uploadActivityResultLauncher;

    // Enum định nghĩa các trạng thái UI
    private enum StateType {
        LOADING, CONTENT, EMPTY, ERROR
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // --- Setup Toolbar ---
        setSupportActionBar(binding.toolbarMain);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Media Feed");
        }

        // --- Initialize ---
        apiService = ApiClient.getApiService(getApplicationContext());
        sessionManager = new SessionManager(getApplicationContext());

        // Lấy username hiện tại
        currentUsername = getCurrentUsernameFromTokenOrPrefs();

        // --- Setup ActivityResultLauncher ---
        setupUploadLauncher();

        // --- Setup RecyclerView ---
        setupRecyclerView(); // Đã bao gồm việc set listener

        // --- Gắn listener cho nút Retry ---
        binding.btnRetry.setOnClickListener(v -> {
            Log.d(TAG, "Retry button clicked.");
            fetchData(); // Gọi lại hàm fetch data khi nhấn retry
        });

        // --- Fetch Media Data lần đầu ---
        fetchData();
    }

    /**
     * Lấy username từ token đã lưu.
     * TODO: Nên thay thế bằng cách lấy từ SharedPreferences sau khi lưu lúc login.
     * @return Username hoặc null nếu không lấy được.
     */
    private String getCurrentUsernameFromTokenOrPrefs() {
        String token = sessionManager.getToken();
        if (token != null && !token.startsWith("dummy-test-token")) {
            try {
                String[] chunks = token.split("\\.");
                if (chunks.length >= 2) {
                    byte[] data = android.util.Base64.decode(chunks[1], android.util.Base64.URL_SAFE);
                    String decodedPayload = new String(data, "UTF-8");
                    com.google.gson.JsonObject payloadJson = new Gson().fromJson(decodedPayload, com.google.gson.JsonObject.class);
                    if (payloadJson != null && payloadJson.has("sub")) {
                        String username = payloadJson.get("sub").getAsString();
                        Log.d(TAG, "Username retrieved from token: " + username);
                        return username;
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Could not get username from token", e); }
        } else if (token != null && token.startsWith("dummy-test-token")) {
            Log.i(TAG, "Using dummy token, returning 'admin' for testing.");
            return "admin";
        }
        Log.w(TAG, "Could not determine current username.");
        return null;
    }

    /**
     * Khởi tạo ActivityResultLauncher cho UploadActivity.
     */
    private void setupUploadLauncher() {
        uploadActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Log.d(TAG, "UploadActivity returned RESULT_OK, refreshing media list.");
                        Toast.makeText(this, "Upload successful! Refreshing feed...", Toast.LENGTH_SHORT).show();
                        fetchData(); // Tải lại dữ liệu
                    } else {
                        Log.d(TAG, "UploadActivity returned code: " + result.getResultCode());
                    }
                });
    }

    /**
     * Khởi tạo RecyclerView và MediaAdapter, gán các listeners.
     */
    private void setupRecyclerView() {
        // Khởi tạo adapter (dùng constructor mặc định)
        mediaAdapter = new MediaAdapter();

        // Set listener cho click vào item -> mở MediaDetailActivity
        mediaAdapter.setOnItemClickListener(mediaItem -> {
            Log.d(TAG, "onItemClick triggered for: " + mediaItem.getTitle() + " with ID: " + mediaItem.getId());
            if (mediaItem.getId() == null) {
                Toast.makeText(MainActivity.this, "Cannot view details: Invalid Media ID.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent detailIntent = new Intent(MainActivity.this, MediaDetailActivity.class);
            detailIntent.putExtra("MEDIA_ID", mediaItem.getId()); // Truyền ID
            try {
                startActivity(detailIntent);
            } catch (Exception e) {
                Log.e(TAG, "Error starting MediaDetailActivity", e);
                Toast.makeText(this, "Could not open details.", Toast.LENGTH_SHORT).show();
            }
        });

        // Set listener cho click vào nút xóa -> hiển thị dialog xác nhận
        mediaAdapter.setOnItemDeleteListener((mediaItem, position) -> {
            Log.d(TAG, "onDeleteClick triggered for item: " + mediaItem.getTitle() + " at position: " + position);
            // Kiểm tra quyền sở hữu
            if (currentUsername != null && currentUsername.equals(mediaItem.getOwnerUsername())) {
                showDeleteConfirmationDialog(mediaItem, position);
            } else {
                Log.w(TAG, "Delete clicked, but user '" + currentUsername + "' is not the owner '" + mediaItem.getOwnerUsername() + "'");
                Toast.makeText(this, "You can only delete your own media.", Toast.LENGTH_SHORT).show();
            }
        });

        // Set username cho adapter
        mediaAdapter.setCurrentUsername(currentUsername);

        // Gán LayoutManager và Adapter
        binding.recyclerViewMedia.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewMedia.setAdapter(mediaAdapter);
    }

    /**
     * Hiển thị hộp thoại xác nhận xóa media.
     */
    private void showDeleteConfirmationDialog(MediaResponse mediaItem, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Media")
                .setMessage("Are you sure you want to delete '" + mediaItem.getTitle() + "'?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                    Log.d(TAG, "User confirmed deletion for media ID: " + mediaItem.getId());
                    deleteMediaItem(mediaItem.getId(), position);
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    /**
     * Gọi API để xóa media item.
     */
    private void deleteMediaItem(Long mediaId, int position) {
        String token = sessionManager.getToken();
        if (mediaId == null || token == null || token.startsWith("dummy-test-token")) {
            handleApiError(null, "Cannot delete media. Invalid session or media ID.", true);
            return;
        }

        Log.d(TAG, "Attempting to delete media with ID: " + mediaId + " at position: " + position);
        showState(StateType.LOADING); // Hiển thị loading khi đang xóa

        apiService.deleteMedia("Bearer " + token, mediaId).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(@NonNull Call<MessageResponse> call, @NonNull Response<MessageResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(MainActivity.this, response.body().getMessage(), Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Media deleted via API. Removing item from UI at position: " + position);
                    mediaAdapter.removeItem(position); // Cập nhật UI
                    // Cập nhật trạng thái màn hình sau khi xóa
                    if(mediaAdapter.getItemCount() == 0) {
                        showState(StateType.EMPTY, "No media found.");
                    } else {
                        showState(StateType.CONTENT);
                    }
                } else {
                    Log.e(TAG, "Failed to delete media via API.");
                    handleApiError(response, "Failed to delete media", false);
                    // Quay lại trạng thái CONTENT nếu vẫn còn item
                    if(mediaAdapter.getItemCount() > 0) {
                        showState(StateType.CONTENT);
                    } else {
                        showState(StateType.ERROR, "Failed to delete media."); // Hoặc EMPTY
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<MessageResponse> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                Log.e(TAG, "API Call Failed (deleteMedia): ", t);
                showState(StateType.ERROR, "Error deleting media: " + t.getMessage());
            }
        });
    }

    /**
     * Hàm fetch data chung, quyết định API nào cần gọi và cập nhật username cho adapter.
     */
    private void fetchData() {
        Log.d(TAG, "fetchData called. Current username: " + currentUsername);
        currentUsername = getCurrentUsernameFromTokenOrPrefs(); // Luôn lấy username mới nhất
        if (mediaAdapter != null) {
            mediaAdapter.setCurrentUsername(currentUsername);
        }

        showState(StateType.LOADING); // Hiển thị loading

        if (currentUsername != null && sessionManager.getToken() != null && !sessionManager.getToken().startsWith("dummy-test-token")) {
            fetchUserMedia();
        } else {
            fetchPublicMedia();
        }
    }

    /**
     * Tải danh sách media của người dùng hiện tại.
     */
    private void fetchUserMedia() {
        Log.d(TAG, "Fetching user media...");
        String token = sessionManager.getToken();
        // Không cần kiểm tra token lại

        apiService.getUserMedia("Bearer " + token).enqueue(new Callback<List<MediaResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<MediaResponse>> call, @NonNull Response<List<MediaResponse>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "getUserMedia successful, " + response.body().size() + " items received.");
                    List<MediaResponse> mediaList = response.body();
                    mediaAdapter.setData(mediaList); // Cập nhật adapter bằng DiffUtil
                    showState(mediaList.isEmpty() ? StateType.EMPTY : StateType.CONTENT,
                            mediaList.isEmpty() ? "You haven't uploaded any media yet." : null);
                } else {
                    handleApiError(response, "Failed to fetch user media", true); // Logout nếu lỗi
                }
            }
            @Override
            public void onFailure(@NonNull Call<List<MediaResponse>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                Log.e(TAG, "API Call Failed (getUserMedia): ", t);
                showState(StateType.ERROR, "Error fetching your media: " + t.getMessage());
            }
        });
    }

    /**
     * Tải danh sách media public.
     */
    private void fetchPublicMedia() {
        Log.d(TAG, "Fetching public media...");
        apiService.getPublicMedia().enqueue(new Callback<List<MediaResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<MediaResponse>> call, @NonNull Response<List<MediaResponse>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "getPublicMedia successful, " + response.body().size() + " items received.");
                    List<MediaResponse> mediaList = response.body();
                    mediaAdapter.setData(mediaList); // Cập nhật adapter bằng DiffUtil
                    showState(mediaList.isEmpty() ? StateType.EMPTY : StateType.CONTENT,
                            mediaList.isEmpty() ? "No public media found." : null);
                } else {
                    handleApiError(response, "Failed to fetch public media", false); // Không logout
                }
            }
            @Override
            public void onFailure(@NonNull Call<List<MediaResponse>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                Log.e(TAG, "API Call Failed (getPublicMedia): ", t);
                showState(StateType.ERROR, "Error fetching public media: " + t.getMessage());
            }
        });
    }

    /**
     * Xử lý lỗi API chung.
     */
    private void handleApiError(Response<?> response, String defaultMessage) {
        handleApiError(response, defaultMessage, true);
    }

    private void handleApiError(Response<?> response, String defaultMessage, boolean logoutOnError) {
        if (isFinishing() || isDestroyed()) return;

        String errorMessage = defaultMessage;
        int code = response != null ? response.code() : -1;
        Log.e(TAG, defaultMessage + " - Code: " + code);

        if ((response == null || code == 401 || code == 403) && logoutOnError) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Session expired or invalid. Please login again.", Toast.LENGTH_LONG).show();
                logoutUser();
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
            } catch (Exception jsonError) {
                Log.w(TAG,"Could not parse error body as JSON: " + jsonError.getMessage());
                errorMessage += " (Code: " + code + ")";
            }
        } else if (response != null) { errorMessage += " (Code: " + code + ")"; }
        else { errorMessage += " (Network error or no response)"; }

        // Hiển thị trạng thái lỗi
        showState(StateType.ERROR, errorMessage);
    }

    /**
     * Cập nhật UI theo trạng thái.
     */
    private void showState(StateType state, String message) {
        runOnUiThread(() -> {
            Log.d(TAG,"Changing UI state to: " + state + (message != null ? " ("+message+")" : ""));
            binding.progressBarMain.setVisibility(state == StateType.LOADING ? View.VISIBLE : View.GONE);
            binding.recyclerViewMedia.setVisibility(state == StateType.CONTENT ? View.VISIBLE : View.GONE);
            binding.tvEmptyState.setVisibility(state == StateType.EMPTY || state == StateType.ERROR ? View.VISIBLE : View.GONE);
            binding.btnRetry.setVisibility(state == StateType.ERROR ? View.VISIBLE : View.GONE);

            if (state == StateType.EMPTY || state == StateType.ERROR) {
                binding.tvEmptyState.setText(message != null ? message : "An error occurred.");
            }
        });
    }
    // Overload
    private void showState(StateType state) {
        showState(state, null);
    }

    // --- Options Menu ---
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_logout) { logoutUser(); return true; }
        else if (id == R.id.action_upload) {
            Log.d(TAG, "Upload menu item clicked.");
            Intent uploadIntent = new Intent(MainActivity.this, UploadActivity.class);
            uploadActivityResultLauncher.launch(uploadIntent);
            return true;
        } else if (id == R.id.action_refresh) {
            Log.d(TAG, "Refresh menu item clicked.");
            Toast.makeText(this, "Refreshing feed...", Toast.LENGTH_SHORT).show();
            fetchData();
            return true;
        } else if (id == R.id.action_profile) {
            Log.d(TAG, "Profile menu item clicked.");
            Intent profileIntent = new Intent(MainActivity.this, ProfileActivity.class);
            startActivity(profileIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Đăng xuất người dùng.
     */
    private void logoutUser() {
        Log.d(TAG, "Logging out user...");
        sessionManager.clearToken();
        currentUsername = null; // Reset username
        // TODO: Xóa SharedPreferences khác nếu cần

        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}