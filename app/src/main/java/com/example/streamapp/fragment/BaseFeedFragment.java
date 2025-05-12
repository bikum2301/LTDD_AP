// File: src/main/java/com/example/streamapp/fragment/BaseFeedFragment.java
package com.example.streamapp.fragment; // Hoặc package của bạn

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.streamapp.R; // Import R chung
import com.example.streamapp.activity.WelcomeActivity;
import com.example.streamapp.model.ErrorResponse;
import com.example.streamapp.model.MediaResponse; // Cần cho các interface listener nếu giữ lại ở base
import com.example.streamapp.model.MessageResponse;
import com.example.streamapp.network.ApiClient;
import com.example.streamapp.network.ApiService;
import com.example.streamapp.utils.SessionManager;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Sử dụng Generic Type cho Adapter và ViewHolder nếu cần thiết,
// hoặc một interface chung cho Adapter
public abstract class BaseFeedFragment<AdapterType extends RecyclerView.Adapter<? extends RecyclerView.ViewHolder>> extends Fragment {

    private static final String BASE_TAG = "BaseFeedFragment"; // Tag chung cho logging
    protected String fragmentTag; // Tag cụ thể cho từng fragment con

    protected RecyclerView recyclerView;
    protected AdapterType mediaAdapter; // Kiểu Adapter sẽ được lớp con định nghĩa
    protected ApiService apiService;
    protected SessionManager sessionManager;
    protected String currentUsername;

    protected ProgressBar progressBar;
    protected TextView tvEmptyState;
    protected Button btnRetry;

    // Enum cho trạng thái UI, có thể dùng chung
    protected enum StateType { LOADING, CONTENT, EMPTY, ERROR }

    // Constructor mặc định
    public BaseFeedFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fragmentTag = getClass().getSimpleName(); // Lấy tên lớp con làm tag
        Log.d(fragmentTag, "onCreate");

        if (getActivity() != null) {
            apiService = ApiClient.getApiService(requireActivity().getApplicationContext());
            sessionManager = new SessionManager(requireActivity().getApplicationContext());
            currentUsername = getCurrentUsernameFromToken();
        } else {
            Log.e(fragmentTag, "Activity is null during onCreate, services might not be initialized.");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(fragmentTag, "onCreateView");
        // Lớp con sẽ inflate layout của riêng nó
        View view = inflater.inflate(getLayoutResourceId(), container, false);

        // Ánh xạ các view chung
        recyclerView = view.findViewById(getRecyclerViewId());
        progressBar = view.findViewById(getProgressBarId());
        tvEmptyState = view.findViewById(getEmptyStateTextViewId());
        btnRetry = view.findViewById(getRetryButtonId());

        setupRecyclerViewBase();
        if (btnRetry != null) {
            btnRetry.setOnClickListener(v -> {
                Log.d(fragmentTag, "Retry button clicked.");
                fetchData(); // Gọi hàm fetchData trừu tượng
            });
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(fragmentTag, "onViewCreated");
        // Gọi fetchData nếu danh sách rỗng (hoặc theo logic của lớp con)
        // Điều này có thể cần kiểm tra mediaAdapter đã được khởi tạo chưa
        // Chúng ta sẽ để lớp con quyết định khi nào gọi fetchData lần đầu
        // if (mediaAdapter != null && isAdapterEmpty()) {
        //     fetchData();
        // }
        // Hoặc đơn giản là luôn gọi fetchData khi view được tạo
        fetchData();
    }

    // --- Các phương thức trừu tượng mà lớp con phải implement ---
    protected abstract int getLayoutResourceId(); // Trả về ID layout của Fragment con
    protected abstract int getRecyclerViewId();
    protected abstract int getProgressBarId();
    protected abstract int getEmptyStateTextViewId();
    protected abstract int getRetryButtonId();
    protected abstract AdapterType createAdapter(); // Lớp con tạo instance adapter của nó
    protected abstract void fetchData();           // Lớp con implement logic fetch dữ liệu riêng

    // --- Các phương thức chung ---
    protected void setupRecyclerViewBase() {
        mediaAdapter = createAdapter();
        if (recyclerView != null && mediaAdapter != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setAdapter(mediaAdapter);
            // Các listener chung cho adapter có thể được set ở đây nếu có,
            // hoặc để lớp con tự set listener cụ thể của nó.
            // Ví dụ, nếu AdapterType là một interface có setCurrentUsername:
            // if (mediaAdapter instanceof UserSpecificAdapter) { // Tạo một interface chung
            //    ((UserSpecificAdapter) mediaAdapter).setCurrentUsername(currentUsername);
            // }
        } else {
            Log.e(fragmentTag, "RecyclerView or MediaAdapter is null in setupRecyclerViewBase.");
        }
    }

    public void refreshData() {
        Log.d(fragmentTag, "refreshData called");
        currentUsername = getCurrentUsernameFromToken(); // Cập nhật username
        // Cập nhật username cho adapter nếu adapter có phương thức đó
        // if (mediaAdapter instanceof UserSpecificAdapter) {
        //    ((UserSpecificAdapter) mediaAdapter).setCurrentUsername(currentUsername);
        // }
        // Cần đảm bảo adapter đã được khởi tạo
        if (mediaAdapter == null) {
            setupRecyclerViewBase(); // Thử khởi tạo lại nếu chưa có
        }
        if (mediaAdapter instanceof com.example.streamapp.adapter.VideoAdapter) { // Ví dụ cụ thể
            ((com.example.streamapp.adapter.VideoAdapter) mediaAdapter).setCurrentUsername(currentUsername);
        } else if (mediaAdapter instanceof com.example.streamapp.adapter.MusicAdapter) {
            ((com.example.streamapp.adapter.MusicAdapter) mediaAdapter).setCurrentUsername(currentUsername);
        }

        fetchData(); // Gọi lại logic fetch dữ liệu của lớp con
    }

    protected String getCurrentUsernameFromToken() {
        if (sessionManager == null) {
            Log.w(fragmentTag, "SessionManager is null in getCurrentUsernameFromToken.");
            return null;
        }
        String token = sessionManager.getToken();
        if (token != null && !token.startsWith("dummy-test-token")) {
            try {
                String[] chunks = token.split("\\.");
                if (chunks.length >= 2) {
                    byte[] data = android.util.Base64.decode(chunks[1], android.util.Base64.URL_SAFE);
                    String decodedPayload = new String(data, StandardCharsets.UTF_8);
                    com.google.gson.JsonObject payloadJson = new Gson().fromJson(decodedPayload, com.google.gson.JsonObject.class);
                    if (payloadJson != null && payloadJson.has("sub")) {
                        return payloadJson.get("sub").getAsString();
                    }
                }
            } catch (Exception e) { Log.e(fragmentTag, "Error getting username from token", e); }
        } else if (token != null && token.startsWith("dummy-test-token")){
            return "admin";
        }
        return null;
    }

    protected void handleApiError(Response<?> response, String defaultMessage, boolean logoutOnError) {
        if (getActivity() == null || !isAdded()) {
            Log.w(fragmentTag, "Fragment not attached, cannot handle API error.");
            return;
        }

        String errorMessage = defaultMessage;
        int responseCode = -1;
        String errorBodyContent = null;

        if (response != null) {
            responseCode = response.code();
            if (response.errorBody() != null) {
                try { errorBodyContent = response.errorBody().string(); }
                catch (IOException e) { Log.e(fragmentTag, "Error reading error body: ", e); }
            }
        }
        Log.e(fragmentTag, defaultMessage + " - Code: " + responseCode + ", RawErrorBody: " + errorBodyContent);

        if ((response == null || responseCode == 401 || responseCode == 403) && logoutOnError) {
            logoutUserFromFragment("Session expired or unauthorized. Please login again.");
            return;
        }

        if (errorBodyContent != null && !errorBodyContent.isEmpty()) {
            try {
                ErrorResponse backendError = new Gson().fromJson(errorBodyContent, ErrorResponse.class);
                if (backendError != null && !TextUtils.isEmpty(backendError.getMessage())) {
                    errorMessage = backendError.getMessage();
                } else if (backendError != null && !TextUtils.isEmpty(backendError.getError())) {
                    errorMessage = backendError.getError() + " (Code: " + responseCode + ")";
                } else { errorMessage = defaultMessage + " (Code: " + responseCode + ")";}
            } catch (Exception e) {
                Log.w(fragmentTag, "Could not parse error body as Backend ErrorResponse, trying MessageResponse. Error: " + e.getMessage());
                try {
                    MessageResponse msgResponse = new Gson().fromJson(errorBodyContent, MessageResponse.class);
                    if (msgResponse != null && !TextUtils.isEmpty(msgResponse.getMessage())) {
                        errorMessage = msgResponse.getMessage();
                    } else {errorMessage = defaultMessage + " (Code: " + responseCode + ")";}
                } catch (Exception e2) {
                    Log.w(fragmentTag, "Could not parse error body as MessageResponse either. Error: " + e2.getMessage());
                    errorMessage = defaultMessage + " (Raw: " + errorBodyContent.substring(0, Math.min(100, errorBodyContent.length())) + "... Code: " + responseCode + ")";
                }
            }
        } else if (response != null) { errorMessage = defaultMessage + " (Code: " + responseCode + ")";
        } else {errorMessage = defaultMessage + " (No response from server)";}

        showState(StateType.ERROR, errorMessage, getEmptyStateDefaultErrorText());
    }

    protected void logoutUserFromFragment(String toastMessage) {
        if (getActivity() == null || sessionManager == null) return;
        Log.d(fragmentTag, "Logging out user from fragment...");
        if (!TextUtils.isEmpty(toastMessage)) {
            Toast.makeText(getContext(), toastMessage, Toast.LENGTH_LONG).show();
        }
        sessionManager.clearToken();

        Intent intent = new Intent(getActivity(), WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finishAffinity();
        }
    }

    // showState với message mặc định cho EMPTY và ERROR
    protected void showState(StateType state, @Nullable String customMessage, @NonNull String defaultErrorMsg) {
        if (getView() == null || !isAdded() || progressBar == null || recyclerView == null || tvEmptyState == null || btnRetry == null) {
            Log.w(fragmentTag, "Views not yet initialized or fragment not attached, cannot update state to: " + state);
            return;
        }

        progressBar.setVisibility(state == StateType.LOADING ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(state == StateType.CONTENT ? View.VISIBLE : View.GONE);
        tvEmptyState.setVisibility(state == StateType.EMPTY || state == StateType.ERROR ? View.VISIBLE : View.GONE);
        btnRetry.setVisibility(state == StateType.ERROR ? View.VISIBLE : View.GONE);

        if (state == StateType.EMPTY) {
            tvEmptyState.setText(customMessage != null ? customMessage : getEmptyStateDefaultEmptyText());
        } else if (state == StateType.ERROR) {
            tvEmptyState.setText(customMessage != null ? customMessage : defaultErrorMsg);
        }
    }
    // Phương thức trừu tượng để lớp con cung cấp message mặc định
    protected abstract String getEmptyStateDefaultEmptyText();
    protected abstract String getEmptyStateDefaultErrorText();

    // (Tùy chọn) Helper kiểm tra adapter có rỗng không
    // protected boolean isAdapterEmpty() {
    //     return mediaAdapter != null && mediaAdapter.getItemCount() == 0;
    // }
}