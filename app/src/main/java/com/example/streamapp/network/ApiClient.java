// File: src/main/java/com/example/streamapp/network/ApiClient.java
package com.example.streamapp.network;

import android.content.Context;
import android.util.Log; // Thêm import cho Log

import androidx.annotation.NonNull;

import com.example.streamapp.utils.SessionManager;
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

    // Đảm bảo BASE_URL trỏ đúng đến địa chỉ IP local và cổng của backend
    private static final String BASE_URL = "http://192.168.1.152:9999/"; // Giữ nguyên IP của bạn

    private static Retrofit retrofit = null;
    private static ApiService apiService = null; // apiService nên được khởi tạo mỗi lần getApiService nếu retrofit là null

    public static Retrofit getClient(Context context) {
        // Không nên dùng singleton pattern cho Retrofit theo cách này nếu context có thể thay đổi
        // hoặc nếu bạn muốn cấu hình OkHttpClient khác nhau cho các trường hợp khác nhau.
        // Tuy nhiên, để đơn giản, chúng ta giữ lại cấu trúc hiện tại nhưng làm cho nó an toàn hơn một chút.
        // Cách tốt hơn là inject OkHttpClient và Retrofit bằng Dagger/Hilt.

        // Tạo HttpLoggingInterceptor với một custom logger để có thể lọc tag trong Logcat
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message -> {
            // Log message với tag "OkHttp"
            // Bạn có thể tùy chỉnh tag này
            Log.d("OkHttp", message);
        });

        // Đặt mức độ log. Khi upload file lớn, dùng HEADERS để tránh OutOfMemoryError.
        // Khi debug các API khác, bạn có thể tạm thời đổi lại thành BODY.
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);
        // loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY); // GÂY OOM VỚI FILE LỚN
        // loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        // loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.NONE);


        // Interceptor để tự động thêm Header Authorization
        AuthInterceptor authInterceptor = new AuthInterceptor(context.getApplicationContext()); // Dùng application context

        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();

        // Thứ tự add Interceptor quan trọng:
        // AuthInterceptor nên được thêm trước LoggingInterceptor nếu bạn muốn thấy
        // header Authorization đã được thêm vào trong log (khi log ở mức HEADERS hoặc BODY).
        httpClientBuilder.addInterceptor(authInterceptor);
        httpClientBuilder.addInterceptor(loggingInterceptor);

        // Thiết lập timeouts
        httpClientBuilder.connectTimeout(60, TimeUnit.SECONDS);    // Thời gian chờ kết nối
        httpClientBuilder.readTimeout(600, TimeUnit.SECONDS);     // Thời gian chờ đọc response (10 phút)
        httpClientBuilder.writeTimeout(600, TimeUnit.SECONDS);    // Thời gian chờ ghi request (10 phút)

        OkHttpClient client = httpClientBuilder.build();

        // Cấu hình Gson
        Gson gson = new GsonBuilder()
                .setLenient() // Cho phép JSON ít chuẩn hơn một chút
                .create();

        // Tạo Retrofit instance mới mỗi lần nếu nó là null (an toàn hơn cho static variable)
        // Hoặc bạn có thể đồng bộ hóa việc khởi tạo này.
        // Đối với mục đích hiện tại, kiểm tra null là đủ.
        if (retrofit == null) { // Chỉ tạo mới nếu chưa có
            synchronized (ApiClient.class) { // Đồng bộ hóa để tránh tạo nhiều instance khi có nhiều thread gọi
                if (retrofit == null) {
                    retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create(gson))
                            .build();
                }
            }
        }
        return retrofit;
    }

    public static ApiService getApiService(Context context) {
        // Luôn lấy Retrofit client mới nhất (hoặc instance đã có)
        // và tạo ApiService từ đó nếu apiService là null hoặc retrofit đã thay đổi (dù ở đây retrofit là static)
        if (apiService == null) {
            synchronized (ApiClient.class) {
                if (apiService == null) {
                    apiService = getClient(context.getApplicationContext()).create(ApiService.class);
                }
            }
        }
        return apiService;
    }

    // --- Auth Interceptor ---
    private static class AuthInterceptor implements Interceptor {
        private SessionManager sessionManager;

        AuthInterceptor(Context context) {
            // Nên sử dụng ApplicationContext để tránh memory leak tiềm ẩn với Activity Context
            this.sessionManager = new SessionManager(context.getApplicationContext());
        }

        @NonNull
        @Override
        public Response intercept(@NonNull Chain chain) throws IOException {
            Request originalRequest = chain.request();
            String token = sessionManager.getToken();

            // Chỉ thêm token nếu token tồn tại và request không phải là các request auth
            if (token != null && !token.isEmpty() && !isAuthRequest(originalRequest)) {
                Log.d("AuthInterceptor", "Token found, adding Authorization header for: " + originalRequest.url().encodedPath());
                Request.Builder builder = originalRequest.newBuilder()
                        .header("Authorization", "Bearer " + token);
                Request newRequest = builder.build();
                return chain.proceed(newRequest);
            } else if (token == null && !isAuthRequest(originalRequest)) {
                Log.w("AuthInterceptor", "Token is null, proceeding without Authorization header for: " + originalRequest.url().encodedPath());
            }


            return chain.proceed(originalRequest);
        }

        // Helper để kiểm tra xem request có phải là request không cần token không
        private boolean isAuthRequest(Request request) {
            String path = request.url().encodedPath();
            // Thêm dấu / ở đầu để khớp chính xác hơn, tránh trường hợp path chứa các từ này ở giữa
            return path.contains("/api/auth/login") ||
                    path.contains("/api/auth/register") ||
                    path.contains("/api/auth/verify") ||
                    path.contains("/api/media/public"); // API public media cũng không cần token
        }
    }

    // (Tùy chọn) Phương thức để reset Retrofit và ApiService instance nếu cần (ví dụ khi đổi BASE_URL)
    public static void resetApiService() {
        retrofit = null;
        apiService = null;
    }
}