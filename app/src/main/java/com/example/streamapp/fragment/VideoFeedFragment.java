// File: src/main/java/com/example/streamapp/fragment/VideoFeedFragment.java
package com.example.streamapp.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.streamapp.R;
import com.example.streamapp.activity.MediaDetailActivity;
import com.example.streamapp.adapter.VideoAdapter;
import com.example.streamapp.model.MediaResponse;
import com.example.streamapp.model.MessageResponse;
// Import các lớp cần thiết từ BaseFeedFragment nếu có sử dụng trực tiếp (ví dụ: StateType)
// Hoặc không cần nếu BaseFeedFragment đã xử lý hết.

import java.util.ArrayList;
import java.util.List;
// import java.util.stream.Collectors; // Không cần nếu backend đã lọc

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VideoFeedFragment extends BaseFeedFragment<VideoAdapter>
        implements VideoAdapter.OnVideoItemClickListener, VideoAdapter.OnVideoItemOptionsListener {

    // fragmentTag đã được khai báo và gán giá trị trong BaseFeedFragment

    public VideoFeedFragment() {
        // Required empty public constructor
    }

    // Các phương thức getLayoutResourceId, getRecyclerViewId, getProgressBarId,
    // getEmptyStateTextViewId, getRetryButtonId đã được implement ở các bước trước.
    // Chúng ta chỉ cần đảm bảo chúng trả về đúng ID của layout fragment_video_feed.xml

    @Override
    protected int getLayoutResourceId() {
        return R.layout.fragment_video_feed;
    }

    @Override
    protected int getRecyclerViewId() {
        return R.id.recyclerViewVideos;
    }

    @Override
    protected int getProgressBarId() {
        return R.id.progressBarVideoFeed;
    }

    @Override
    protected int getEmptyStateTextViewId() {
        return R.id.tvEmptyStateVideoFeed;
    }

    @Override
    protected int getRetryButtonId() {
        return R.id.btnRetryVideoFeed;
    }

    @Override
    protected VideoAdapter createAdapter() {
        VideoAdapter adapter = new VideoAdapter();
        adapter.setOnVideoItemClickListener(this);
        adapter.setOnVideoItemOptionsListener(this);
        // currentUsername được kế thừa từ BaseFeedFragment và được cập nhật trong BaseFeedFragment.refreshData()
        if (currentUsername != null) {
            adapter.setCurrentUsername(currentUsername);
        }
        return adapter;
    }

    @Override
    protected void fetchData() {
        // apiService đã được khởi tạo trong BaseFeedFragment.onCreate()
        if (apiService == null) {
            Log.e(fragmentTag, "ApiService is null in fetchData (VideoFeedFragment). Cannot fetch data.");
            if (isAdded() && getView() != null) {
                showState(StateType.ERROR, "Service not available.", getEmptyStateDefaultErrorText());
            }
            return;
        }
        Log.d(fragmentTag, "Fetching public videos...");
        showState(StateType.LOADING, null, getEmptyStateDefaultErrorText());

        // Giả sử backend API /api/media/public?type=VIDEO đã hoạt động
        apiService.getPublicMedia("VIDEO").enqueue(new Callback<List<MediaResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<MediaResponse>> call, @NonNull Response<List<MediaResponse>> response) {
                if (!isAdded() || getActivity() == null || getView() == null) {
                    Log.w(fragmentTag, "VideoFeedFragment not attached or view destroyed, ignoring response.");
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    List<MediaResponse> publicVideos = response.body(); // API đã lọc theo type "VIDEO"
                    Log.d(fragmentTag, "Fetched " + publicVideos.size() + " public videos.");
                    if (mediaAdapter != null) {
                        mediaAdapter.submitList(new ArrayList<>(publicVideos)); // Luôn tạo list mới cho ListAdapter
                    }
                    showState(publicVideos.isEmpty() ? StateType.EMPTY : StateType.CONTENT,
                            publicVideos.isEmpty() ? getEmptyStateDefaultEmptyText() : null,
                            getEmptyStateDefaultErrorText());
                } else {
                    Log.e(fragmentTag, "Failed to fetch public videos. Code: " + response.code());
                    handleApiError(response, "Failed to load videos", false); // handleApiError từ BaseFeedFragment
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<MediaResponse>> call, @NonNull Throwable t) {
                if (!isAdded() || getActivity() == null || getView() == null) {
                    Log.w(fragmentTag, "VideoFeedFragment not attached or view destroyed, ignoring failure.");
                    return;
                }
                Log.e(fragmentTag, "API Call Failed (getPublicMedia for videos): ", t);
                showState(StateType.ERROR, "Error loading videos: " + t.getMessage(), getEmptyStateDefaultErrorText());
            }
        });
    }

    @Override
    protected String getEmptyStateDefaultEmptyText() {
        return "No public videos available at the moment.";
    }

    @Override
    protected String getEmptyStateDefaultErrorText() {
        return "Oops! Something went wrong while loading videos.";
    }


    // --- Implement interface callbacks cho VideoAdapter ---
    @Override
    public void onVideoItemClick(MediaResponse videoItem) {
        if (videoItem == null || videoItem.getId() == null) {
            if (getContext() != null) Toast.makeText(getContext(), "Cannot view details: Invalid video data.", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(fragmentTag, "Video item clicked: " + videoItem.getTitle());
        Intent detailIntent = new Intent(getActivity(), MediaDetailActivity.class);
        detailIntent.putExtra("MEDIA_ID", videoItem.getId());
        startActivity(detailIntent);
    }

    private void showDeleteConfirmationDialog(MediaResponse videoItem) {
        if (getContext() == null || videoItem == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Video")
                .setMessage("Are you sure you want to delete '" + videoItem.getTitle() + "'?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                    if (videoItem.getId() != null) {
                        deleteVideoItemApiCall(videoItem.getId());
                    } else {
                        if(getContext() != null) Toast.makeText(getContext(), "Cannot delete: Video ID is missing.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void deleteVideoItemApiCall(Long videoIdToDelete) { // Sử dụng tên tham số rõ ràng
        // apiService và sessionManager đã được BaseFeedFragment khởi tạo
        if (apiService == null || sessionManager == null) {
            Log.e(fragmentTag, "ApiService or SessionManager is null in deleteVideoItemApiCall.");
            if (getContext() != null) Toast.makeText(getContext(), "Error: Cannot perform delete operation.", Toast.LENGTH_SHORT).show();
            return;
        }
        String token = sessionManager.getToken();
        if (token == null || token.startsWith("dummy-test-token")) {
            handleApiError(null, "Session invalid. Please login again to delete video.", true);
            return;
        }
        if (videoIdToDelete == null) {
            Log.e(fragmentTag, "Cannot delete video: videoIdToDelete is null.");
            if(getContext() != null) Toast.makeText(getContext(), "Cannot delete: Video ID is null.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(fragmentTag, "Attempting to delete video with ID: " + videoIdToDelete);
        showState(StateType.LOADING, "Deleting video...", getEmptyStateDefaultErrorText());

        // SỬ DỤNG THAM SỐ videoIdToDelete
        apiService.deleteMedia(videoIdToDelete).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(@NonNull Call<MessageResponse> call, @NonNull Response<MessageResponse> response) {
                if (!isAdded() || getActivity() == null || getView() == null) return;

                if (response.isSuccessful()) {
                    String successMsg = "Video deleted successfully.";
                    if (response.code() == 204) {
                        Log.d(fragmentTag, "Video deleted (204 No Content). Refreshing list.");
                    } else if (response.body() != null && response.body().getMessage() != null) {
                        successMsg = response.body().getMessage();
                        Log.d(fragmentTag, "Video deleted (200 OK with message: " + successMsg + "). Refreshing list.");
                    } else {
                        Log.w(fragmentTag, "Video deletion successful (Code: " + response.code() + ") but response body or message is null.");
                    }
                    if(getContext() != null) Toast.makeText(getContext(), successMsg, Toast.LENGTH_SHORT).show();
                    refreshData(); // refreshData() được kế thừa từ BaseFeedFragment
                } else {
                    Log.e(fragmentTag, "Failed to delete video via API. Code: " + response.code());
                    handleApiError(response, "Failed to delete video", false);
                    if (mediaAdapter != null && mediaAdapter.getCurrentList().isEmpty()) {
                        showState(StateType.EMPTY, getEmptyStateDefaultEmptyText(), getEmptyStateDefaultErrorText());
                    } else {
                        showState(StateType.CONTENT, null, getEmptyStateDefaultErrorText());
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<MessageResponse> call, @NonNull Throwable t) {
                if (!isAdded() || getActivity() == null || getView() == null) return;
                Log.e(fragmentTag, "API Call Failed (deleteMedia for video): ", t);
                String errorMsg = "Error deleting video: " + t.getMessage();
                if(getContext() != null) Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();

                if (mediaAdapter != null && mediaAdapter.getCurrentList().isEmpty()) {
                    showState(StateType.ERROR, errorMsg, getEmptyStateDefaultErrorText());
                } else {
                    showState(StateType.CONTENT, null, getEmptyStateDefaultErrorText()); // Giữ lại content nếu có
                }
            }
        });
    }

    @Override
    public void onShareClick(MediaResponse videoItem) {
        if (videoItem == null || TextUtils.isEmpty(videoItem.getUrl())) {
            if (getContext() != null) Toast.makeText(getContext(), "Cannot share: Video URL is missing.", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(fragmentTag, "Sharing video: " + videoItem.getTitle());
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        String shareBody = "Check out this video: " + videoItem.getTitle() + "\n" + videoItem.getUrl();
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Sharing: " + videoItem.getTitle());
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
        try {
            startActivity(Intent.createChooser(shareIntent, "Share video via"));
        } catch (Exception e) {
            Log.e(fragmentTag, "Error starting share intent for video", e);
            if (getContext() != null) Toast.makeText(getContext(), "Could not open share options.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDeleteClick(MediaResponse videoItem, int position) {
        Log.d(fragmentTag, "Delete requested for video: " + videoItem.getTitle());
        // currentUsername được kế thừa từ BaseFeedFragment
        if (currentUsername != null && videoItem.getOwnerUsername() != null && currentUsername.equals(videoItem.getOwnerUsername())) {
            showDeleteConfirmationDialog(videoItem);
        } else {
            if (getContext() != null) Toast.makeText(getContext(), "You can only delete your own videos.", Toast.LENGTH_SHORT).show();
        }
    }
}