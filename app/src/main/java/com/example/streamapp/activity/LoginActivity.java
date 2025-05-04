package com.example.streamapp.activity;

import androidx.annotation.NonNull; // Import NonNull
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem; // Import MenuItem
import android.view.View;
import android.widget.Toast;

import com.example.streamapp.activity.MainActivity; // Import MainActivity từ package gốc
import com.example.streamapp.databinding.ActivityLoginBinding; // Sử dụng binding tương ứng với layout mới
import com.example.streamapp.model.LoginRequest;
import com.example.streamapp.model.MessageResponse;
import com.example.streamapp.network.ApiClient;
import com.example.streamapp.network.ApiService;
import com.example.streamapp.utils.SessionManager;
import com.google.gson.Gson;

import java.io.IOException; // Import IOException

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    // Đảm bảo tên binding khớp với file layout XML của bạn (ví dụ: activity_login.xml -> ActivityLoginBinding)
    private ActivityLoginBinding binding;
    private ApiService apiService;
    private SessionManager sessionManager;
    private static final String TAG = "LoginActivity";

    // Đặt là false để dùng API thật, true để test với "admin"/"123"
    private static final boolean ENABLE_TEST_LOGIN = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate layout mới
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // --- Setup Toolbar ---
        setSupportActionBar(binding.toolbarLogin); // Sử dụng ID của Toolbar trong layout mới
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Hiển thị nút back
            getSupportActionBar().setDisplayShowTitleEnabled(false); // Ẩn tiêu đề mặc định của Toolbar
        }

        // --- Initialize ---
        apiService = ApiClient.getApiService(getApplicationContext()); // Dùng ApplicationContext
        sessionManager = new SessionManager(getApplicationContext());

        // --- Set Listeners ---
        binding.btnLogin.setOnClickListener(v -> attemptLogin()); // Nút Login mới
        binding.tvGoToRegister.setOnClickListener(v -> {
            Log.d(TAG, "Navigating from Login to Register");
            // Tạo Intent để mở RegisterActivity
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            // (Tùy chọn) Thêm cờ để không đưa Login vào back stack khi mở Register từ đây
            // intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY); // Hoặc dùng finish()
            startActivity(intent);
            // Đóng LoginActivity sau khi mở RegisterActivity
            finish();
        });

        // TODO: Thêm listener cho binding.tvForgotPassword nếu cần xử lý quên mật khẩu
    }

    /**
     * Xử lý logic đăng nhập, bao gồm cả chế độ test và gọi API.
     */
    private void attemptLogin() {
        // Lấy dữ liệu từ TextInputEditText bên trong TextInputLayout
        String username = binding.etUsername.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        // Xóa lỗi cũ trên TextInputLayout (nếu có)
        binding.tilUsername.setError(null);
        binding.tilPassword.setError(null);

        // --- Chế độ Test ---
        if (ENABLE_TEST_LOGIN && "admin".equals(username) && "123".equals(password)) {
            Log.i(TAG, "Performing test login for 'admin'");
            String dummyToken = "dummy-test-token-for-admin-" + System.currentTimeMillis();
            sessionManager.saveToken(dummyToken);
            Toast.makeText(LoginActivity.this, "Test Login Successful!", Toast.LENGTH_SHORT).show();
            navigateToMain();
            return;
        }
        // --- Kết thúc Test ---

        // --- Validation Input ---
        boolean isValid = true;
        if (TextUtils.isEmpty(username)) {
            binding.tilUsername.setError("Username or Email cannot be empty"); // Đặt lỗi cho TextInputLayout
            isValid = false;
        }
        if (TextUtils.isEmpty(password)) {
            binding.tilPassword.setError("Password cannot be empty"); // Đặt lỗi cho TextInputLayout
            isValid = false;
        }
        if (!isValid) {
            return; // Dừng nếu input không hợp lệ
        }
        // --- Kết thúc Validation ---

        // --- Gọi API thật ---
        showLoading(true); // Hiển thị ProgressBar và vô hiệu hóa input/button
        LoginRequest loginRequest = new LoginRequest(username, password);

        apiService.loginUser(loginRequest).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(@NonNull Call<MessageResponse> call, @NonNull Response<MessageResponse> response) {
                // Luôn ẩn loading khi nhận được phản hồi (thành công hoặc lỗi)
                showLoading(false);
                // Kiểm tra activity còn tồn tại không
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null && response.body().getToken() != null) {
                    // Đăng nhập thành công
                    sessionManager.saveToken(response.body().getToken());
                    Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                    navigateToMain(); // Chuyển đến màn hình chính
                } else {
                    // Xử lý lỗi đăng nhập từ server
                    handleLoginApiError(response);
                }
            }

            @Override
            public void onFailure(@NonNull Call<MessageResponse> call, @NonNull Throwable t) {
                showLoading(false); // Luôn ẩn loading khi có lỗi mạng
                if (isFinishing() || isDestroyed()) return;

                Log.e(TAG, "Login API call failed", t);
                // Hiển thị lỗi mạng chung
                Toast.makeText(LoginActivity.this, "Login failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Xử lý lỗi cụ thể từ API Login, hiển thị lỗi trên các trường nhập liệu nếu có thể.
     * @param response Phản hồi từ Retrofit.
     */
    private void handleLoginApiError(Response<?> response) {
        String errorMessage = "Login failed"; // Lỗi mặc định
        int code = response != null ? response.code() : -1;

        String errorBodyContent = "";
        if (response != null && response.errorBody() != null) {
            try { errorBodyContent = response.errorBody().string(); Log.e(TAG, "Login Error Body Raw: " + errorBodyContent); }
            catch (IOException e) { Log.e(TAG, "Error reading error body", e); }
        }

        if (!errorBodyContent.isEmpty()) {
            try {
                MessageResponse errorMsg = new Gson().fromJson(errorBodyContent, MessageResponse.class);
                if (errorMsg != null && !TextUtils.isEmpty(errorMsg.getMessage())) {
                    errorMessage = errorMsg.getMessage(); // Lấy lỗi từ API
                } else { errorMessage += " (Code: " + code + ")"; }
            } catch (Exception jsonError) {
                Log.w(TAG,"Could not parse login error body as JSON: " + jsonError.getMessage());
                errorMessage += " (Code: " + code + ")";
            }
        } else if (response != null) { errorMessage += " (Code: " + code + ")"; }
        else { errorMessage += ": Unknown error"; }

        // Hiển thị lỗi
        // Nếu là lỗi sai thông tin (ví dụ 401 hoặc 400), hiển thị lỗi trên trường Password
        if (code == 401 || code == 400) { // Giả sử 401/400 là sai thông tin
            binding.tilUsername.setError(" "); // Đặt lỗi trống để TextInputLayout đổi màu viền
            binding.tilPassword.setError(errorMessage); // Hiển thị thông báo lỗi ở trường Password
        } else {
            // Các lỗi khác thì hiển thị Toast
            Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
        }
    }


    /**
     * Chuyển đến MainActivity và xóa các activity trước đó khỏi back stack.
     */
    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish(); // Đóng LoginActivity
    }

    /**
     * Hiển thị/Ẩn ProgressBar và vô hiệu hóa/kích hoạt các input/button.
     * @param isLoading True để hiển thị loading, False để ẩn.
     */
    private void showLoading(boolean isLoading) {
        runOnUiThread(() -> { // Đảm bảo chạy trên UI thread
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnLogin.setEnabled(!isLoading);
            binding.tvGoToRegister.setEnabled(!isLoading); // Vô hiệu hóa link Sign Up
            binding.tilUsername.setEnabled(!isLoading);    // Vô hiệu hóa TextInputLayout
            binding.tilPassword.setEnabled(!isLoading);
            // binding.tvForgotPassword.setEnabled(!isLoading); // Vô hiệu hóa link Forgot Pass
        });
    }

    /**
     * Xử lý sự kiện nhấn nút back trên Toolbar.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Kiểm tra xem có phải là nút home (back) không
        if (item.getItemId() == android.R.id.home) {
            // Kết thúc activity hiện tại để quay lại màn hình trước đó (WelcomeActivity)
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}