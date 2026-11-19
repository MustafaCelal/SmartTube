package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AppUpdatePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AboutSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AboutSimpleSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AutoFrameRateSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.BackupSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.SponsorBlockSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.DeArrowSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.GeneralSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.LanguageSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.MainUISettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.PlayerSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.RemoteControlSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.SearchSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.SubtitleSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem.VideoPreset;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class AppDataSourceManager {
    private static AppDataSourceManager sInstance;

    private AppDataSourceManager() {
    }

    public static AppDataSourceManager instance() {
        if (sInstance == null) {
            sInstance = new AppDataSourceManager();
        }

        return sInstance;
    }

    public List<SettingsItem> getSettingItems(Context context) {
        if (context.getResources().getBoolean(R.bool.hometube_rail_actions)) {
            return getHomeTubeSettingItems(context);
        }

        List<SettingsItem> settingItems = new ArrayList<>();

        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_accounts), () -> AccountSettingsPresenter.instance(context).show(), R.drawable.settings_account));
        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_remote_control), () -> RemoteControlSettingsPresenter.instance(context).show(), R.drawable.settings_cast));
        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_language_country), () -> LanguageSettingsPresenter.instance(context).show(), R.drawable.settings_language));
        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_general), () -> GeneralSettingsPresenter.instance(context).show(), R.drawable.settings_app));
        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_main_ui), () -> MainUISettingsPresenter.instance(context).show(), R.drawable.settings_main_ui));
        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_player), () -> PlayerSettingsPresenter.instance(context).show(), R.drawable.settings_player));
        // Don't add afr support check here.
        // Users want even fake afr settings.
        settingItems.add(new SettingsItem(
                context.getString(R.string.auto_frame_rate), () -> AutoFrameRateSettingsPresenter.instance(context).show(), R.drawable.settings_afr));
        settingItems.add(new SettingsItem(
                context.getString(R.string.subtitle_category_title), () -> SubtitleSettingsPresenter.instance(context).show(), R.drawable.settings_subtitles));
        settingItems.add(new SettingsItem(
                context.getString(R.string.settings_search), () -> SearchSettingsPresenter.instance(context).show(), R.drawable.settings_search));
        settingItems.add(new SettingsItem(
                context.getString(R.string.content_block_provider), () -> SponsorBlockSettingsPresenter.instance(context).show(), R.drawable.settings_block));
        settingItems.add(new SettingsItem(
                context.getString(R.string.dearrow_provider), () -> DeArrowSettingsPresenter.instance(context).show(), R.drawable.settings_dearrow));
        settingItems.add(new SettingsItem(
                context.getString(R.string.app_backup_restore), () -> BackupSettingsPresenter.instance(context).show(), R.drawable.settings_backup));

        if (Helpers.equalsAny(context.getPackageName(), Utils.KNOWN_PACKAGES)) {
            settingItems.add(new SettingsItem(
                    context.getString(R.string.settings_about), () -> AboutSettingsPresenter.instance(context).show(), R.drawable.settings_about));
        } else {
            settingItems.add(new SettingsItem(
                    context.getString(R.string.settings_about), () -> AboutSimpleSettingsPresenter.instance(context).show(), R.drawable.settings_about));
        }

        return settingItems;
    }

    /**
     * HomeTube: most used settings first, rarely used ones grouped under "Advanced", outline icons
     */
    private List<SettingsItem> getHomeTubeSettingItems(Context context) {
        List<SettingsItem> items = new ArrayList<>();

        // New versions come from the HomeTube releases
        items.add(new SettingsItem(context.getString(R.string.hometube_update),
                () -> AppUpdatePresenter.instance(context).start(true), R.drawable.hts_update));
        items.add(new SettingsItem(context.getString(R.string.settings_accounts),
                () -> AccountSettingsPresenter.instance(context).show(), R.drawable.hts_account));
        items.add(new SettingsItem(context.getString(R.string.settings_player),
                () -> PlayerSettingsPresenter.instance(context).show(), R.drawable.hts_player));
        items.add(new SettingsItem(context.getString(R.string.subtitle_category_title),
                () -> SubtitleSettingsPresenter.instance(context).show(), R.drawable.hts_subtitles));
        items.add(new SettingsItem(context.getString(R.string.settings_main_ui),
                () -> MainUISettingsPresenter.instance(context).show(), R.drawable.hts_ui));
        items.add(new SettingsItem(context.getString(R.string.settings_language_country),
                () -> LanguageSettingsPresenter.instance(context).show(), R.drawable.hts_language));
        items.add(new SettingsItem(context.getString(R.string.settings_search),
                () -> SearchSettingsPresenter.instance(context).show(), R.drawable.hts_search));
        items.add(new SettingsItem(context.getString(R.string.content_block_provider),
                () -> SponsorBlockSettingsPresenter.instance(context).show(), R.drawable.hts_block));
        items.add(new SettingsItem(context.getString(R.string.app_backup_restore),
                () -> BackupSettingsPresenter.instance(context).show(), R.drawable.hts_backup));
        items.add(new SettingsItem(context.getString(R.string.settings_general),
                () -> GeneralSettingsPresenter.instance(context).show(), R.drawable.hts_general));
        items.add(new SettingsItem(context.getString(R.string.hometube_advanced),
                () -> showAdvancedSettings(context), R.drawable.hts_advanced));
        items.add(new SettingsItem(context.getString(R.string.settings_about),
                () -> AboutSettingsPresenter.instance(context).show(), R.drawable.hts_about));

        return items;
    }

    private static void showAdvancedSettings(Context context) {
        AppDialogPresenter dialog = AppDialogPresenter.instance(context);

        addAdvancedItem(dialog, context.getString(R.string.settings_remote_control),
                () -> RemoteControlSettingsPresenter.instance(context).show());
        addAdvancedItem(dialog, context.getString(R.string.auto_frame_rate),
                () -> AutoFrameRateSettingsPresenter.instance(context).show());
        addAdvancedItem(dialog, context.getString(R.string.dearrow_provider),
                () -> DeArrowSettingsPresenter.instance(context).show());

        dialog.showDialog(context.getString(R.string.hometube_advanced));
    }

    private static void addAdvancedItem(AppDialogPresenter dialog, String title, Runnable show) {
        dialog.appendSingleButton(UiOptionItem.from(title, option -> {
            dialog.closeDialog();
            Utils.postDelayed(show, 100); // let the dialog close first
        }));
    }

    public VideoPreset[] getVideoPresets() {
        VideoPreset[] presets = {
                new VideoPreset("144p     30fps    avc", "256,144,30,avc"),
                new VideoPreset("144p     30fps    vp9", "256,144,30,vp9"),
                new VideoPreset("144p     30fps    av01", "256,144,30,av01"),
                new VideoPreset("240p     30fps    avc", "320,240,30,avc"),
                new VideoPreset("240p     30fps    vp9", "320,240,30,vp9"),
                new VideoPreset("240p     30fps    av01", "320,240,30,av01"),
                new VideoPreset("360p     30fps    avc", "640,360,30,avc"),
                new VideoPreset("360p     30fps    vp9", "640,360,30,vp9"),
                new VideoPreset("360p     30fps    av01", "640,360,30,av01"),
                new VideoPreset("360p     60fps    avc", "640,360,60,avc"),
                new VideoPreset("360p     60fps    vp9", "640,360,60,vp9"),
                new VideoPreset("360p     60fps    av01", "640,360,60,av01"),
                new VideoPreset("480p     30fps    avc", "854,480,30,avc"),
                new VideoPreset("480p     30fps    vp9", "854,480,30,vp9"),
                new VideoPreset("480p     30fps    av01", "854,480,30,av01"),
                new VideoPreset("480p     60fps    avc", "854,480,60,avc"),
                new VideoPreset("480p     60fps    vp9", "854,480,60,vp9"),
                new VideoPreset("480p     60fps    av01", "854,480,60,av01"),
                new VideoPreset("720p     30fps    avc", "1280,720,30,avc"),
                new VideoPreset("720p     60fps    avc", "1280,720,60,avc"),
                new VideoPreset("720p     30fps    vp9", "1280,720,30,vp9"),
                new VideoPreset("720p     60fps    vp9", "1280,720,60,vp9"),
                new VideoPreset("720p     30fps    av01", "1280,720,30,av01"),
                new VideoPreset("720p     60fps    av01", "1280,720,60,av01"),
                new VideoPreset("720p     30fps    av01+hdr", "1280,720,30,av01.hdr"),
                new VideoPreset("720p     60fps    av01+hdr", "1280,720,60,av01.hdr"),
                new VideoPreset("1080p    30fps    avc", "1920,1080,30,avc"),
                new VideoPreset("1080p    60fps    avc", "1920,1080,60,avc"),
                new VideoPreset("1080p    30fps    vp9", "1920,1080,30,vp9"),
                new VideoPreset("1080p    60fps    vp9", "1920,1080,60,vp9"),
                new VideoPreset("1080p    30fps    vp9+hdr", "1920,1080,30,vp9.2"),
                new VideoPreset("1080p    60fps    vp9+hdr", "1920,1080,60,vp9.2"),
                new VideoPreset("1080p    30fps    av01", "1920,1080,30,av01"),
                new VideoPreset("1080p    60fps    av01", "1920,1080,60,av01"),
                new VideoPreset("1080p    30fps    av01+hdr", "1920,1080,30,av01.hdr"),
                new VideoPreset("1080p    60fps    av01+hdr", "1920,1080,60,av01.hdr"),
                new VideoPreset("(2K) 1440p    30fps    vp9", "2560,1440,30,vp9"),
                new VideoPreset("(2K) 1440p    60fps    vp9", "2560,1440,60,vp9"),
                new VideoPreset("(2K) 1440p    30fps    vp9+hdr", "2560,1440,30,vp9.2"),
                new VideoPreset("(2K) 1440p    60fps    vp9+hdr", "2560,1440,60,vp9.2"),
                new VideoPreset("(2K) 1440p    30fps    av01", "2560,1440,30,av01"),
                new VideoPreset("(2K) 1440p    60fps    av01", "2560,1440,60,av01"),
                new VideoPreset("(2K) 1440p    30fps    av01+hdr", "2560,1440,30,av01.hdr"),
                new VideoPreset("(2K) 1440p    60fps    av01+hdr", "2560,1440,60,av01.hdr"),
                new VideoPreset("(4K) 2160p    30fps    vp9", "3840,2160,30,vp9"),
                new VideoPreset("(4K) 2160p    60fps    vp9", "3840,2160,60,vp9"),
                new VideoPreset("(4K) 2160p    30fps    vp9+hdr", "3840,2160,30,vp9.2"),
                new VideoPreset("(4K) 2160p    60fps    vp9+hdr", "3840,2160,60,vp9.2"),
                new VideoPreset("(4K) 2160p    30fps    av01", "3840,2160,30,av01"),
                new VideoPreset("(4K) 2160p    60fps    av01", "3840,2160,60,av01"),
                new VideoPreset("(4K) 2160p    30fps    av01+hdr", "3840,2160,30,av01.hdr"),
                new VideoPreset("(4K) 2160p    60fps    av01+hdr", "3840,2160,60,av01.hdr"),
                new VideoPreset("(8K) 4320p    30fps    vp9", "7680,4320,30,vp9"),
                new VideoPreset("(8K) 4320p    60fps    vp9", "7680,4320,60,vp9"),
                new VideoPreset("(8K) 4320p    30fps    vp9+hdr", "7680,4320,30,vp9.2"),
                new VideoPreset("(8K) 4320p    60fps    vp9+hdr", "7680,4320,60,vp9.2"),
                new VideoPreset("(8K) 4320p    30fps    av01", "7680,4320,30,av01"),
                new VideoPreset("(8K) 4320p    60fps    av01", "7680,4320,60,av01"),
                new VideoPreset("(8K) 4320p    30fps    av01+hdr", "7680,4320,30,av01.hdr"),
                new VideoPreset("(8K) 4320p    60fps    av01+hdr", "7680,4320,60,av01.hdr"),
                //new VideoPreset("Adaptive", null)
        };

        return presets;
    }
}
