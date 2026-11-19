package com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * HomeTube: YouTube-like search field shown on top of the current page.<br/>
 * Results open in the search page only after the query is submitted (OK / IME search action).
 */
public final class InlineSearchDialog {
    private InlineSearchDialog() {
    }

    public static void show(Context context) {
        if (context == null) {
            return;
        }

        float density = context.getResources().getDisplayMetrics().density;

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int padH = (int) (24 * density);
        int padV = (int) (12 * density);
        bar.setPadding(padH, padV, padH, padV);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#272727"));
        background.setCornerRadius(32 * density);
        bar.setBackground(background);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.icon_hometube_search);
        int iconSize = (int) (28 * density);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMarginEnd((int) (16 * density));
        bar.addView(icon, iconParams);

        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setHint(R.string.action_search);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#AAAAAA"));
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        input.setBackground(null);
        bar.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSubmit = actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getAction() == KeyEvent.ACTION_DOWN &&
                            (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER));

            if (!isSubmit) {
                return false;
            }

            String query = input.getText().toString().trim();
            dialog.dismiss();

            if (!TextUtils.isEmpty(query)) {
                SearchPresenter.instance(context).startSearch(query);
            }

            return true;
        });

        dialog.setContentView(bar);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.6f);
            params.y = (int) (40 * density);
            params.dimAmount = 0.6f;
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE |
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }

        dialog.setOnShowListener(d -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        dialog.show();
    }
}
