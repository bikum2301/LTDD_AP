// File: src/main/java/com/example/streamapp/adapter/MediaFeedPagerAdapter.java
package com.example.streamapp.adapter; // Hoặc package của bạn

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity; // Hoặc FragmentManager + Lifecycle nếu dùng trong Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter;

// Sửa import nếu bạn đặt Fragment ở package khác
import com.example.streamapp.fragment.MusicFeedFragment;
import com.example.streamapp.fragment.VideoFeedFragment;


public class MediaFeedPagerAdapter extends FragmentStateAdapter {

    private static final int NUM_TABS = 2;

    public MediaFeedPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new VideoFeedFragment();
            case 1:
                return new MusicFeedFragment();
            default:
                return new VideoFeedFragment(); // Hoặc throw exception
        }
    }

    @Override
    public int getItemCount() {
        return NUM_TABS;
    }
}