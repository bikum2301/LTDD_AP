package com.example.streamapp.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.DecelerateInterpolator; // Import Interpolator

import com.example.streamapp.activity.MainActivity; // Import MainActivity
import com.example.streamapp.databinding.ActivityWelcomeBinding; // Import Binding
import com.example.streamapp.utils.SessionManager;

public class WelcomeActivity extends AppCompatActivity {

    private ActivityWelcomeBinding binding; // Khai báo ViewBinding
    private SessionManager sessionManager;
    private static final String TAG = "WelcomeActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate layout sử dụng ViewBinding
        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        // Kiểm tra nếu người dùng đã đăng nhập thì chuyển thẳng vào MainActivity
        if (sessionManager.getToken() != null) {
            Log.i(TAG, "User already logged in. Navigating to MainActivity.");
            navigateToMain(); // Gọi hàm điều hướng
            return; // Thoát khỏi onCreate sớm
        }

        // Nếu chưa đăng nhập, thiết lập màn hình Welcome
        Log.d(TAG, "User not logged in. Setting up Welcome Screen.");

        // Bắt đầu hiệu ứng xuất hiện sau khi layout đã sẵn sàng
        binding.getRoot().post(() -> {
            Log.d(TAG, "Starting entrance animation.");
            startEntranceAnimation();
        });

        // Gán sự kiện click cho các nút
        binding.btnGoToLogin.setOnClickListener(v -> {
            Log.d(TAG, "Login button clicked.");
            startActivity(new Intent(WelcomeActivity.this, LoginActivity.class));
        });

        binding.btnGoToSignUp.setOnClickListener(v -> {
            Log.d(TAG, "Sign Up button clicked.");
            startActivity(new Intent(WelcomeActivity.this, RegisterActivity.class));
        });
    }

    /**
     * Thực hiện hiệu ứng xuất hiện cho các thành phần trên màn hình.
     */
    private void startEntranceAnimation() {
        long duration = 700; // Thời gian animation (milliseconds)
        long logoDelay = 150;  // Thời gian trễ trước khi animation của CardView bắt đầu
        long sloganDelay = logoDelay + 250; // Trễ thêm cho slogan
        long buttonsDelay = sloganDelay + 250; // Trễ thêm cho các nút

        // Animate CardView chứa Lottie (Fade in và trượt xuống)
        binding.cardLottieBackground.animate() // Sử dụng ID của CardView
                .alpha(1f) // Hiện ra
                .translationY(0f) // Về vị trí Y ban đầu
                .setDuration(duration)
                .setStartDelay(logoDelay)
                .setInterpolator(new DecelerateInterpolator(1.5f)) // Hiệu ứng chậm dần
                .start();
        // Lottie animation bên trong sẽ tự chạy do app:lottie_autoPlay="true"

        // Animate Slogan (Fade in và trượt lên)
        binding.tvSlogan.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(sloganDelay)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();

        // Animate LinearLayout chứa các nút (Fade in và trượt lên)
        binding.llButtons.animate() // Sử dụng ID của LinearLayout
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(buttonsDelay)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
    }

    /**
     * (Tùy chọn) Quản lý vòng đời Lottie để tiết kiệm tài nguyên.
     */
    @Override
    protected void onPause() {
        super.onPause();
        // Tạm dừng animation khi Activity không còn thấy
        if (binding != null && binding.lottieAnimationView != null && binding.lottieAnimationView.isAnimating()) {
            binding.lottieAnimationView.pauseAnimation();
            Log.d(TAG, "Lottie animation paused.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tiếp tục/Bắt đầu animation khi Activity thấy lại
        if (binding != null && binding.lottieAnimationView != null && !binding.lottieAnimationView.isAnimating()){
            // Gọi playAnimation() để đảm bảo nó chạy (ngay cả khi autoPlay=true nhưng có thể bị pause)
            binding.lottieAnimationView.playAnimation();
            Log.d(TAG, "Lottie animation (re)started in onResume.");
        }
    }
    // Lưu ý: Không cần thiết phải dừng hoàn toàn trong onDestroy nếu chỉ dùng loop và autoPlay cơ bản.

    /**
     * Chuyển hướng đến MainActivity và xóa các activity trước đó khỏi back stack.
     */
    private void navigateToMain() {
        Intent mainIntent = new Intent(WelcomeActivity.this, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(mainIntent);
        finish(); // Đóng WelcomeActivity
    }
}