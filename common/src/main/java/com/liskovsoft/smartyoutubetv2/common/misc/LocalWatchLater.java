package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * HomeTube: "Watch later" list kept on the device, for use without a YouTube account.<br/>
 * Stored in its own shared prefs file, so the app backup includes it.
 */
public final class LocalWatchLater {
    private static final String TAG = LocalWatchLater.class.getSimpleName();
    /** Sidebar section id of the list */
    public static final int SECTION_ID = -2003;
    private static final String PREFS_NAME = "hometube_watch_later";
    private static final String KEY_VIDEOS = "videos";
    private static final int MAX_VIDEOS = 500;
    @SuppressLint("StaticFieldLeak")
    private static LocalWatchLater sInstance;
    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final List<Video> mVideos = new ArrayList<>();

    private LocalWatchLater(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        load();
    }

    public static LocalWatchLater instance(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new LocalWatchLater(context);
        }

        return sInstance;
    }

    /**
     * Local library is used in HomeTube while no YouTube account is signed in.
     */
    public static boolean isEnabled(@Nullable Context context) {
        return context != null && context.getResources().getBoolean(R.bool.hometube_rail_actions) &&
                !YouTubeSignInService.instance().isSigned();
    }

    public synchronized boolean contains(@Nullable Video video) {
        return video != null && indexOf(video.videoId) != -1;
    }

    /**
     * @return true if the video was added, false if it was removed
     */
    public synchronized boolean toggle(@NonNull Video video) {
        if (contains(video)) {
            remove(video);
            return false;
        }

        add(video);
        return true;
    }

    /**
     * Toggle with a short message. Used by the video menu and the player's playlist button.
     * @return true if the video was added
     */
    public boolean toggleWithMessage(@NonNull Video video) {
        boolean added = toggle(video);
        com.liskovsoft.smartyoutubetv2.common.misc.MessageHelpers.showMessage(mContext,
                added ? R.string.hometube_watch_later_added : R.string.hometube_watch_later_removed);
        return added;
    }

    public synchronized void add(@NonNull Video video) {
        if (video.videoId == null) {
            return;
        }

        removeInt(video.videoId);

        // Own copy without the context it came from (playlist, section, etc)
        Video copy = Video.fromString(video.toString());
        if (copy == null) {
            return;
        }
        copy.playlistId = null;
        copy.playlistParams = null;
        copy.playlistIndex = -1;

        mVideos.add(0, copy);

        while (mVideos.size() > MAX_VIDEOS) {
            mVideos.remove(mVideos.size() - 1);
        }

        persist();
    }

    public synchronized void remove(@NonNull Video video) {
        if (removeInt(video.videoId)) {
            persist();
        }
    }

    public synchronized List<Video> getVideos() {
        List<Video> result = new ArrayList<>();
        for (Video video : mVideos) {
            // fresh objects: the UI may change them (watched percent etc)
            Video copy = Video.fromString(video.toString());
            if (copy != null) {
                result.add(copy);
            }
        }
        return result;
    }

    public synchronized boolean isEmpty() {
        return mVideos.isEmpty();
    }

    private int indexOf(@Nullable String videoId) {
        if (videoId == null) {
            return -1;
        }

        for (int i = 0; i < mVideos.size(); i++) {
            if (Helpers.equals(mVideos.get(i).videoId, videoId)) {
                return i;
            }
        }

        return -1;
    }

    private boolean removeInt(@Nullable String videoId) {
        boolean removed = false;
        Iterator<Video> iterator = mVideos.iterator();
        while (iterator.hasNext()) {
            if (Helpers.equals(iterator.next().videoId, videoId)) {
                iterator.remove();
                removed = true;
            }
        }
        return removed;
    }

    private void load() {
        String data = mPrefs.getString(KEY_VIDEOS, null);

        if (data == null) {
            return;
        }

        try {
            JSONArray array = new JSONArray(data);
            for (int i = 0; i < array.length(); i++) {
                Video video = Video.fromString(array.optString(i, null));
                if (video != null && video.videoId != null) {
                    mVideos.add(video);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Can't read the list: %s", e.getMessage());
        }
    }

    private void persist() {
        JSONArray array = new JSONArray();
        for (Video video : mVideos) {
            array.put(video.toString());
        }
        mPrefs.edit().putString(KEY_VIDEOS, array.toString()).apply();
    }
}
