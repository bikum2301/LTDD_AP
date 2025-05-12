// File: src/main/java/com/example/streamapp/fragment/MusicFeedFragment.java
package com.example.streamapp.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.streamapp.R;
import com.example.streamapp.activity.MusicPlayerActivity;
import com.example.streamapp.adapter.MusicAdapter;
import com.example.streamapp.model.MediaResponse;
import com.example.streamapp.model.MessageResponse;
import com.example.streamapp.service.MusicService;

import java.util.ArrayList;
import java.util.List;
// import java.util.stream.Collectors; // Không cần nếu backend đã lọc

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MusicFeedFragment extends BaseFeedFragment<MusicAdapter>
        implements MusicAdapter.OnMusicItemClickListener, MusicAdapter.OnMusicItemOptionsListener {

    // fragmentTag đã được khai báo và gán giá trị trong BaseFeedFragment

    public MusicFeedFragment() {
        // Required empty public constructor
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.fragment_music_feed;
    }

    @Override
    protected int getRecyclerViewId() {
        return R.id.recyclerViewMusic;
    }

    @Override
    protected int getProgressBarId() {
        return R.id.progressBarMusicFeed;
    }

    @Override
    protected int getEmptyStateTextViewId() {
        return R.id.tvEmptyStateMusicFeed;
    }

    @Override
    protected int getRetryButtonId() {
        return R.id.btnRetryMusicFeed;
    }

    @Override
    protected MusicAdapter createAdapter() {
        MusicAdapter adapter = new MusicAdapter();
        adapter.setOnMusicItemClickListener(this);
        adapter.setOnMusicItemOptionsListener(this);
        if (currentUsername != null) {
            adapter.setCurrentUsername(currentUsername);
        }
        return adapter;
    }

    @Override
    protected void fetchData() {
        if (apiService == null) {
            Log.e(fragmentTag, "ApiService is null in fetchData (MusicFeedFragment). Cannot fetch data.");
            if (isAdded() && getView() != null) {
                showState(StateType.ERROR, "Service not available.", getEmptyStateDefaultErrorText());
            }
            return;
        }
        Log.d(fragmentTag, "Fetching public music...");
        showState(StateType.LOADING, null, getEmptyStateDefaultErrorText());

        apiService.getPublicMedia("MUSIC").enqueue(new Callback<List<MediaResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<MediaResponse>> call, @NonNull Response<List<MediaResponse>> response) {
                if (!isAdded() || getActivity() == null || getView() == null) {
                    Log.w(fragmentTag, "MusicFeedFragment not attached or view destroyed, ignoring response.");
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    List<MediaResponse> publicMusic = response.body();
                    Log.d(fragmentTag, "Fetched " + publicMusic.size() + " public music items.");
                    if (mediaAdapter != null) {
                        mediaAdapter.submitList(new ArrayList<>(publicMusic));
                    }
                    showState(publicMusic.isEmpty() ? StateType.EMPTY : StateType.CONTENT,
                            publicMusic.isEmpty() ? getEmptyStateDefaultEmptyText() : null,
                            getEmptyStateDefaultErrorText());
                } else {
                    Log.e(fragmentTag, "Failed to fetch public music. Code: " + response.code());
                    handleApiError(response, "Failed to load music", false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<MediaResponse>> call, @NonNull Throwable t) {
                if (!isAdded() || getActivity() == null || getView() == null) {
                    Log.w(fragmentTag, "MusicFeedFragment not attached or view destroyed, ignoring failure.");
                    return;
                }
                Log.e(fragmentTag, "API Call Failed (getPublicMedia for music): ", t);
                showState(StateType.ERROR, "Error loading music: " + t.getMessage(), getEmptyStateDefaultErrorText());
            }
        });
    }

    @Override
    protected String getEmptyStateDefaultEmptyText() {
        return "No public music available at the moment.";
    }

    @Override
    protected String getEmptyStateDefaultErrorText() {
        return "Oops! Something went wrong while loading music.";
    }


    // --- Implement interface callbacks cho MusicAdapter ---
    @Override
    public void onMusicItemClick(MediaResponse musicItem) {
        if (musicItem == null || TextUtils.isEmpty(musicItem.getUrl())) {
            if (getContext() != null) Toast.makeText(getContext(), "Cannot play: Music URL is missing.", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(fragmentTag, "Music item clicked: " + musicItem.getTitle() + ", URL: " + musicItem.getUrl());

        Intent playIntent = new Intent(getActivity(), MusicService.class);
        playIntent.setAction("ACTION_PLAY");
        playIntent.putExtra("MEDIA_URL", musicItem.getUrl());
        playIntent.putExtra("MEDIA_TITLE", musicItem.getTitle());
        playIntent.putExtra("MEDIA_ARTIST", musicItem.getArtist());
        playIntent.putExtra("ARTWORK_URL", musicItem.getThumbnailUrl());

        if (getActivity() != null) {
            ContextCompat.startForegroundService(getActivity(), playIntent);
        }

        Intent playerActivityIntent = new Intent(getActivity(), MusicPlayerActivity.class);
        playerActivityIntent.putExtra("MEDIA_URL", musicItem.getUrl());
        playerActivityIntent.putExtra("MEDIA_TITLE", musicItem.getTitle());
        playerActivityIntent.putExtra("MEDIA_ARTIST", musicItem.getArtist());
        playerActivityIntent.putExtra("ARTWORK_URL", musicItem.getThumbnailUrl());
        startActivity(playerActivityIntent);
    }

    @Override
    public void onShareClick(MediaResponse musicItem) {
        if (musicItem == null || TextUtils.isEmpty(musicItem.getUrl())) {
            if (getContext() != null) Toast.makeText(getContext(), "Cannot share: Music URL is missing.", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(fragmentTag, "Sharing music: " + musicItem.getTitle());
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        String shareBody = "Listen to this track: " + musicItem.getTitle() + "\n" + musicItem.getUrl();
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Sharing: " + musicItem.getTitle());
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
        try {
            startActivity(Intent.createChooser(shareIntent, "Share music via"));
        } catch (Exception e) {
            Log.e(fragmentTag, "Error starting share intent for music", e);
            if (getContext() != null) Toast.makeText(getContext(), "Could not open share options.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDeleteClick(MediaResponse musicItem, int position) {
        Log.d(fragmentTag, "Delete requested for music: " + musicItem.getTitle());
        if (currentUsername != null && musicItem.getOwnerUsername() != null && currentUsername.equals(musicItem.getOwnerUsername())) {
            showDeleteConfirmationDialog(musicItem);
        } else {
            if (getContext() != null) Toast.makeText(getContext(), "You can only delete your own music.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteConfirmationDialog(MediaResponse musicItem) {
        if (getContext() == null || musicItem == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Music")
                .setMessage("Are you sure you want to delete '" + musicItem.getTitle() + "'?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                    if (musicItem.getId() != null) {
                        deleteMusicItemApiCall(musicItem.getId());
                    } else {
                        if(getContext() != null) Toast.makeText(getContext(), "Cannot delete: Music ID is missing.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void deleteMusicItemApiCall(Long musicIdToDelete) { // Sử dụng tên tham số rõ ràng
        if (apiService == null || sessionManager == null) {
            Log.e(fragmentTag, "ApiService or SessionManager is null in deleteMusicItemApiCall.");
            if(getContext() != null) Toast.makeText(getContext(), "Error: Cannot perform delete operation.", Toast.LENGTH_SHORT).show();
            return;
        }
        String token = sessionManager.getToken();
        if (token == null || token.startsWith("dummy-test-token")) {
            handleApiError(null, "Session invalid. Please login again to delete music.", true);
            return;
        }
        if (musicIdToDelete == null) {
            Log.e(fragmentTag, "Cannot delete music: musicIdToDelete is null.");
            if(getContext() != null) Toast.makeText(getContext(), "Cannot delete: Music ID is null.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(fragmentTag, "Attempting to delete music with ID: " + musicIdToDelete);
        showState(StateType.LOADING, "Deleting music...", getEmptyStateDefaultErrorText());

        // SỬ DỤNG THAM SỐ musicIdToDelete
        apiService.deleteMedia(musicIdToDelete).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(@NonNull Call<MessageResponse> call, @NonNull Response<MessageResponse> response) {
                if (!isAdded() || getActivity() == null || getView() == null) return;

                if (response.isSuccessful()) {
                    String successMsg = "Music deleted successfully.";
                    if (response.code() == 204) {
                        Log.d(fragmentTag, "Music deleted (204 No Content). Refreshing list.");
                    } else if (response.body() != null && response.body().getMessage() != null) {
                        successMsg = response.body().getMessage();
                        Log.d(fragmentTag, "Music deleted (200 OK with message: " + successMsg + "). Refreshing list.");
                    } else {
                        Log.w(fragmentTag, "Music deletion successful (Code: " + response.code() + ") but response body or message is null.");
                    }
                    if(getContext() != null) Toast.makeText(getContext(), successMsg, Toast.LENGTH_SHORT).show();
                    refreshData();
                } else {
                    Log.e(fragmentTag, "Failed to delete music via API. Code: " + response.code());
                    handleApiError(response, "Failed to delete music", false);
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
                Log.e(fragmentTag, "API Call Failed (deleteMedia for music): ", t);
                String errorMsg = "Error deleting music: " + t.getMessage();
                if(getContext() != null) Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
                if (mediaAdapter != null && mediaAdapter.getCurrentList().isEmpty()) {
                    showState(StateType.ERROR, errorMsg, getEmptyStateDefaultErrorText());
                } else {
                    showState(StateType.CONTENT, null, getEmptyStateDefaultErrorText());
                }
            }
        });
    }
}