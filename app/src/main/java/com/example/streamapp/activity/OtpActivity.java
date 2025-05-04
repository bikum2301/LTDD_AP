package com.example.streamapp.activity; // Hoặc package của bạn

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.streamapp.databinding.ActivityOtpAcvitityBinding;
import com.example.streamapp.model.MessageResponse;
import com.example.streamapp.network.ApiClient;
import com.example.streamapp.network.ApiService;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OtpActivity extends AppCompatActivity {

    private ActivityOtpAcvitityBinding binding;
    private ApiService apiService;
    private String userEmail; // Để lưu email nhận được
    private static final String TAG = "OtpActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOtpAcvitityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        apiService = ApiClient.getApiService(this);

        // Lấy email từ Intent gửi từ RegisterActivity
        userEmail = getIntent().getStringExtra("USER_EMAIL");
        if (userEmail != null) {
            binding.tvUserEmail.setText(userEmail); // Hiển thị email
            binding.tvOtpInstruction.setText("Enter the 6-digit code sent to:");
        } else {
            binding.tvOtpInstruction.setText("Enter the 6-digit OTP code:");
            binding.tvUserEmail.setVisibility(View.GONE);
        }


        binding.btnVerifyOtp.setOnClickListener(v -> attemptVerification());

        // binding.tvResendOtp.setOnClickListener(v -> {
        //     // TODO: Implement Resend OTP functionality when backend API is ready
        //     Toast.makeText(this, "Resend OTP not implemented yet", Toast.LENGTH_SHORT).show();
        // });
    }

    private void attemptVerification() {
        String otpCode = binding.etOtpCode.getText().toString().trim();

        if (TextUtils.isEmpty(otpCode)) {
            binding.etOtpCode.setError("OTP Code is required");
            binding.etOtpCode.requestFocus();
            return;
        }
        if (otpCode.length() != 6) {
            binding.etOtpCode.setError("OTP Code must be 6 digits");
            binding.etOtpCode.requestFocus();
            return;
        }

        showLoading(true);

        apiService.verifyUser(otpCode).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(OtpActivity.this, response.body().getMessage(), Toast.LENGTH_LONG).show();
                    // Xác thực thành công, quay lại màn hình Login
                    navigateToLogin();
                } else {
                    // Xử lý lỗi từ server (ví dụ: OTP sai, hết hạn)
                    String errorMessage = "OTP Verification failed";
                    if (response.errorBody() != null) {
                        try {
                            MessageResponse errorMsg = new Gson().fromJson(response.errorBody().charStream(), MessageResponse.class);
                            if (errorMsg != null && !TextUtils.isEmpty(errorMsg.getMessage())) {
                                errorMessage = errorMsg.getMessage(); // Lấy thông báo lỗi cụ thể
                            } else {
                                errorMessage += ": " + response.code() + " " + response.message();
                            }
                            Log.e(TAG, "OTP Verification failed. Code: " + response.code() + ", ErrorBody: " + new Gson().toJson(errorMsg));

                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing error body for OTP verification", e);
                            errorMessage += ": Invalid response from server";
                        }
                    } else {
                        errorMessage += ": Unknown error";
                        Log.e(TAG, "OTP Verification failed with null error body. Code: " + response.code());
                    }
                    Toast.makeText(OtpActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "OTP Verification API call failed", t);
                Toast.makeText(OtpActivity.this, "Verification failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void navigateToLogin() {
        // Intent nên xóa các activity trước đó và đưa Login lên đầu
        Intent intent = new Intent(OtpActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish(); // Đóng OtpActivity và các Activity trước đó (như RegisterActivity)
    }

    private void showLoading(boolean isLoading) {
        binding.progressBarOtp.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnVerifyOtp.setEnabled(!isLoading);
        binding.etOtpCode.setEnabled(!isLoading);
        // binding.tvResendOtp.setEnabled(!isLoading);
    }
}