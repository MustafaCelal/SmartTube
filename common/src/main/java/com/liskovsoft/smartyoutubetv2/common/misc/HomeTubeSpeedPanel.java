package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerManager;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * HomeTube: playback speed panel at the bottom of the player.<br/>
 * A bar changed with left/right in 0.05 steps, and quick buttons (Normal, 1.25x...) below it.
 * The speed is applied immediately, the video keeps playing above the panel.
 */
public final class HomeTubeSpeedPanel {
    private static final int MIN_STEP = 5;   // 0.25x
    private static final int MAX_STEP = 40;  // 2.0x
    private static final float STEP = 0.05f;
    private static final float[] PRESETS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    private static final int ACCENT = 0xFFFF0033;

    private final Context mContext;
    private final PlayerManager mPlayer;
    private final float mDensity;
    private Dialog mDialog;
    private SpeedBar mBar;
    private TextView mValue;
    private final List<TextView> mPresetButtons = new ArrayList<>();

    private HomeTubeSpeedPanel(Context context, PlayerManager player) {
        mContext = context;
        mPlayer = player;
        mDensity = context.getResources().getDisplayMetrics().density;
    }

    public static void show(@Nullable Context context, @Nullable PlayerManager player, @Nullable Runnable onClose) {
        if (!(context instanceof Activity) || player == null || ((Activity) context).isFinishing()) {
            return;
        }

        new HomeTubeSpeedPanel(context, player).showInt(onClose);
    }

    private void showInt(@Nullable Runnable onClose) {
        mDialog = new Dialog(mContext);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setContentView(createContent());
        mDialog.setCanceledOnTouchOutside(true);
        if (onClose != null) {
            mDialog.setOnDismissListener(dialog -> onClose.run());
        }

        Window window = mDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); // keep the video bright
        }

        updateUi(toStep(mPlayer.getSpeed()));
        mPlayer.showOverlay(false); // the player controls would show through behind the panel
        mDialog.show();
        mBar.requestFocus();
    }

    private View createContent() {
        LinearLayout panel = new LinearLayout(mContext);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(48), dp(24), dp(48), dp(28));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF0181818);
        float radius = dp(20);
        background.setCornerRadii(new float[] {radius, radius, radius, radius, 0, 0, 0, 0});
        panel.setBackground(background);

        // Title and the current value
        LinearLayout header = new LinearLayout(mContext);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(mContext);
        title.setText(R.string.hometube_speed);
        title.setTextColor(0xFFAAAAAA);
        title.setTextSize(18);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mValue = new TextView(mContext);
        mValue.setTextColor(Color.WHITE);
        mValue.setTextSize(30);
        mValue.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(mValue);
        panel.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The bar: left/right in 0.05 steps
        mBar = new SpeedBar(mContext);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        barParams.topMargin = dp(8);
        panel.addView(mBar, barParams);

        // Quick buttons
        LinearLayout presets = new LinearLayout(mContext);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setGravity(Gravity.CENTER);
        for (float speed : PRESETS) {
            TextView button = createPresetButton(speed);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
            params.leftMargin = dp(6);
            params.rightMargin = dp(6);
            presets.addView(button, params);
            mPresetButtons.add(button);
        }
        LinearLayout.LayoutParams presetsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        presetsParams.topMargin = dp(12);
        panel.addView(presets, presetsParams);

        TextView hint = new TextView(mContext);
        hint.setText(R.string.hometube_speed_hint);
        hint.setTextColor(0xFF888888);
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(14);
        panel.addView(hint, hintParams);

        // Keys: up/down between the bar and the buttons, the default focus search is unreliable here
        mBar.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusPresetNearest();
                return true;
            }
            return false;
        });

        return panel;
    }

    private TextView createPresetButton(float speed) {
        TextView button = new TextView(mContext);
        button.setText(AppDialogUtil.formatSpeed(mContext, speed));
        button.setTag(speed);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setFocusable(true);
        button.setClickable(true);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0xFF2E2E2E);
        normal.setCornerRadius(dp(22));
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.WHITE);
        focused.setCornerRadius(dp(22));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, focused);
        states.addState(new int[] {}, normal);
        button.setBackground(states);

        button.setOnFocusChangeListener((v, hasFocus) -> updateButtonColors());
        button.setOnClickListener(v -> applyStep(toStep(speed)));
        button.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                mBar.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return true; // nothing below
            }
            int index = mPresetButtons.indexOf(v);
            // stay inside the row at its ends
            return (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index == 0) ||
                    (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && index == mPresetButtons.size() - 1);
        });

        return button;
    }

    private void focusPresetNearest() {
        int step = mBar.getStep();
        TextView best = null;
        int bestDiff = Integer.MAX_VALUE;
        for (TextView button : mPresetButtons) {
            int diff = Math.abs(toStep((float) button.getTag()) - step);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = button;
            }
        }
        if (best != null) {
            best.requestFocus();
        }
    }

    private void applyStep(int step) {
        step = Math.max(MIN_STEP, Math.min(MAX_STEP, step));
        mPlayer.setSpeed(toSpeed(step));
        updateUi(step);
    }

    private void updateUi(int step) {
        mBar.setStep(step);
        mValue.setText(formatValue(toSpeed(step)));
        updateButtonColors();
    }

    private void updateButtonColors() {
        int step = mBar != null ? mBar.getStep() : -1;
        for (TextView button : mPresetButtons) {
            boolean selected = toStep((float) button.getTag()) == step;
            button.setTextColor(button.isFocused() ? Color.BLACK : selected ? ACCENT : Color.WHITE);
            button.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private static int toStep(float speed) {
        return Math.max(MIN_STEP, Math.min(MAX_STEP, Math.round(speed / STEP)));
    }

    private static float toSpeed(int step) {
        return step * 5 / 100f;
    }

    private static String formatValue(float speed) {
        return String.format(java.util.Locale.US, "%.2fx", speed);
    }

    private int dp(float value) {
        return (int) (value * mDensity);
    }

    /**
     * Focusable bar: left/right change the speed by one step, OK closes the panel.
     */
    private final class SpeedBar extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private int mStep = 20;

        SpeedBar(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setOnFocusChangeListener((v, hasFocus) -> invalidate());
        }

        void setStep(int step) {
            mStep = step;
            invalidate();
        }

        int getStep() {
            return mStep;
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    applyStep(mStep - 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    applyStep(mStep + 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    return true; // nothing above
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (mDialog != null) {
                        mDialog.dismiss();
                    }
                    return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            boolean focused = isFocused();
            float thumbRadius = dp(focused ? 12 : 9);
            float left = getPaddingLeft() + dp(14);
            float right = getWidth() - getPaddingRight() - dp(14);
            float centerY = getHeight() / 2f;
            float trackHalf = dp(focused ? 4 : 3);
            float fraction = (mStep - MIN_STEP) / (float) (MAX_STEP - MIN_STEP);
            float thumbX = left + (right - left) * fraction;

            // track
            mPaint.setColor(0x55FFFFFF);
            mRect.set(left, centerY - trackHalf, right, centerY + trackHalf);
            canvas.drawRoundRect(mRect, trackHalf, trackHalf, mPaint);

            // filled part
            mPaint.setColor(ACCENT);
            mRect.set(left, centerY - trackHalf, thumbX, centerY + trackHalf);
            canvas.drawRoundRect(mRect, trackHalf, trackHalf, mPaint);

            // marks at the quick values
            for (float speed : PRESETS) {
                float x = left + (right - left) * (toStep(speed) - MIN_STEP) / (float) (MAX_STEP - MIN_STEP);
                mPaint.setColor(0xCCFFFFFF);
                canvas.drawCircle(x, centerY, dp(2), mPaint);
            }

            // thumb
            if (focused) {
                mPaint.setColor(0x40FFFFFF);
                canvas.drawCircle(thumbX, centerY, thumbRadius + dp(8), mPaint);
            }
            mPaint.setColor(Color.WHITE);
            canvas.drawCircle(thumbX, centerY, thumbRadius, mPaint);
        }
    }
}
