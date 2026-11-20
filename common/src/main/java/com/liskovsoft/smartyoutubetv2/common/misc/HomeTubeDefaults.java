package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.locale.LocaleUpdater;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

/**
 * HomeTube: one-time defaults, applied on start (each step only once per install),
 * so users can still change them later in settings.<br/>
 * Steps also run on existing installs after an update. They only touch UI settings,
 * never the account, history or visitor data.
 */
public final class HomeTubeDefaults {
    private static final String PREFS_NAME = "hometube_defaults";
    private static final String KEY_APPLIED_VERSION = "applied_version";
    private static final int VERSION = 7;

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
        int appliedVersion = prefs.getInt(KEY_APPLIED_VERSION, 0);

        if (appliedVersion >= VERSION) {
            return;
        }

        if (appliedVersion < 1) {
            // Hide Shorts everywhere (home shelf, search, subscriptions, history, channels, trending, news)
            MediaServiceData.instance().setContentHidden(MediaServiceData.CONTENT_SHORTS_ALL, true);
        }

        if (appliedVersion < 2) {
            applyYouTubeLikeUi(context);
        }

        if (appliedVersion < 3) {
            // Static card titles (no marquee), like YouTube
            MainUIData.instance(context).setCardTextAutoScrollEnabled(false);
        }

        if (appliedVersion < 4) {
            // Region follows the system (TV settings) instead of a manually picked country.
            // The UI language choice is kept.
            LocaleUpdater localeUpdater = new LocaleUpdater(context);
            if (!localeUpdater.getPreferredCountry().isEmpty()) {
                localeUpdater.setPreferredCountry("");
                localeUpdater.update();
            }
        }

        if (appliedVersion < 6) {
            // Updates: no check on start, only when the user presses Settings > Update
            new AppUpdateChecker(context, null).setUpdateCheckEnabled(false);
        }

        if (appliedVersion < 7) {
            // Player menus (speed, quality...) open over the video instead of shrinking it
            com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData.instance(context).setDontResizeVideoToFitDialogEnabled(true);
        }

        prefs.edit().putInt(KEY_APPLIED_VERSION, VERSION).apply();
    }

    /**
     * YouTube TV-like UI: short sidebar, no clocks or tech info, minimal player controls.
     */
    private static void applyYouTubeLikeUi(Context context) {
        SidebarService sidebar = SidebarService.instance(context);
        int[] hidden = {
                MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_KIDS_HOME, MediaGroup.TYPE_SPORTS, MediaGroup.TYPE_LIVE,
                MediaGroup.TYPE_GAMING, MediaGroup.TYPE_NEWS, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_CHANNEL_UPLOADS,
                MediaGroup.TYPE_MY_VIDEOS, MediaGroup.TYPE_TRENDING, MediaGroup.TYPE_NOTIFICATIONS,
                MediaGroup.TYPE_BLOCKED_CHANNELS, MediaGroup.TYPE_PLAYBACK_QUEUE
        };
        for (int sectionId : hidden) {
            sidebar.enableSection(sectionId, false);
        }
        int[] shown = {
                MediaGroup.TYPE_HOME, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY,
                MediaGroup.TYPE_USER_PLAYLISTS, MediaGroup.TYPE_SETTINGS
        };
        for (int sectionId : shown) {
            sidebar.enableSection(sectionId, true);
        }

        GeneralData.instance(context).setGlobalClockEnabled(false);

        PlayerData playerData = PlayerData.instance(context);
        playerData.setClockEnabled(false);
        playerData.setRemainingTimeEnabled(false);
        playerData.setQualityInfoEnabled(false);

        PlayerTweaksData tweaks = PlayerTweaksData.instance(context);
        tweaks.setPlayerButtonDisabled(~0);
        tweaks.setPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_DEFAULT);
    }
}
