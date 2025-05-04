package com.example.streamapp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class SessionManager {

    private static final String USER_TOKEN = "user_token";
    private SharedPreferences prefs;

    public SessionManager(Context context) {
        // Sử dụng context ứng dụng để tránh memory leak
        prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    /**
     * Lưu token vào SharedPreferences.
     * @param token JWT token.
     */
    public void saveToken(String token) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(USER_TOKEN, token);
        editor.apply(); // apply() chạy bất đồng bộ, commit() chạy đồng bộ
    }

    /**
     * Lấy token từ SharedPreferences.
     * @return JWT token hoặc null nếu không tìm thấy.
     */
    public String getToken() {
        return prefs.getString(USER_TOKEN, null);
    }

    /**
     * Xóa token khỏi SharedPreferences (khi logout).
     */
    public void clearToken() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(USER_TOKEN);
        editor.apply();
    }
}