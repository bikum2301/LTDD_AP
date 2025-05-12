// File: src/main/java/com/example/streamapp/activity/PlayerActivity.java
package com.example.streamapp.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
// import android.widget.Toast; // Có thể không cần Toast nữa

import com.example.streamapp.R; // Import R
import com.example.streamapp.databinding.ActivityPlayerBinding;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
// import com.google.android.exoplayer2.source.MediaSource; // Không dùng trực tiếp
// import com.google.android.exoplayer2.source.ProgressiveMediaSource; // Không dùng trực tiếp
// import com.google.android.exoplayer2.upstream.DefaultDataSource; // Không dùng trực tiếp
import com.google.android.exoplayer2.util.Util;

public class PlayerActivity extends AppCompatActivity {

    private ActivityPlayerBinding binding;
    private ExoPlayer player;

    private String mediaUrl;
    private String mediaTitle;

    private static final String TAG = "PlayerActivity";

    private long playbackPosition = 0;
    private int currentWindow = 0;
    private boolean playWhenReady = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mediaUrl = getIntent().getStringExtra("MEDIA_URL");
        mediaTitle = getIntent().getStringExtra("MEDIA_TITLE");

        if (mediaUrl == null || mediaUrl.isEmpty()) {
            Log.e(TAG, "Media URL is missing!");
            showErrorState("Error: Media URL not found.", false); // Hiển thị lỗi, không cho retry nếu URL lỗi
            return; // Không khởi tạo player
        }

        if (mediaTitle != null) {
            binding.tvPlayerTitle.setText(mediaTitle);
            binding.tvPlayerTitle.setVisibility(View.VISIBLE);
        } else {
            binding.tvPlayerTitle.setVisibility(View.GONE);
        }

        if (savedInstanceState != null) {
            playbackPosition = savedInstanceState.getLong("playbackPosition", 0L);
            currentWindow = savedInstanceState.getInt("currentWindow", 0);
            playWhenReady = savedInstanceState.getBoolean("playWhenReady", true);
        }

        // Nút Retry
        binding.layoutPlayerError.findViewById(R.id.btnPlayerRetry).setOnClickListener(v -> {
            hideErrorState();
            releasePlayer(); // Release player cũ
            initializePlayer(); // Thử khởi tạo lại
        });

        hideSystemUi();
        // initializePlayer() sẽ được gọi trong onStart/onResume
    }

    private void initializePlayer() {
        if (mediaUrl == null || mediaUrl.isEmpty()) { // Kiểm tra lại URL
            showErrorState("Cannot initialize player: Media URL is missing.", false);
            return;
        }
        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            binding.playerView.setPlayer(player);

            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(mediaUrl));
            player.setMediaItem(mediaItem);

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    String stateString;
                    if (playbackState == Player.STATE_BUFFERING) {
                        stateString = "ExoPlayer.STATE_BUFFERING";
                        // binding.progressBarPlayer.setVisibility(View.VISIBLE); // Nếu có progress bar riêng
                    } else if (playbackState == Player.STATE_READY) {
                        stateString = "ExoPlayer.STATE_READY";
                        // binding.progressBarPlayer.setVisibility(View.GONE);
                        hideErrorState(); // Ẩn thông báo lỗi nếu trước đó có lỗi và giờ đã ready
                    } else if (playbackState == Player.STATE_ENDED) {
                        stateString = "ExoPlayer.STATE_ENDED";
                        // Xử lý khi phát xong
                    } else if (playbackState == Player.STATE_IDLE) {
                        stateString = "ExoPlayer.STATE_IDLE";
                        // binding.progressBarPlayer.setVisibility(View.GONE);
                    } else {
                        stateString = "UNKNOWN_STATE";
                    }
                    Log.d(TAG, "Player changed state to " + stateString);
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Log.e(TAG, "ExoPlayer Error: ", error);
                    String errorMessage = "Could not play media. Please try again later."; // Mặc định
                    String errorCodeNameString = PlaybackException.getErrorCodeName(error.errorCode);

                    if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) {
                        errorMessage = "Network error. Please check your connection and try again.";
                    } else if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                            error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
                        errorMessage = "Could not load media. The content might be unavailable. (Error: " + errorCodeNameString + ")";
                    } else if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                            (errorCodeNameString != null && errorCodeNameString.contains("UnrecognizedInputFormatException"))) {
                        errorMessage = "Media format not supported or stream is invalid.";
                    } else if (errorCodeNameString != null) {
                        errorMessage = "Playback error (" + errorCodeNameString + "). Please try again.";
                    }

                    Log.e(TAG, "Detailed ExoPlayer Error: Code=" + error.errorCode + ", Name=" + errorCodeNameString + ", Message=" + error.getLocalizedMessage());
                    showErrorState(errorMessage, true); // Hiển thị lỗi và cho phép retry
                }
            });

            player.setPlayWhenReady(playWhenReady);
            player.seekTo(currentWindow, playbackPosition);
            player.prepare();
            Log.d(TAG, "Player initialized and preparing.");
        }
    }

    private void showErrorState(String message, boolean showRetryButton) {
        binding.playerView.setVisibility(View.GONE); // Ẩn PlayerView
        binding.tvPlayerTitle.setVisibility(View.GONE); // Ẩn tiêu đề
        binding.layoutPlayerError.setVisibility(View.VISIBLE);
        TextView tvErrorMessage = binding.layoutPlayerError.findViewById(R.id.tvPlayerErrorMessage);
        Button btnRetry = binding.layoutPlayerError.findViewById(R.id.btnPlayerRetry);
        tvErrorMessage.setText(message);
        btnRetry.setVisibility(showRetryButton ? View.VISIBLE : View.GONE);
    }

    private void hideErrorState() {
        binding.layoutPlayerError.setVisibility(View.GONE);
        binding.playerView.setVisibility(View.VISIBLE);
        if (mediaTitle != null && !mediaTitle.isEmpty()) { // Chỉ hiện title nếu có
            binding.tvPlayerTitle.setVisibility(View.VISIBLE);
        }
    }


    private void releasePlayer() {
        if (player != null) {
            playbackPosition = player.getCurrentPosition();
            currentWindow = player.getCurrentWindowIndex();
            playWhenReady = player.getPlayWhenReady();
            player.release();
            player = null;
            Log.d(TAG, "Player released.");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Util.SDK_INT >= 24) {
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if ((Util.SDK_INT < 24 || player == null)) {
            initializePlayer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Util.SDK_INT < 24) {
            releasePlayer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Util.SDK_INT >= 24) {
            releasePlayer();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (player != null) {
            outState.putLong("playbackPosition", player.getCurrentPosition());
            outState.putInt("currentWindow", player.getCurrentWindowIndex());
            outState.putBoolean("playWhenReady", player.getPlayWhenReady());
        } else {
            outState.putLong("playbackPosition", playbackPosition);
            outState.putInt("currentWindow", currentWindow);
            outState.putBoolean("playWhenReady", playWhenReady);
        }
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            if (getWindow().getInsetsController() != null) {
                getWindow().getInsetsController().hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                getWindow().getInsetsController().setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            binding.playerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }
}