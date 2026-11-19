package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

/**
 * HomeTube: one-time defaults that live in shared modules (MediaServiceCore),
 * applied on the first start so users can still change them later in settings.
 */
public final class HomeTubeDefaults {
    private static final String PREFS_NAME = "hometube_defaults";
    private static final String KEY_APPLIED_VERSION = "applied_version";
    private static final int VERSION = 1;

    private HomeTubeDefaults() {
    }

    /**
     * Call after GlobalPreferences is initialized.
     */
    public static void applyOnce(Context context) {
        if (context == null) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getInt(KEY_APPLIED_VERSION, 0) >= VERSION) {
            return;
        }

        // Hide Shorts everywhere (home shelf, search, subscriptions, history, channels, trending, news)
        MediaServiceData.instance().setContentHidden(MediaServiceData.CONTENT_SHORTS_ALL, true);

        prefs.edit().putInt(KEY_APPLIED_VERSION, VERSION).apply();
    }
}
