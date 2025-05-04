package com.example.streamapp.activity; // Hoặc package của bạn

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

// Import ExoPlayer
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory; // Có thể deprecated, xem thay thế
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.Util; // Có thể deprecated, xem thay thế

import com.example.streamapp.databinding.ActivityPlayerBinding; // ViewBinding

public class PlayerActivity extends AppCompatActivity {

    private ActivityPlayerBinding binding;
    private ExoPlayer player;

    private String mediaUrl;
    private String mediaTitle;
    // private String mediaType; // Có thể không cần thiết nếu chỉ dùng URL

    private static final String TAG = "PlayerActivity";

    // Lưu trạng thái player khi Activity bị pause
    private long playbackPosition = 0;
    private int currentWindow = 0;
    private boolean playWhenReady = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Lấy dữ liệu từ Intent
        mediaUrl = getIntent().getStringExtra("MEDIA_URL");
        mediaTitle = getIntent().getStringExtra("MEDIA_TITLE");
        // mediaType = getIntent().getStringExtra("MEDIA_TYPE");

        if (mediaUrl == null || mediaUrl.isEmpty()) {
            Log.e(TAG, "Media URL is missing!");
            Toast.makeText(this, "Error: Media URL not found.", Toast.LENGTH_LONG).show();
            finish(); // Đóng activity nếu không có URL
            return;
        }

        // Hiển thị tiêu đề (nếu có)
        if (mediaTitle != null) {
            binding.tvPlayerTitle.setText(mediaTitle);
            binding.tvPlayerTitle.setVisibility(View.VISIBLE);
        } else {
            binding.tvPlayerTitle.setVisibility(View.GONE);
        }

        // Khôi phục trạng thái nếu có savedInstanceState
        if (savedInstanceState != null) {
            playbackPosition = savedInstanceState.getLong("playbackPosition", 0L);
            currentWindow = savedInstanceState.getInt("currentWindow", 0);
            playWhenReady = savedInstanceState.getBoolean("playWhenReady", true);
        }

        // System UI Visibility (ẩn status bar, navigation bar)
        hideSystemUi();
    }

    // Khởi tạo Player
    private void initializePlayer() {
        if (player == null) {
            // 1. Tạo instance ExoPlayer
            player = new ExoPlayer.Builder(this).build();

            // 2. Gắn player vào PlayerView
            binding.playerView.setPlayer(player);

            // 3. Tạo MediaItem từ URL
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(mediaUrl));

            // 4. (Tùy chọn) Tạo MediaSource nếu cần tùy chỉnh (ví dụ: DataSourceFactory)
            // Sử dụng DefaultHttpDataSource cho URL HTTP/HTTPS
            // DataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
            // MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
            //         .createMediaSource(mediaItem);

            // 5. Set MediaItem (hoặc MediaSource) cho player
            player.setMediaItem(mediaItem);
            // player.setMediaSource(mediaSource);

            // 6. Thêm Listener để xử lý lỗi, thay đổi trạng thái
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    String stateString;
                    switch (playbackState) {
                        case ExoPlayer.STATE_IDLE:
                            stateString = "ExoPlayer.STATE_IDLE      -";
                            break;
                        case ExoPlayer.STATE_BUFFERING:
                            stateString = "ExoPlayer.STATE_BUFFERING -";
                            // Hiện/ẩn ProgressBar tùy chỉnh nếu cần
                            break;
                        case ExoPlayer.STATE_READY:
                            stateString = "ExoPlayer.STATE_READY     -";
                            // Ẩn ProgressBar tùy chỉnh
                            break;
                        case ExoPlayer.STATE_ENDED:
                            stateString = "ExoPlayer.STATE_ENDED     -";
                            // Xử lý khi phát xong (ví dụ: quay lại, phát bài tiếp)
                            break;
                        default:
                            stateString = "UNKNOWN_STATE             -";
                            break;
                    }
                    Log.d(TAG, "Changed state to " + stateString);
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Log.e(TAG, "ExoPlayer Error: ", error);
                    // Hiển thị thông báo lỗi thân thiện cho người dùng
                    String errorMessage = "An error occurred during playback.";
                    if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                            || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) {
                        errorMessage = "Network error. Please check your connection.";
                    } else if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                        // Lỗi HTTP (403 Forbidden do SAS hết hạn, 404 Not Found, etc.)
                        errorMessage = "Could not load media. Please try again later.";
                    } else if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                            || error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
                        errorMessage = "Media format not supported on this device.";
                    }
                    Toast.makeText(PlayerActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    // Có thể đóng activity hoặc cho phép thử lại
                    finish();
                }
            });

            // 7. Khôi phục trạng thái và chuẩn bị phát
            player.setPlayWhenReady(playWhenReady);
            player.seekTo(currentWindow, playbackPosition);
            player.prepare(); // Bắt đầu chuẩn bị media
            Log.d(TAG, "Player initialized and preparing.");
        }
    }

    // Giải phóng Player
    private void releasePlayer() {
        if (player != null) {
            // Lưu lại trạng thái trước khi giải phóng
            playbackPosition = player.getCurrentPosition();
            currentWindow = player.getCurrentWindowIndex();
            playWhenReady = player.getPlayWhenReady();

            player.release(); // Giải phóng tài nguyên
            player = null;
            Log.d(TAG, "Player released.");
        }
    }

    // --- Quản lý vòng đời Activity và Player ---

    @Override
    protected void onStart() {
        super.onStart();
        // Khởi tạo player trên Android API 24+ (trước đó là onResume)
        if (Util.SDK_INT >= 24) {
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi(); // Đảm bảo UI ẩn khi quay lại
        // Khởi tạo player trên Android API < 24
        if ((Util.SDK_INT < 24 || player == null)) {
            initializePlayer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Giải phóng player trên Android API < 24
        if (Util.SDK_INT < 24) {
            releasePlayer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Giải phóng player trên Android API 24+
        if (Util.SDK_INT >= 24) {
            releasePlayer();
        }
    }

    // Lưu trạng thái player khi Activity bị hủy (ví dụ xoay màn hình)
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (player != null) {
            outState.putLong("playbackPosition", player.getCurrentPosition());
            outState.putInt("currentWindow", player.getCurrentWindowIndex());
            outState.putBoolean("playWhenReady", player.getPlayWhenReady());
        } else {
            // Lưu trạng thái đã lưu trước đó nếu player đã release
            outState.putLong("playbackPosition", playbackPosition);
            outState.putInt("currentWindow", currentWindow);
            outState.putBoolean("playWhenReady", playWhenReady);
        }
    }

    // --- Helper ẩn System UI ---
    private void hideSystemUi() {
        // Sử dụng WindowInsetsController cho API 30+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            if (getWindow().getInsetsController() != null) {
                getWindow().getInsetsController().hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                getWindow().getInsetsController().setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // Cách cũ hơn cho API < 30
            binding.playerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }
}