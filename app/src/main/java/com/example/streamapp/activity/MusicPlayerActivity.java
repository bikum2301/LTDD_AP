// File: src/main/java/com/example/streamapp/activity/MusicPlayerActivity.java
package com.example.streamapp.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
// ... (các import khác)
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.streamapp.R;
import com.example.streamapp.databinding.ActivityMusicPlayerBinding;
import com.example.streamapp.service.MusicService; // Import Service
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.MediaItem;

public class MusicPlayerActivity extends AppCompatActivity {

    private static final String TAG = "MusicPlayerActivity";
    private ActivityMusicPlayerBinding binding;

    // private ExoPlayer exoPlayer; // SẼ LẤY TỪ SERVICE
    private MusicService musicService;
    private boolean isServiceBound = false;
    private ExoPlayer playerFromService; // Để lưu trữ instance player từ service

    private String mediaUrl;
    private String mediaTitle;
    private String mediaArtist;
    private String artworkUrl;

    // Không cần lưu playbackPosition, currentWindow, playWhenReady ở đây nữa, service sẽ quản lý
    // private long playbackPosition = 0;
    // private int currentWindow = 0;
    // private boolean playWhenReady = true;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            playerFromService = binder.getPlayerInstance(); // Lấy player instance
            isServiceBound = true;
            Log.d(TAG, "MusicService bound.");

            // Sau khi service bound và có player instance, gắn vào UI controls
            if (playerFromService != null) {
                binding.exoPlayerControlsMusic.setPlayer(playerFromService);
                playerFromService.addListener(playerListenerForActivity);
                // Nếu service đang phát bài khác, cần cập nhật UI với thông tin bài hiện tại
                // Hoặc nếu đây là lần đầu mở, bắt đầu phát bài mới
                // Kiểm tra xem có phải là đang resume lại activity với bài đang phát không
                boolean shouldStartPlayback = true; // Mặc định là bắt đầu phát
                if (playerFromService.isPlaying() || playerFromService.getPlaybackState() != Player.STATE_IDLE) {
                    MediaItem currentItem = playerFromService.getCurrentMediaItem();
                    if (currentItem != null && currentItem.localConfiguration != null &&
                            mediaUrl.equals(currentItem.localConfiguration.uri.toString())) {
                        Log.d(TAG, "Resuming playback for already playing/paused track in service.");
                        shouldStartPlayback = false; // Không cần start lại nếu service đã có bài này
                    } else {
                        Log.d(TAG, "Service is playing a different track or idle. Starting new track.");
                        playerFromService.stop(); // Dừng bài cũ nếu có
                    }
                }

                if (shouldStartPlayback) {
                    startPlaybackInService();
                }
            } else {
                Log.e(TAG, "Player instance from service is null after binding.");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            playerFromService = null; // Clear player instance
            binding.exoPlayerControlsMusic.setPlayer(null); // Bỏ player khỏi UI
            Log.d(TAG, "MusicService unbound.");
            if (playerFromService != null) {
                playerFromService.removeListener(playerListenerForActivity); // << GỠ LISTENER
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMusicPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarMusicPlayer);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        mediaUrl = getIntent().getStringExtra("MEDIA_URL");
        mediaTitle = getIntent().getStringExtra("MEDIA_TITLE");
        mediaArtist = getIntent().getStringExtra("MEDIA_ARTIST");
        artworkUrl = getIntent().getStringExtra("ARTWORK_URL");

        if (TextUtils.isEmpty(mediaUrl)) {
            Log.e(TAG, "Media URL is missing!");
            showErrorState("Error: Music URL not found.", false);
            // Không bind service hoặc làm gì thêm nếu URL lỗi
            return;
        }

        updateUiStaticInfo(); // Cập nhật title, artist, artwork
        binding.layoutMusicPlayerError.findViewById(R.id.btnMusicPlayerRetry).setOnClickListener(v -> {
            hideErrorState();
            // Khi retry, yêu cầu service phát lại bài hát hiện tại
            // (nếu mediaUrl vẫn còn) hoặc thử bind lại service.
            // Cách đơn giản là thử startPlaybackInService lại.
            if (isServiceBound && musicService != null) {
                startPlaybackInService();
            } else {
                // Nếu service chưa bound, onStart sẽ cố gắng bind lại,
                // và onServiceConnected sẽ gọi startPlaybackInService.
                // Hoặc có thể gọi startService trực tiếp ở đây.
                Intent intent = new Intent(this, MusicService.class);
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
                Log.d(TAG, "Retry: Attempting to re-bind and play.");
            }
        });
        // Không khởi tạo player ở đây nữa
    }

    private void updateUiStaticInfo() {
        binding.tvMusicPlayerTitle.setText(mediaTitle != null ? mediaTitle : "Unknown Title");
        binding.tvMusicPlayerArtist.setText(mediaArtist != null ? mediaArtist : "Unknown Artist");
        RequestOptions artworkOptions = new RequestOptions()
                .placeholder(R.drawable.ic_default_music_artwork)
                .error(R.drawable.ic_default_music_artwork)
                .centerInside();
        Glide.with(this)
                .load(artworkUrl)
                .apply(artworkOptions)
                .into(binding.ivMusicPlayerArtwork);
    }

    private void startPlaybackInService() {
        hideErrorState();
        if (musicService != null && playerFromService != null && mediaUrl != null) {
            Log.d(TAG, "Instructing service to play: " + mediaTitle);
            // Thay vì gọi trực tiếp player, chúng ta sẽ gửi Intent đến Service
            // Hoặc nếu đã bind, gọi phương thức của service (an toàn hơn là thao tác trực tiếp player)
            // Hiện tại, onServiceConnected sẽ gọi startPlaybackInService nếu cần.
            // Và startService với ACTION_PLAY cũng là một cách.
            // Để đảm bảo service chạy và xử lý, dùng startService.
            Intent playIntent = new Intent(this, MusicService.class);
            playIntent.setAction("ACTION_PLAY");
            playIntent.putExtra("MEDIA_URL", mediaUrl);
            playIntent.putExtra("MEDIA_TITLE", mediaTitle);
            playIntent.putExtra("MEDIA_ARTIST", mediaArtist);
            playIntent.putExtra("ARTWORK_URL", artworkUrl);
            ContextCompat.startForegroundService(this, playIntent); // Dùng cho Android O+

            // Gắn player của service vào UI control sau khi service đã chuẩn bị
            binding.exoPlayerControlsMusic.setPlayer(playerFromService);

        } else {
            Log.e(TAG, "Cannot start playback: Service not bound, player is null, or mediaUrl is null.");
        }
    }


    // Bỏ các hàm initializePlayer và releasePlayer ở đây
    // private void initializePlayer() { ... }
    // private void releasePlayer() { ... }

    private Player.Listener playerListenerForActivity = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            // Cập nhật progressBarMusicPlayer dựa trên playbackState
            binding.progressBarMusicPlayer.setVisibility(
                    playbackState == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);

            if (playbackState == Player.STATE_READY) {
                hideErrorState(); // Ẩn lỗi nếu trước đó có
            }
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            Log.e(TAG, "ExoPlayer Error from Service for Activity UI: ", error);
            String errorMessage = "An error occurred during playback.";
            String errorCodeNameString = PlaybackException.getErrorCodeName(error.errorCode);

            if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    /* ... các mã lỗi khác ... */
                    (errorCodeNameString != null && errorCodeNameString.contains("UnrecognizedInputFormatException")) ) {
                errorMessage = "Music format not supported or stream is invalid.";
            }
            // ... (các trường hợp lỗi khác như trong PlayerActivity)
            showErrorState(errorMessage, true);
        }
    };
    private void showErrorState(String message, boolean showRetryButton) {
        // Ẩn các view chính
        binding.ivMusicPlayerArtwork.setVisibility(View.INVISIBLE); // Dùng INVISIBLE để giữ không gian
        binding.tvMusicPlayerTitle.setVisibility(View.INVISIBLE);
        binding.tvMusicPlayerArtist.setVisibility(View.INVISIBLE);
        binding.exoPlayerControlsMusic.setVisibility(View.GONE);
        binding.progressBarMusicPlayer.setVisibility(View.GONE);

        // Hiển thị layout lỗi
        binding.layoutMusicPlayerError.setVisibility(View.VISIBLE);
        TextView tvErrorMessage = binding.layoutMusicPlayerError.findViewById(R.id.tvMusicPlayerErrorMessage);
        Button btnRetry = binding.layoutMusicPlayerError.findViewById(R.id.btnMusicPlayerRetry);

        tvErrorMessage.setText(message);
        btnRetry.setVisibility(showRetryButton ? View.VISIBLE : View.GONE);
    }

    private void hideErrorState() {
        binding.layoutMusicPlayerError.setVisibility(View.GONE);
        // Hiện lại các view chính
        binding.ivMusicPlayerArtwork.setVisibility(View.VISIBLE);
        binding.tvMusicPlayerTitle.setVisibility(View.VISIBLE);
        binding.tvMusicPlayerArtist.setVisibility(View.VISIBLE);
        binding.exoPlayerControlsMusic.setVisibility(View.VISIBLE);
    }
    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isServiceBound) {
            if (playerFromService != null) {
                playerFromService.removeListener(playerListenerForActivity); // Gỡ listener khi unbind
            }
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    // onSaveInstanceState không cần quản lý trạng thái player nữa, service làm việc đó
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Không cần lưu trạng thái player ở đây
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Nếu bạn muốn nút back của hệ thống cũng dừng nhạc (tùy chọn)
    // @Override
    // public void onBackPressed() {
    //     super.onBackPressed();
    //     if (isServiceBound && musicService != null && playerFromService != null && playerFromService.isPlaying()) {
    //         // Gửi intent để dừng service và notification
    //         Intent stopIntent = new Intent(this, MusicService.class);
    //         stopIntent.setAction("ACTION_STOP_FOREGROUND");
    //         ContextCompat.startForegroundService(this, stopIntent);
    //     }
    // }
}