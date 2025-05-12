// File: src/main/java/com/example/streamapp/activity/MediaDetailActivity.java
package com.example.streamapp.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
// import android.net.Uri; // Không dùng trực tiếp
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.streamapp.R;
import com.example.streamapp.databinding.ActivityMediaDetailBinding;
import com.example.streamapp.model.MediaResponse;
import com.example.streamapp.model.MessageResponse;
import com.example.streamapp.model.ErrorResponse; // << THÊM IMPORT NÀY
import com.example.streamapp.network.ApiClient;
import com.example.streamapp.network.ApiService;
import com.example.streamapp.utils.SessionManager;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MediaDetailActivity extends AppCompatActivity {

    private ActivityMediaDetailBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private Long mediaId;
    private MediaResponse currentMediaItem;
    private static final String TAG = "MediaDetailActivity";

    private enum State { LOADING, CONTENT, ERROR }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMediaDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarDetail);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            // Tiêu đề sẽ được set trong populateUi sau khi fetch data
        }

        apiService = ApiClient.getApiService(getApplicationContext());
        sessionManager = new SessionManager(getApplicationContext());

        mediaId = getIntent().getLongExtra("MEDIA_ID", -1L);

        if (mediaId == -1L) {
            Log.e(TAG, "Media ID not passed correctly in Intent.");
            Toast.makeText(this, "Error: Could not load media details (Invalid ID).", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        binding.fabPlay.setOnClickListener(v -> playMedia());
        binding.btnRetryDetail.setOnClickListener(v -> fetchMediaDetails());

        fetchMediaDetails();
    }

    private void fetchMediaDetails() {
        String token = sessionManager.getToken();
        // API getMediaDetails có thể không yêu cầu token nếu media là public.
        // Tuy nhiên, nếu nó yêu cầu token để theo dõi user hoặc cho media private,
        // AuthInterceptor sẽ thêm nó. Ta chỉ cần đảm bảo user đã login nếu cần.
        // Hiện tại, ApiService.getMediaDetails(mediaId) không yêu cầu token ở client-side call.

        Log.d(TAG, "Fetching media details for ID: " + mediaId);
        showState(State.LOADING);

        // SỬA Ở ĐÂY: Bỏ token khỏi lời gọi API
        apiService.getMediaDetails(mediaId).enqueue(new Callback<MediaResponse>() {
            @Override
            public void onResponse(@NonNull Call<MediaResponse> call, @NonNull Response<MediaResponse> response) {
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "getMediaDetails successful.");
                    currentMediaItem = response.body();
                    populateUi(currentMediaItem);
                    showState(State.CONTENT);
                } else {
                    Log.e(TAG, "Failed to fetch media details - Code: " + response.code());
                    handleApiError(response, "Failed to load media details", false); // Không logout nếu chỉ là lỗi lấy chi tiết
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

    private void populateUi(MediaResponse media) {
        if (media == null) {
            Log.e(TAG, "populateUi called with null media data.");
            showState(State.ERROR, "Media information is unavailable.");
            return;
        }

        runOnUiThread(() -> {
            // Toolbar Title
            if (getSupportActionBar() != null && !TextUtils.isEmpty(media.getTitle())) {
                getSupportActionBar().setTitle(media.getTitle());
            } else if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Media Details"); // Fallback
            }

            // Thumbnail
            RequestOptions thumbnailOptions = new RequestOptions()
                    .placeholder(R.color.placeholder_color) // Hoặc R.drawable.ic_default_media
                    .error(R.drawable.ic_default_media)
                    .centerCrop();
            Glide.with(this)
                    .load(media.getThumbnailUrl()) // Dùng thumbnailUrl từ MediaResponse
                    .apply(thumbnailOptions)
                    .into(binding.ivDetailThumbnail);

            // Title
            binding.tvDetailTitle.setText(media.getTitle());

            // Channel/Artist Info
            RequestOptions avatarOptions = new RequestOptions()
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .circleCrop();
            Glide.with(this)
                    .load(media.getChannelAvatarUrl())
                    .apply(avatarOptions)
                    .into(binding.ivDetailChannelAvatar);

            binding.tvDetailChannelName.setText(media.getChannelName() != null ? media.getChannelName() : "Unknown Channel/Artist");
            if (!TextUtils.isEmpty(media.getOwnerUsername())) {
                binding.tvDetailOwnerUsername.setText("by " + media.getOwnerUsername());
                binding.tvDetailOwnerUsername.setVisibility(View.VISIBLE);
            } else {
                binding.tvDetailOwnerUsername.setVisibility(View.GONE);
            }

            // Stats (Views, Upload Date, Duration)
            StringBuilder statsBuilder = new StringBuilder();
            if (media.getViewCount() >= 0) {
                statsBuilder.append(formatViewCount(media.getViewCount())).append(" views");
            }
            if (!TextUtils.isEmpty(media.getUploadDate())) {
                if (statsBuilder.length() > 0) statsBuilder.append(" • ");
                statsBuilder.append(media.getUploadDate());
            }
            if (!TextUtils.isEmpty(media.getDuration()) && !"00:00".equals(media.getDuration())) {
                if (statsBuilder.length() > 0) statsBuilder.append(" • ");
                statsBuilder.append(media.getDuration());
            }
            binding.tvDetailStats.setText(statsBuilder.toString());
            binding.tvDetailStats.setVisibility(statsBuilder.length() > 0 ? View.VISIBLE : View.GONE);


            // Album (for Music)
            if ("MUSIC".equalsIgnoreCase(media.getType()) && !TextUtils.isEmpty(media.getAlbum())) {
                binding.tvDetailAlbum.setText("Album: " + media.getAlbum());
                binding.tvDetailAlbum.setVisibility(View.VISIBLE);
            } else {
                binding.tvDetailAlbum.setVisibility(View.GONE);
            }

            // Visibility
            binding.tvDetailVisibility.setText(media.isPublic() ? "Public" : "Private");

            // Description
            if (!TextUtils.isEmpty(media.getDescription())) {
                binding.tvDetailDescription.setText(media.getDescription());
                binding.tvDetailDescriptionLabel.setVisibility(View.VISIBLE);
                binding.tvDetailDescription.setVisibility(View.VISIBLE);
            } else {
                binding.tvDetailDescription.setText("No description available.");
                // binding.tvDetailDescriptionLabel.setVisibility(View.GONE); // Tùy chọn
                // binding.tvDetailDescription.setVisibility(View.GONE);
            }

            // FAB Play
            binding.fabPlay.setVisibility(View.VISIBLE);
        });
    }
    private String formatViewCount(long count) {
        if (count < 1000) return String.valueOf(count);
        int exp = (int) (Math.log(count) / Math.log(1000));
        return String.format(Locale.US, "%.1f%c", count / Math.pow(1000, exp), "KMBTPE".charAt(exp - 1));
    }

    private void playMedia() {
        if (currentMediaItem == null || TextUtils.isEmpty(currentMediaItem.getUrl())) {
            Toast.makeText(this, "Cannot play: Media URL is missing or invalid.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Attempted to play media but URL is missing or item is null.");
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

    private void showState(State state, String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || binding == null) { // Thêm kiểm tra binding
                return;
            }
            Log.d(TAG, "Changing detail UI state to: " + state + (message != null ? " (" + message + ")" : ""));

            // Hiển thị/Ẩn ProgressBar
            binding.progressBarDetail.setVisibility(state == State.LOADING ? View.VISIBLE : View.GONE);

            // Hiển thị/Ẩn toàn bộ nội dung trong ScrollView
            // Giả sử ScrollView có ID là "scrollViewDetail" và ConstraintLayout bên trong là "contentLayoutDetail"
            // Bạn đã đặt ID cho ScrollView, nên có thể dùng binding.scrollViewDetail
            if (binding.scrollViewDetail != null) {
                binding.scrollViewDetail.setVisibility(state == State.CONTENT ? View.VISIBLE : View.GONE);
            } else {
                // Fallback nếu scrollViewDetail không có trong binding (dù nó nên có nếu ID đúng)
                // Hoặc bạn có thể ẩn/hiện contentLayoutDetail nếu nó có ID và là con trực tiếp
                // binding.contentLayoutDetail.setVisibility(state == State.CONTENT ? View.VISIBLE : View.GONE);
                // Hoặc ẩn/hiện từng view con một cách thủ công (ít ưu tiên hơn)
                Log.w(TAG, "scrollViewDetail is null in binding, attempting to set visibility for individual content views.");
                binding.ivDetailThumbnail.setVisibility(state == State.CONTENT ? View.VISIBLE : View.GONE);
                binding.tvDetailTitle.setVisibility(state == State.CONTENT ? View.VISIBLE : View.GONE);
                binding.ivDetailChannelAvatar.setVisibility(state == State.CONTENT ? View.VISIBLE : View.GONE);
                binding.llDetailChannelInfo.setVisibility(state == State.CONTENT ? View.VISIBLE : View.GONE);
                binding.tvDetailStats.setVisibility(state == State.CONTENT ? View.VISIBLE : View.GONE);
                binding.tvDetailAlbum.setVisibility(state == State.CONTENT && "MUSIC".equalsIgnoreCase(currentMediaItem != null ? currentMediaItem.getType() : "") ? View.VISIBLE : View.GONE);
                binding.tvDetailVisibility.setVisibility(state == State.CONTENT ? View.VISIBLE : View.GONE);
                binding.tvDetailDescriptionLabel.setVisibility(state == State.CONTENT ? View.VISIBLE : View.GONE);
                binding.tvDetailDescription.setVisibility(state == State.CONTENT ? View.VISIBLE : View.GONE);
            }

            // FAB Play chỉ hiển thị khi có nội dung và currentMediaItem không null
            binding.fabPlay.setVisibility(state == State.CONTENT && currentMediaItem != null ? View.VISIBLE : View.GONE);


            // Hiển thị/Ẩn thông báo lỗi và nút Retry
            binding.tvErrorDetail.setVisibility(state == State.ERROR ? View.VISIBLE : View.GONE);
            binding.btnRetryDetail.setVisibility(state == State.ERROR ? View.VISIBLE : View.GONE);

            if (state == State.ERROR) {
                binding.tvErrorDetail.setText(message != null ? message : "An error occurred loading details.");
            }
        });
    }

    private void showState(State state) {
        showState(state, null);
    }

    private void handleApiError(Response<?> response, String defaultMessage, boolean logoutOnError) {
        if (isFinishing() || isDestroyed()) return;
        String errorMessage = defaultMessage;
        int responseCode = -1;
        String errorBodyContent = null;

        if (response != null) {
            responseCode = response.code();
            if (response.errorBody() != null) {
                try { errorBodyContent = response.errorBody().string(); }
                catch (IOException e) { Log.e(TAG, "Error reading error body: ", e); }
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
                } else { errorMessage = defaultMessage + " (Code: " + responseCode + ")";}
            } catch (Exception e) {
                Log.w(TAG, "Could not parse error body as Backend ErrorResponse, trying MessageResponse. Error: " + e.getMessage());
                try {
                    MessageResponse msgResponse = new Gson().fromJson(errorBodyContent, MessageResponse.class);
                    if (msgResponse != null && !TextUtils.isEmpty(msgResponse.getMessage())) {
                        errorMessage = msgResponse.getMessage();
                    } else {errorMessage = defaultMessage + " (Code: " + responseCode + ")";}
                } catch (Exception e2) {
                    Log.w(TAG, "Could not parse error body as MessageResponse either. Error: " + e2.getMessage());
                    errorMessage = defaultMessage + " (Code: " + responseCode + ")";
                }
            }
        } else if (response != null) { errorMessage = defaultMessage + " (Code: " + responseCode + ")";
        } else {errorMessage = defaultMessage + " (No response from server)";}

        showState(State.ERROR, errorMessage);
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
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}