// File: src/main/java/com/example/streamapp/activity/MainActivity.java
package com.example.streamapp.activity;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment; // Thêm import này
import androidx.viewpager2.widget.ViewPager2; // Thêm ViewPager2

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils; // Vẫn cần cho một số kiểm tra
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
// import android.view.View; // Có thể không cần View trực tiếp ở đây nữa
import android.widget.Toast;

import com.example.streamapp.R;
// Các import Activity khác vẫn giữ
import com.example.streamapp.activity.WelcomeActivity; // Import WelcomeActivity
import com.example.streamapp.activity.UploadActivity;
import com.example.streamapp.activity.ProfileActivity;
// Các import model và network không cần thiết ở đây nữa nếu logic fetch ở Fragment
// import com.example.streamapp.model.MediaResponse;
// import com.example.streamapp.model.MessageResponse;
// import com.example.streamapp.model.ErrorResponse;
// import com.example.streamapp.network.ApiClient;
// import com.example.streamapp.network.ApiService;
import com.example.streamapp.adapter.MediaFeedPagerAdapter; // Import PagerAdapter
import com.example.streamapp.databinding.ActivityMainBinding;
import com.example.streamapp.fragment.MusicFeedFragment; // Import Fragment
import com.example.streamapp.fragment.VideoFeedFragment; // Import Fragment
import com.example.streamapp.utils.SessionManager;
import com.google.android.material.tabs.TabLayout; // Thêm TabLayout
import com.google.android.material.tabs.TabLayoutMediator; // Thêm TabLayoutMediator
import com.google.gson.Gson; // Vẫn có thể cần cho một số tiện ích

import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding; // Binding này giờ trỏ tới activity_main.xml mới với ViewPager
    // private ApiService apiService; // Sẽ được dùng trong Fragment
    private SessionManager sessionManager;
    // private MediaAdapter mediaAdapter; // Sẽ được dùng trong Fragment
    private String currentUsername; // Vẫn cần để truyền cho Fragment nếu cần
    private static final String TAG = "MainActivity";

    private ActivityResultLauncher<Intent> uploadActivityResultLauncher;
    private MediaFeedPagerAdapter mediaFeedPagerAdapter;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;

    // Enum StateType không còn cần ở MainActivity nữa
    // private enum StateType { LOADING, CONTENT, EMPTY, ERROR }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbarMain); // Sử dụng toolbar từ binding
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("StreamApp Feed"); // Hoặc tên app của bạn
        }

        // apiService = ApiClient.getApiService(getApplicationContext()); // Khởi tạo trong Fragment khi cần
        sessionManager = new SessionManager(getApplicationContext());
        currentUsername = getCurrentUsernameFromTokenOrPrefs(); // Lấy username để có thể truyền cho Fragment

        setupUploadLauncher();
        setupViewPagerAndTabs(); // Setup ViewPager và TabLayout

        // Logic của binding.btnRetry sẽ được xử lý trong Fragment
        // binding.btnRetry.setOnClickListener(v -> { ... });

        // Việc fetchData ban đầu cũng sẽ do Fragment tự quản lý
        // fetchData();
    }

    private void setupViewPagerAndTabs() {
        viewPager = binding.viewPager; // Lấy ViewPager2 từ binding
        tabLayout = binding.tabLayout; // Lấy TabLayout từ binding

        mediaFeedPagerAdapter = new MediaFeedPagerAdapter(this);
        viewPager.setAdapter(mediaFeedPagerAdapter);

        // Kết nối TabLayout với ViewPager2
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    if (position == 0) {
                        tab.setText("Videos");
                        // tab.setIcon(R.drawable.ic_videos_tab); // Tùy chọn: thêm icon
                    } else {
                        tab.setText("Music");
                        // tab.setIcon(R.drawable.ic_music_tab);  // Tùy chọn: thêm icon
                    }
                }).attach();

        // (Tùy chọn) Giữ lại số lượng trang được load để tránh Fragment bị hủy và tạo lại quá thường xuyên
        // viewPager.setOffscreenPageLimit(2); // Bằng số lượng tab
    }

    private String getCurrentUsernameFromTokenOrPrefs() {
        String token = sessionManager.getToken();
        if (token != null && !token.startsWith("dummy-test-token")) {
            try {
                String[] chunks = token.split("\\.");
                if (chunks.length >= 2) {
                    byte[] decodedBytes = android.util.Base64.decode(chunks[1], android.util.Base64.URL_SAFE);
                    String decodedPayload = new String(decodedBytes, StandardCharsets.UTF_8);
                    com.google.gson.JsonObject payloadJson = new Gson().fromJson(decodedPayload, com.google.gson.JsonObject.class);
                    if (payloadJson != null && payloadJson.has("sub")) {
                        String username = payloadJson.get("sub").getAsString();
                        Log.d(TAG, "Username retrieved from token: " + username);
                        return username;
                    } else {
                        Log.w(TAG, "Token payload does not contain 'sub' claim or is null.");
                    }
                } else {
                    Log.w(TAG, "Invalid JWT token format (less than 2 parts).");
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error decoding Base64 token payload: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Could not get username from token due to an unexpected error: " + e.getMessage(), e);
            }
        } else if (token != null && token.startsWith("dummy-test-token")) {
            Log.i(TAG, "Using dummy token, returning 'admin' for testing purposes.");
            return "admin";
        }
        Log.w(TAG, "Could not determine current username. Token might be null, dummy, or invalid.");
        return null;
    }

    private void setupUploadLauncher() {
        uploadActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Log.d(TAG, "UploadActivity returned RESULT_OK. Refreshing current fragment.");
                        Toast.makeText(this, "Upload successful! Refreshing current feed...", Toast.LENGTH_SHORT).show();
                        refreshCurrentFragment(); // Gọi hàm để refresh fragment hiện tại
                    } else {
                        Log.d(TAG, "UploadActivity returned code: " + result.getResultCode() + ", not refreshing.");
                    }
                });
    }

    // Hàm để refresh Fragment hiện tại đang hiển thị
    private void refreshCurrentFragment() {
        if (viewPager == null || mediaFeedPagerAdapter == null) return;

        int currentItemPosition = viewPager.getCurrentItem();
        Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + currentItemPosition);

        // Hoặc cách khác để lấy Fragment (an toàn hơn nếu tag không được set đúng)
        // Fragment currentFragment = mediaFeedPagerAdapter.getFragmentAt(currentItemPosition);
        // (Bạn cần thêm hàm getFragmentAt vào MediaFeedPagerAdapter)

        if (currentFragment instanceof VideoFeedFragment) {
            ((VideoFeedFragment) currentFragment).refreshData(); // Giả sử Fragment có hàm này
            Log.d(TAG, "Refreshing VideoFeedFragment");
        } else if (currentFragment instanceof MusicFeedFragment) {
            ((MusicFeedFragment) currentFragment).refreshData(); // Giả sử Fragment có hàm này
            Log.d(TAG, "Refreshing MusicFeedFragment");
        }
    }

    // CÁC HÀM SAU SẼ ĐƯỢC CHUYỂN VÀO FRAGMENT, KHÔNG CÒN Ở MAINACTIVITY
    // private void setupRecyclerView() { ... }
    // private void showDeleteConfirmationDialog(MediaResponse mediaItem, int position) { ... }
    // private void deleteMediaItem(Long mediaId, int position) { ... }
    // private void fetchData() { ... } // Sẽ có trong từng Fragment
    // private void fetchUserMedia() { ... } // Sẽ có trong từng Fragment (ví dụ: "My Music/My Videos")
    // private void fetchPublicMedia() { ... } // Sẽ có trong từng Fragment
    // private void handleApiError(Response<?> response, String defaultMessage, boolean logoutOnError) { ... }
    // private void showState(StateType state, String message) { ... }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_logout) {
            logoutUser("You have been logged out.");
            return true;
        } else if (id == R.id.action_upload) {
            Log.d(TAG, "Upload menu item clicked.");
            Intent uploadIntent = new Intent(MainActivity.this, UploadActivity.class);
            uploadActivityResultLauncher.launch(uploadIntent);
            return true;
        } else if (id == R.id.action_refresh) {
            Log.d(TAG, "Refresh menu item clicked.");
            Toast.makeText(this, "Refreshing current feed...", Toast.LENGTH_SHORT).show();
            refreshCurrentFragment(); // Gọi hàm để refresh fragment hiện tại
            return true;
        } else if (id == R.id.action_profile) {
            Log.d(TAG, "Profile menu item clicked.");
            Intent profileIntent = new Intent(MainActivity.this, ProfileActivity.class);
            startActivity(profileIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logoutUser(String toastMessage) {
        Log.d(TAG, "Logging out user...");
        if (!TextUtils.isEmpty(toastMessage)) {
            Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
        }
        sessionManager.clearToken();
        currentUsername = null;

        // Không cần clear adapter ở đây nữa vì Fragment tự quản lý adapter của nó

        Intent intent = new Intent(MainActivity.this, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }
}