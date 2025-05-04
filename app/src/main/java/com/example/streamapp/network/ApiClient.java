package com.example.streamapp.network;

import android.content.Context;

import com.example.streamapp.utils.SessionManager; // Sẽ tạo ở bước sau
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    // Nếu chạy trên điện thoại thật cùng mạng Wifi: dùng địa chỉ IP LAN của máy tính
    private static final String BASE_URL = "https://musicandvideo-brh9bdc6cabcb4f0.canadacentral-01.azurewebsites.net/";

    private static Retrofit retrofit = null;
    private static ApiService apiService = null;

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            // Interceptor để log request/response (optional, hữu ích khi debug)
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            // Interceptor để tự động thêm Header Authorization
            AuthInterceptor authInterceptor = new AuthInterceptor(context);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging) // Thêm logging interceptor
                    .addInterceptor(authInterceptor)
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)// Thêm auth interceptor
                    .build();

            // Cấu hình Gson (nếu cần tùy chỉnh date format, etc.)
            Gson gson = new GsonBuilder()
                    .setLenient() // Có thể cần nếu JSON từ server không chuẩn lắm
                    .create();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client) // Sử dụng OkHttpClient đã thêm Interceptor
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();
        }
        return retrofit;
    }

    public static ApiService getApiService(Context context) {
        if (apiService == null) {
            apiService = getClient(context).create(ApiService.class);
        }
        return apiService;
    }

    // --- Auth Interceptor ---
    private static class AuthInterceptor implements Interceptor {
        private SessionManager sessionManager;

        AuthInterceptor(Context context) {
            sessionManager = new SessionManager(context);
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            String token = sessionManager.getToken();

            // Nếu có token và request không phải là login/register/verify, thêm header
            if (token != null && !isAuthRequest(originalRequest)) {
                Request.Builder builder = originalRequest.newBuilder()
                        .header("Authorization", "Bearer " + token);
                Request newRequest = builder.build();
                return chain.proceed(newRequest);
            }

            return chain.proceed(originalRequest);
        }

        // Helper để kiểm tra xem request có phải là request không cần token không
        private boolean isAuthRequest(Request request) {
            String path = request.url().encodedPath();
            return path.contains("/api/auth/login") ||
                    path.contains("/api/auth/register") ||
                    path.contains("/api/auth/verify");
        }
    }
}