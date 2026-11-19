package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build.VERSION;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class SettingsCardPresenter extends Presenter {
    private int mDefaultBackgroundColor;
    private int mDefaultTextColor;
    private int mSelectedBackgroundColor;
    private int mSelectedTextColor;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();

        mDefaultBackgroundColor =
                ContextCompat.getColor(context, Helpers.getThemeAttr(context, R.attr.cardDefaultBackground));
        mDefaultTextColor =
                ContextCompat.getColor(context, R.color.card_default_text);
        mSelectedBackgroundColor =
                ContextCompat.getColor(context, R.color.card_selected_background_white);
        mSelectedTextColor =
                ContextCompat.getColor(context, R.color.card_selected_text_grey);

        @SuppressLint("InflateParams")
        View container = LayoutInflater.from(context).inflate(R.layout.settings_card, null);

        if (Helpers.getThemeAttr(context, R.attr.headerFocusPill) != 0) {
            applyHomeTubeTile(container);
            return new ViewHolder(container);
        }

        container.setBackgroundColor(mDefaultBackgroundColor);
        //if (VERSION.SDK_INT >= 23 && MainUIData.instance(context).isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS)) {
        //    container.setForeground(ContextCompat.getDrawable(context, R.drawable.lb_card_outline));
        //}

        TextView textView = container.findViewById(R.id.settings_title);
        textView.setBackgroundColor(mDefaultBackgroundColor);
        textView.setTextColor(mDefaultTextColor);

        ViewUtil.setTextScrollSpeed(textView, getCardTextScrollSpeed(context));

        container.setOnFocusChangeListener((v, hasFocus) -> {
            int backgroundColor = hasFocus ? mSelectedBackgroundColor : mDefaultBackgroundColor;
            int textColor = hasFocus ? mSelectedTextColor : mDefaultTextColor;
            
            textView.setBackgroundColor(backgroundColor);
            textView.setTextColor(textColor);

            if (hasFocus) {
                ViewUtil.enableMarquee(textView);
            } else {
                ViewUtil.disableMarquee(textView);
            }
        });

        return new ViewHolder(container);
    }

    /**
     * HomeTube: dark rounded tile, small outline icon at the top, name below (left aligned).
     * Focused tile turns white like the sidebar pill.
     */
    private static void applyHomeTubeTile(View container) {
        Context context = container.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int tileColor = 0xFF1E1E1E;
        int textColor = 0xFFF1F1F1;

        android.graphics.drawable.GradientDrawable normal = new android.graphics.drawable.GradientDrawable();
        normal.setColor(tileColor);
        normal.setCornerRadius(16 * density);
        android.graphics.drawable.GradientDrawable focused = new android.graphics.drawable.GradientDrawable();
        focused.setColor(Color.WHITE);
        focused.setCornerRadius(16 * density);

        int pad = (int) (20 * density);
        container.setPadding(pad, pad, pad, (int) (16 * density));
        container.setBackground(normal);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                context.getResources().getDimensionPixelSize(R.dimen.settings_card_width), (int) (124 * density)));

        ImageView imageView = container.findViewById(R.id.settings_image);
        int iconSize = (int) (36 * density);
        android.widget.LinearLayout.LayoutParams iconParams = new android.widget.LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = android.view.Gravity.START;
        imageView.setLayoutParams(iconParams);
        imageView.setColorFilter(textColor, PorterDuff.Mode.SRC_IN);

        TextView textView = container.findViewById(R.id.settings_title);
        android.widget.LinearLayout.LayoutParams textParams = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        textView.setLayoutParams(textParams);
        textView.setPadding(0, 0, 0, 0);
        textView.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.START);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        textView.setSingleLine(false);
        textView.setMaxLines(2);
        textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        textView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        textView.setTextColor(textColor);
        textView.setBackground(null);

        container.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(hasFocus ? focused : normal);
            int color = hasFocus ? Color.BLACK : textColor;
            textView.setTextColor(color);
            imageView.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        });
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        SettingsItem settingsItem = (SettingsItem) item;

        TextView textView = viewHolder.view.findViewById(R.id.settings_title);

        textView.setText(settingsItem.title);

        if (settingsItem.imageResId > 0) {
            Context context = viewHolder.view.getContext();
            ImageView imageView = viewHolder.view.findViewById(R.id.settings_image);
            imageView.setImageDrawable(ContextCompat.getDrawable(context, settingsItem.imageResId));
            imageView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }

    protected boolean isCardTextAutoScrollEnabled(Context context) {
        return MainUIData.instance(context).isCardTextAutoScrollEnabled();
    }

    protected float getCardTextScrollSpeed(Context context) {
        return MainUIData.instance(context).getCardTextScrollSpeed();
    }
}
