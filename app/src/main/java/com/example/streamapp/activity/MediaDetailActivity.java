package com.example.streamapp.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide; // Import Glide
import com.bumptech.glide.request.RequestOptions;
import com.example.streamapp.R; // Import R
import com.example.streamapp.databinding.ActivityMediaDetailBinding; // ViewBinding
import com.example.streamapp.model.MediaResponse;
import com.example.streamapp.model.MessageResponse; // Import nếu dùng trong handleApiError
import com.example.streamapp.network.ApiClient;
import com.example.streamapp.network.ApiService;
import com.example.streamapp.utils.SessionManager;
import com.google.gson.Gson; // Import Gson

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MediaDetailActivity extends AppCompatActivity {

    private ActivityMediaDetailBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private Long mediaId; // Lưu ID nhận từ Intent
    private MediaResponse currentMediaItem; // Lưu thông tin media sau khi fetch thành công
    private static final String TAG = "MediaDetailActivity";

    // Enum trạng thái UI
    private enum State { LOADING, CONTENT, ERROR }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMediaDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // --- Setup Toolbar ---
        setSupportActionBar(binding.toolbarDetail);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Hiển thị nút back
            getSupportActionBar().setTitle("Media Details"); // Tiêu đề mặc định
        }

        // --- Initialize ---
        apiService = ApiClient.getApiService(getApplicationContext());
        sessionManager = new SessionManager(getApplicationContext());

        // --- Lấy Media ID từ Intent ---
        mediaId = getIntent().getLongExtra("MEDIA_ID", -1L); // -1L là giá trị mặc định nếu không tìm thấy

        if (mediaId == -1L) {
            Log.e(TAG, "Media ID not passed correctly in Intent.");
            Toast.makeText(this, "Error: Could not load media details (Invalid ID).", Toast.LENGTH_LONG).show();
            finish(); // Đóng activity nếu không có ID hợp lệ
            return;
        }

        // --- Set Listeners ---
        binding.fabPlay.setOnClickListener(v -> playMedia());
        binding.btnRetryDetail.setOnClickListener(v -> fetchMediaDetails()); // Nút retry gọi lại fetch

        // --- Fetch Data ---
        fetchMediaDetails();
    }

    /**
     * Gọi API để lấy thông tin chi tiết của media item.
     */
    private void fetchMediaDetails() {
        String token = sessionManager.getToken();
        // API này yêu cầu token
        if (token == null || token.startsWith("dummy-test-token")) {
            handleApiError(null, "Please login to view details.", true);
            return;
        }

        Log.d(TAG, "Fetching media details for ID: " + mediaId);
        showState(State.LOADING); // Hiển thị loading

        // Gọi API getMediaDetails
        apiService.getMediaDetails("Bearer " + token, mediaId).enqueue(new Callback<MediaResponse>() {
            @Override
            public void onResponse(@NonNull Call<MediaResponse> call, @NonNull Response<MediaResponse> response) {
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "getMediaDetails successful.");
                    currentMediaItem = response.body(); // Lưu lại thông tin
                    populateUi(currentMediaItem); // Hiển thị dữ liệu
                    showState(State.CONTENT); // Hiển thị nội dung
                } else {
                    Log.e(TAG, "Failed to fetch media details - Code: " + response.code());
                    handleApiError(response, "Failed to load media details", false); // Không logout
                }
            }

            @Override
            public void onFailure(@NonNull Call<MediaResponse> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                Log.e(TAG, "API Call Failed (getMediaDetails): ", t);
                showState(State.ERROR, "Error loading details: " + t.getMessage());
            }
        });
    }

    /**
     * Hiển thị dữ liệu từ MediaResponse lên các View.
     * @param media Thông tin media item.
     */
    private void populateUi(MediaResponse media) {
        if (media == null) return; // Không làm gì nếu data null

        runOnUiThread(() -> {
            // Cập nhật tiêu đề Toolbar
            if (getSupportActionBar() != null && media.getTitle() != null) {
                getSupportActionBar().setTitle(media.getTitle());
            }

            // Hiển thị các thông tin text
            binding.tvDetailTitle.setText(media.getTitle());
            String type = media.getType() != null ? media.getType().toUpperCase() : "UNKNOWN";
            String visibility = media.isPublic() ? "Public" : "Private";
            String owner = media.getOwnerUsername() != null ? "by " + media.getOwnerUsername() : ""; // Hiển thị owner nếu có
            binding.tvDetailInfo.setText(String.format("%s - %s %s", type, visibility, owner).trim());

            if (!TextUtils.isEmpty(media.getDescription())) {
                binding.tvDetailDescription.setText(media.getDescription());
                binding.tvDetailDescription.setVisibility(View.VISIBLE);
            } else {
                binding.tvDetailDescription.setVisibility(View.GONE); // Ẩn nếu không có mô tả
            }

            // Load ảnh thumbnail (nếu có API trả về URL thumbnail)
            // Hiện tại dùng icon mặc định
            int iconResId;
            if ("MUSIC".equals(type)) iconResId = android.R.drawable.ic_media_play;
            else if ("VIDEO".equals(type)) iconResId = android.R.drawable.presence_video_online;
            else iconResId = R.drawable.ic_default_media; // Dùng placeholder của bạn
            binding.ivDetailThumbnail.setImageResource(iconResId);

            // TODO: Thay thế bằng Glide nếu có thumbnailUrl
            // String thumbnailUrl = media.getThumbnailUrl(); // Giả sử có getter này
            // Glide.with(this)
            //      .load(thumbnailUrl)
            //      .placeholder(iconResId)
            //      .error(iconResId)
            //      .into(binding.ivDetailThumbnail);

            // Hiện nút Play
            binding.fabPlay.setVisibility(View.VISIBLE);
        });
    }

    /**
     * Xử lý sự kiện nhấn nút Play.
     */
    private void playMedia() {
        if (currentMediaItem == null || TextUtils.isEmpty(currentMediaItem.getUrl())) {
            Toast.makeText(this, "Cannot play: Media information is incomplete.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Play button clicked. Opening PlayerActivity for URL: " + currentMediaItem.getUrl());
        Intent playerIntent = new Intent(this, PlayerActivity.class);
        playerIntent.putExtra("MEDIA_URL", currentMediaItem.getUrl());
        playerIntent.putExtra("MEDIA_TITLE", currentMediaItem.getTitle());
        try {
            startActivity(playerIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting PlayerActivity from Detail", e);
            Toast.makeText(this, "Could not open player.", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Cập nhật giao diện dựa trên trạng thái hiện tại.
     * @param state Trạng thái cần hiển thị.
     * @param message Thông báo tùy chọn cho trạng thái ERROR.
     */
    private void showState(State state, String message) {
        runOnUiThread(() -> {
            Log.d(TAG,"Changing detail UI state to: " + state + (message != null ? " ("+message+")" : ""));
            binding.progressBarDetail.setVisibility(state == State.LOADING ? View.VISIBLE : View.GONE);
            // Ẩn/hiện nội dung chính (ScrollView hoặc ConstraintLayout cha)
            binding.getRoot().findViewById(R.id.ivDetailThumbnail).setVisibility(state == State.CONTENT ? View.VISIBLE : View.INVISIBLE);
            binding.getRoot().findViewById(R.id.fabPlay).setVisibility(state == State.CONTENT ? View.VISIBLE : View.INVISIBLE);
            binding.getRoot().findViewById(R.id.tvDetailTitle).setVisibility(state == State.CONTENT ? View.VISIBLE : View.INVISIBLE);
            binding.getRoot().findViewById(R.id.tvDetailInfo).setVisibility(state == State.CONTENT ? View.VISIBLE : View.INVISIBLE);
            binding.getRoot().findViewById(R.id.tvDetailDescription).setVisibility(state == State.CONTENT && !TextUtils.isEmpty(binding.tvDetailDescription.getText()) ? View.VISIBLE : View.INVISIBLE);

            binding.tvErrorDetail.setVisibility(state == State.ERROR ? View.VISIBLE : View.GONE);
            binding.btnRetryDetail.setVisibility(state == State.ERROR ? View.VISIBLE : View.GONE);

            if (state == State.ERROR) {
                binding.tvErrorDetail.setText(message != null ? message : "An error occurred.");
            }
        });
    }
    // Overload
    private void showState(State state) {
        showState(state, null);
    }

    // Hàm xử lý lỗi API chung (tương tự MainActivity)
    private void handleApiError(Response<?> response, String defaultMessage, boolean logoutOnError) {
        // ... (Copy code handleApiError từ MainActivity và điều chỉnh nếu cần) ...
        if (isFinishing() || isDestroyed()) return;
        String errorMessage = defaultMessage;
        int code = response != null ? response.code() : -1;
        Log.e(TAG, defaultMessage + " - Code: " + code);

        if ((response == null || code == 401 || code == 403) && logoutOnError) {
            runOnUiThread(()->{
                Toast.makeText(this, "Session expired or invalid. Please login again.", Toast.LENGTH_LONG).show();
                // Quay về Login
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
            return;
        }

        String errorBodyContent = "";
        // ... (code đọc và parse error body như trong MainActivity.handleApiError) ...
        if (response != null && response.errorBody() != null) {
            try {
                errorBodyContent = response.errorBody().string();
                Log.e(TAG, "API Error Body Raw: " + errorBodyContent);
            } catch (Exception e) { Log.e(TAG, "Error reading error body", e); }
        }

        if (!errorBodyContent.isEmpty()) {
            try {
                MessageResponse errorMsg = new Gson().fromJson(errorBodyContent, MessageResponse.class);
                if (errorMsg != null && !TextUtils.isEmpty(errorMsg.getMessage())) {
                    errorMessage = errorMsg.getMessage();
                } else { errorMessage += " (Code: " + code + ")"; }
            } catch (Exception jsonError) {
                Log.w(TAG,"Could not parse error body as JSON: " + jsonError.getMessage());
                errorMessage += " (Code: " + code + ")";
            }
        } else if (response != null) { errorMessage += " (Code: " + code + ")"; }
        else { errorMessage += " (No response)"; }

        // Hiển thị trạng thái lỗi
        showState(State.ERROR, errorMessage);
    }


    // Xử lý nút back trên ActionBar
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Đóng Activity hiện tại
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}