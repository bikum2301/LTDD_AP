package com.example.streamapp.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

import com.example.streamapp.databinding.ActivityWelcomeBinding;
import com.example.streamapp.utils.SessionManager; // Import SessionManager

public class WelcomeActivity extends AppCompatActivity {

    private ActivityWelcomeBinding binding;
    private SessionManager sessionManager; // Thêm SessionManager

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this); // Khởi tạo

        // *** QUAN TRỌNG: Kiểm tra nếu đã đăng nhập thì vào thẳng MainActivity ***
        if (sessionManager.getToken() != null) {
            Intent mainIntent = new Intent(WelcomeActivity.this, MainActivity.class);
            // Thêm cờ để xóa WelcomeActivity khỏi back stack
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(mainIntent);
            finish(); // Đóng WelcomeActivity
            return; // Không cần làm gì thêm trong onCreate
        }
        // *** Kết thúc kiểm tra đăng nhập ***


        // Set listener cho nút Login
        binding.btnGoToLogin.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        // Set listener cho nút Sign Up
        binding.btnGoToSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }
}