package com.example.streamapp.activity;

import androidx.annotation.NonNull; // Import NonNull
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.MenuItem; // Import MenuItem
import android.view.View;
import android.widget.Toast;

// Đảm bảo tên binding đúng với layout XML mới (activity_register.xml -> ActivityRegisterBinding)
import com.example.streamapp.databinding.ActivityRegisterBinding;
import com.example.streamapp.model.MessageResponse;
import com.example.streamapp.model.SignupRequest;
import com.example.streamapp.network.ApiClient;
import com.example.streamapp.network.ApiService;
import com.google.gson.Gson; // Import Gson

import java.io.IOException; // Import IOException

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    // Sử dụng lớp ViewBinding tương ứng
    private ActivityRegisterBinding binding;
    private ApiService apiService;
    private static final String TAG = "RegisterActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate layout mới
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // --- Setup Toolbar ---
        setSupportActionBar(binding.toolbarRegister); // Dùng ID toolbar mới
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Hiện nút back
            getSupportActionBar().setDisplayShowTitleEnabled(false); // Ẩn tiêu đề toolbar
        }

        // --- Initialize ---
        apiService = ApiClient.getApiService(getApplicationContext());

        // --- Set Listeners ---
        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.tvGoToLogin.setOnClickListener(v -> {
            Log.d(TAG, "Navigating from Register to Login");
            // Tạo Intent để mở LoginActivity
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            // (Tùy chọn) Thêm cờ nếu cần
            // intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(intent);
            // Đóng RegisterActivity sau khi mở LoginActivity
            finish();
        });
    }

    /**
     * Xử lý logic đăng ký.
     */
    private void attemptRegister() {
        // Lấy dữ liệu từ TextInputEditText
        String fullName = binding.etFullNameReg.getText().toString().trim();
        String email = binding.etEmailReg.getText().toString().trim();
        String username = binding.etUsernameReg.getText().toString().trim();
        String password = binding.etPasswordReg.getText().toString().trim();

        // Xóa lỗi cũ (nếu có)
        binding.tilFullNameReg.setError(null);
        binding.tilUsernameReg.setError(null);
        binding.tilEmailReg.setError(null);
        binding.tilPasswordReg.setError(null);

        // --- Validation Input ---
        if (!validateInput(fullName, email, username, password)) {
            return; // Dừng nếu input không hợp lệ
        }
        // --- End Validation ---

        showLoading(true); // Hiển thị loading
        SignupRequest signupRequest = new SignupRequest(username, email, password, fullName);

        apiService.registerUser(signupRequest).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(@NonNull Call<MessageResponse> call, @NonNull Response<MessageResponse> response) {
                showLoading(false); // Ẩn loading
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null) {
                    // Đăng ký thành công (nhưng chưa verify OTP)
                    Toast.makeText(RegisterActivity.this, response.body().getMessage(), Toast.LENGTH_LONG).show();
                    Log.i(TAG, "Registration request successful. Proceeding to OTP verification.");
                    navigateToOtp(email); // Chuyển sang màn hình OTP
                } else {
                    // Xử lý lỗi từ server
                    handleRegisterApiError(response);
                }
            }

            @Override
            public void onFailure(@NonNull Call<MessageResponse> call, @NonNull Throwable t) {
                showLoading(false); // Ẩn loading
                if (isFinishing() || isDestroyed()) return;

                Log.e(TAG, "Registration API call failed", t);
                Toast.makeText(RegisterActivity.this, "Registration failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Kiểm tra tính hợp lệ của các trường input.
     * @return True nếu tất cả hợp lệ, False nếu có lỗi.
     */
    private boolean validateInput(String fullName, String email, String username, String password) {
        boolean isValid = true;

        if (TextUtils.isEmpty(fullName)) {
            binding.tilFullNameReg.setError("Full Name is required");
            isValid = false;
        }
        if (TextUtils.isEmpty(username)) {
            binding.tilUsernameReg.setError("Username is required");
            isValid = false;
        } else if (username.length() < 3) {
            binding.tilUsernameReg.setError("Username must be at least 3 characters");
            isValid = false;
        }
        if (TextUtils.isEmpty(email)) {
            binding.tilEmailReg.setError("Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmailReg.setError("Enter a valid email address");
            isValid = false;
        }
        if (TextUtils.isEmpty(password)) {
            binding.tilPasswordReg.setError("Password is required");
            isValid = false;
        } else if (password.length() < 6) {
            binding.tilPasswordReg.setError("Password must be at least 6 characters");
            isValid = false;
        }

        return isValid;
    }

    /**
     * Xử lý lỗi cụ thể từ API Register.
     */
    private void handleRegisterApiError(Response<?> response) {
        String errorMessage = "Registration failed";
        int code = response != null ? response.code() : -1;
        Log.e(TAG, "Registration failed - Code: " + code);

        String errorBodyContent = "";
        if (response != null && response.errorBody() != null) {
            try { errorBodyContent = response.errorBody().string(); Log.e(TAG, "Register Error Body Raw: " + errorBodyContent); }
            catch (IOException e) { Log.e(TAG, "Error reading error body", e); }
        }

        if (!errorBodyContent.isEmpty()) {
            try {
                MessageResponse errorMsg = new Gson().fromJson(errorBodyContent, MessageResponse.class);
                if (errorMsg != null && !TextUtils.isEmpty(errorMsg.getMessage())) {
                    errorMessage = errorMsg.getMessage();
                    // Kiểm tra nội dung lỗi để hiển thị trên trường phù hợp
                    if (errorMessage.toLowerCase().contains("username")) {
                        binding.tilUsernameReg.setError(errorMessage);
                    } else if (errorMessage.toLowerCase().contains("email")) {
                        binding.tilEmailReg.setError(errorMessage);
                    } else {
                        // Lỗi chung không rõ trường nào, hiển thị Toast
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                } else { errorMessage += " (Code: " + code + ")"; Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();}
            } catch (Exception jsonError) {
                Log.w(TAG,"Could not parse register error body as JSON: " + jsonError.getMessage());
                errorMessage += " (Code: " + code + ")";
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
            }
        } else if (response != null) { // Không có error body
            errorMessage += " (Code: " + code + ")";
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
        } else { // response null
            errorMessage += ": Unknown error";
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
        }
    }


    /**
     * Chuyển sang màn hình nhập OTP.
     * @param userEmail Email của người dùng để truyền sang OtpActivity.
     */
    private void navigateToOtp(String userEmail) {
        Intent intent = new Intent(RegisterActivity.this, OtpActivity.class);
        intent.putExtra("USER_EMAIL", userEmail); // Gửi email qua Intent
        startActivity(intent);
        // Không finish() ở đây để người dùng có thể back lại nếu cần sửa thông tin
    }

    /**
     * Hiển thị/Ẩn ProgressBar và quản lý trạng thái enable của các View.
     */
    private void showLoading(boolean isLoading) {
        runOnUiThread(() -> {
            binding.progressBarReg.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnRegister.setEnabled(!isLoading);
            binding.tvGoToLogin.setEnabled(!isLoading);
            binding.tilFullNameReg.setEnabled(!isLoading);
            binding.tilUsernameReg.setEnabled(!isLoading);
            binding.tilEmailReg.setEnabled(!isLoading);
            binding.tilPasswordReg.setEnabled(!isLoading);
        });
    }

    /**
     * Xử lý nút back trên Toolbar.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Kết thúc activity hiện tại để quay lại WelcomeActivity
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}