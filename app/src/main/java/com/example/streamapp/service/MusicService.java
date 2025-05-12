// File: src/main/java/com/example/streamapp/service/MusicService.java
package com.example.streamapp.service; // Hoặc package của bạn

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat; // Cần thêm dependency
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat; // Cho việc hiển thị notification

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.streamapp.R;
import com.example.streamapp.activity.MusicPlayerActivity; // Để mở lại khi nhấn notification
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector; // Cần thêm dependency
import com.google.android.exoplayer2.ui.PlayerNotificationManager; // Cần thêm dependency
import androidx.localbroadcastmanager.content.LocalBroadcastManager; // Thêm import
import com.example.streamapp.utils.AppConstants; // Thêm import
import android.content.BroadcastReceiver; // Thêm import
import android.content.IntentFilter;   // Thêm import

public class MusicService extends Service {

    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "music_playback_channel";
    private static final int NOTIFICATION_ID = 101;

    private ExoPlayer exoPlayer;
    private PlayerNotificationManager playerNotificationManager;
    private MediaSessionCompat mediaSession;
    private MediaSessionConnector mediaSessionConnector;
    private BroadcastReceiver playbackStartedReceiver;

    private final IBinder binder = new MusicBinder();

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
        public ExoPlayer getPlayerInstance() { return exoPlayer; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializePlayerAndMediaSession();
        createNotificationChannel();
        registerPlaybackStartedReceiver(); // Đăng ký receiver
        Log.d(TAG, "MusicService onCreate");
    }

    private void registerPlaybackStartedReceiver() {
        playbackStartedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (AppConstants.ACTION_MEDIA_PLAYBACK_STARTED.equals(intent.getAction())) {
                    String mediaTypeStarted = intent.getStringExtra(AppConstants.EXTRA_MEDIA_TYPE_STARTED);
                    if ("VIDEO".equals(mediaTypeStarted)) {
                        Log.d(TAG, "MusicService: Received VIDEO playback start. Stopping music.");
                        if (exoPlayer != null && exoPlayer.isPlaying()) {
                            exoPlayer.pause(); // Hoặc exoPlayer.stop();
                            // Cập nhật notification nếu cần
                        }
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(AppConstants.ACTION_MEDIA_PLAYBACK_STARTED);
        LocalBroadcastManager.getInstance(this).registerReceiver(playbackStartedReceiver, filter);
    }
    private void initializePlayerAndMediaSession() {
        if (exoPlayer == null) {
            exoPlayer = new ExoPlayer.Builder(this).build();
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying) {
                        Log.d(TAG, "MusicService: Playback started.");
                        Intent intent = new Intent(AppConstants.ACTION_MEDIA_PLAYBACK_STARTED);
                        intent.putExtra(AppConstants.EXTRA_MEDIA_TYPE_STARTED, "MUSIC");
                        LocalBroadcastManager.getInstance(MusicService.this).sendBroadcast(intent);
                    }
                }
                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Log.e(TAG, "ExoPlayer error in Service: ", error);
                    // TODO: Xử lý lỗi, có thể dừng service hoặc thông báo cho UI
                    stopSelf(); // Dừng service nếu có lỗi nghiêm trọng
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_ENDED) {
                        Log.d(TAG, "Playback ended in service.");
                        // TODO: Xử lý khi kết thúc (next, stop, etc.)
                    }
                }
            });

            // Thiết lập MediaSession để tích hợp với hệ thống Android (vd: điều khiển từ màn hình khóa)
            mediaSession = new MediaSessionCompat(this, TAG);
            mediaSession.setActive(true);

            mediaSessionConnector = new MediaSessionConnector(mediaSession);
            mediaSessionConnector.setPlayer(exoPlayer);
        }
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.music_channel_name); // Thêm string này vào strings.xml
            String description = getString(R.string.music_channel_description); // Thêm string này
            int importance = NotificationManager.IMPORTANCE_LOW; // Hoặc DEFAULT nếu muốn âm thanh
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setSound(null, null); // Tắt âm thanh cho notification low importance
            channel.enableLights(false);
            channel.enableVibration(false);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created.");
            } else {
                Log.e(TAG, "NotificationManager is null, cannot create channel.");
            }
        }
    }

    private void setupPlayerNotificationManager(String title, String artist, String artworkUrl) {
        // PlayerNotificationManager để hiển thị thông báo media
        // Cần tạo một MediaDescriptionAdapter
        PlayerNotificationManager.MediaDescriptionAdapter descriptionAdapter = new PlayerNotificationManager.MediaDescriptionAdapter() {
            @Override
            public CharSequence getCurrentContentTitle(Player player) {
                return title != null ? title : "Unknown Title";
            }

            @Nullable
            @Override
            public PendingIntent createCurrentContentIntent(Player player) {
                // Intent để mở lại MusicPlayerActivity khi nhấn vào notification
                Intent intent = new Intent(MusicService.this, MusicPlayerActivity.class);
                // TODO: Truyền dữ liệu cần thiết (url, title, etc.) để MusicPlayerActivity có thể resume đúng trạng thái
                // intent.putExtra("MEDIA_URL", exoPlayer.getCurrentMediaItem() != null ? exoPlayer.getCurrentMediaItem().localConfiguration.uri.toString() : null);
                // intent.putExtra("MEDIA_TITLE", title);
                // intent.putExtra("MEDIA_ARTIST", artist);
                // intent.putExtra("ARTWORK_URL", artworkUrl);
                // intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP); // Để resume activity nếu đã mở

                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                return PendingIntent.getActivity(MusicService.this, 0, intent, flags);
            }

            @Nullable
            @Override
            public CharSequence getCurrentContentText(Player player) {
                return artist != null ? artist : "Unknown Artist";
            }

            @Nullable
            @Override
            public Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
                if (artworkUrl != null && !artworkUrl.isEmpty()) {
                    Glide.with(MusicService.this)
                            .asBitmap()
                            .load(artworkUrl)
                            .into(new CustomTarget<Bitmap>() {
                                @Override
                                public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                    callback.onBitmap(resource);
                                }
                                @Override
                                public void onLoadCleared(@Nullable Drawable placeholder) {
                                    // Có thể callback với ảnh placeholder nếu cần
                                }
                                @Override
                                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                    // callback.onBitmap(placeholderBitmap); // Nếu có ảnh placeholder dạng Bitmap
                                    super.onLoadFailed(errorDrawable);
                                }
                            });
                    return null; // Glide sẽ gọi callback bất đồng bộ
                }
                return null; // Hoặc trả về một Bitmap placeholder mặc định
            }
        };

        // Xây dựng PlayerNotificationManager
        if (playerNotificationManager != null) { // Release cái cũ nếu có
            playerNotificationManager.setPlayer(null);
        }

        PlayerNotificationManager.Builder builder =
                new PlayerNotificationManager.Builder(this, NOTIFICATION_ID, CHANNEL_ID)
                        .setMediaDescriptionAdapter(descriptionAdapter)
                        .setNotificationListener(new PlayerNotificationManager.NotificationListener() {
                            @Override
                            public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
                                if (ongoing) {
                                    startForeground(notificationId, notification);
                                    Log.d(TAG, "Service started in foreground.");
                                } else {
                                    stopForeground(false); // false để không xóa notification ngay
                                    Log.d(TAG, "Service stopped foreground, notification can be dismissed.");
                                }
                            }

                            @Override
                            public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
                                Log.d(TAG, "Notification cancelled. Stopping service.");
                                stopSelf(); // Dừng service khi notification bị hủy
                            }
                        });
        // (Tùy chọn) Custom các action trên notification
        // builder.setPreviousActionIconResourceId(R.drawable.ic_previous);
        // builder.setNextActionIconResourceId(R.drawable.ic_next);
        // builder.setPlayActionIconResourceId(R.drawable.ic_play);
        // builder.setPauseActionIconResourceId(R.drawable.ic_pause);

        playerNotificationManager = builder.build();
        playerNotificationManager.setPlayer(exoPlayer);
        playerNotificationManager.setMediaSessionToken(mediaSession.getSessionToken());
        playerNotificationManager.setUseNextActionInCompactView(true);
        playerNotificationManager.setUsePreviousActionInCompactView(true);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "MusicService onStartCommand");
        if (intent != null) {
            String action = intent.getAction();
            String mediaUrl = intent.getStringExtra("MEDIA_URL");
            String title = intent.getStringExtra("MEDIA_TITLE");
            String artist = intent.getStringExtra("MEDIA_ARTIST");
            String artwork = intent.getStringExtra("ARTWORK_URL");

            if ("ACTION_PLAY".equals(action) && mediaUrl != null) {
                Log.d(TAG, "Action PLAY received. URL: " + mediaUrl);
                if (exoPlayer == null) {
                    initializePlayerAndMediaSession(); // Khởi tạo lại nếu service bị kill và restart
                }
                MediaItem mediaItem = MediaItem.fromUri(Uri.parse(mediaUrl));
                exoPlayer.setMediaItem(mediaItem);
                exoPlayer.prepare();
                exoPlayer.play();
                setupPlayerNotificationManager(title, artist, artwork); // Setup notification
            } else if ("ACTION_STOP_FOREGROUND".equals(action)) {
                Log.d(TAG, "Action STOP_FOREGROUND received.");
                stopForeground(true); // true để xóa notification
                stopSelf(); // Dừng service
            }
        }
        return START_NOT_STICKY; // Service không tự khởi động lại nếu bị kill
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "MusicService onBind");
        return binder; // Cho phép Activity bind tới Service để lấy instance ExoPlayer
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "MusicService onUnbind");
        // Không cần release player ở đây nếu muốn nhạc tiếp tục phát
        // Player sẽ được release trong onDestroy hoặc khi notification bị cancel
        return super.onUnbind(intent);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(playbackStartedReceiver);
        Log.d(TAG, "MusicService onDestroy");
        if (mediaSession != null) {
            mediaSession.release();
        }
        if (mediaSessionConnector != null) {
            mediaSessionConnector.setPlayer(null);
        }
        if (playerNotificationManager != null) {
            playerNotificationManager.setPlayer(null); // Sẽ tự động cancel notification
        }
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }
}