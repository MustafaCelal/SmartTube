package com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.List;

/**
 * HomeTube: YouTube-like search field shown on top of the current page.<br/>
 * Below the field: recent searches (empty field) or suggestions (while typing).<br/>
 * Results open in the search page only after the query is submitted (OK / IME search action) or a list item is picked.
 */
public final class InlineSearchDialog {
    private static final int MAX_SUGGESTIONS = 5; // stay above the on-screen keyboard
    private static final int MAX_RECENT = 4;
    private static final long SUGGEST_DELAY_MS = 300;
    private static boolean sIsShowing;

    private InlineSearchDialog() {
    }

    public static void show(Context context) {
        show(context, null);
    }

    /**
     * @param onClosed called when the field is closed (with or without a search)
     */
    public static void show(Context context, Runnable onClosed) {
        if (context == null || sIsShowing) {
            return;
        }

        float density = context.getResources().getDisplayMetrics().density;

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        // Search field
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int padH = (int) (24 * density);
        int padV = (int) (12 * density);
        bar.setPadding(padH, padV, padH, padV);
        bar.setBackground(rounded(Color.parseColor("#272727"), 32 * density));

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

        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Recent searches / suggestions
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int listPad = (int) (8 * density);
        list.setPadding(listPad, listPad, listPad, listPad);
        list.setBackground(rounded(Color.parseColor("#212121"), 16 * density));
        list.setVisibility(View.GONE);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listParams.topMargin = (int) (8 * density);
        root.addView(list, listParams);

        Runnable[] submit = new Runnable[1];
        Runnable submitAction = () -> submit[0].run();

        submit[0] = () -> {
            String query = input.getText().toString().trim();
            dialog.dismiss();

            if (!TextUtils.isEmpty(query)) {
                SearchPresenter.instance(context).startSearch(query);
            }
        };

        MediaServiceSearchTagProvider provider = new MediaServiceSearchTagProvider(false);
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable loadTags = () -> {
            String query = input.getText().toString().trim();
            provider.search(query, results -> list.post(() -> {
                if (dialog.isShowing() && TextUtils.equals(query, input.getText().toString().trim())) {
                    fillList(context, list, results, TextUtils.isEmpty(query), tag -> {
                        input.setText(tag);
                        submitAction.run();
                    });
                }
            }));
        };

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(loadTags);
                handler.postDelayed(loadTags, SUGGEST_DELAY_MS);
            }
        });

        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSubmit = actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getAction() == KeyEvent.ACTION_DOWN &&
                            (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER));

            if (!isSubmit) {
                return false;
            }

            submitAction.run();
            return true;
        });

        dialog.setContentView(root);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.6f);
            params.y = (int) (32 * density);
            params.dimAmount = 0.75f;
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
            loadTags.run(); // recent searches
        });

        dialog.setOnDismissListener(d -> {
            sIsShowing = false;
            handler.removeCallbacks(loadTags);
            if (onClosed != null) {
                onClosed.run();
            }
        });

        sIsShowing = true;
        dialog.show();
    }

    private interface OnTagClick {
        void onClick(String tag);
    }

    private static void fillList(Context context, LinearLayout list, List<Tag> tags, boolean isRecent, OnTagClick onClick) {
        list.removeAllViews();

        if (tags == null || tags.isEmpty()) {
            list.setVisibility(View.GONE);
            return;
        }

        float density = context.getResources().getDisplayMetrics().density;

        if (isRecent) {
            TextView header = new TextView(context);
            header.setText(R.string.hometube_recent_searches);
            header.setTextColor(Color.parseColor("#AAAAAA"));
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            header.setPadding((int) (16 * density), (int) (4 * density), (int) (16 * density), (int) (4 * density));
            list.addView(header);
        }

        int count = 0;
        for (Tag tag : tags) {
            if (tag == null || TextUtils.isEmpty(tag.tag)) {
                continue;
            }
            if (count++ >= (isRecent ? MAX_RECENT : MAX_SUGGESTIONS)) {
                break;
            }
            list.addView(createItem(context, tag.tag, isRecent, onClick));
        }

        list.setVisibility(list.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private static View createItem(Context context, String text, boolean isRecent, OnTagClick onClick) {
        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setFocusable(true);
        item.setClickable(true);
        item.setPadding((int) (16 * density), (int) (5 * density), (int) (16 * density), (int) (5 * density));

        ImageView icon = new ImageView(context);
        icon.setImageResource(isRecent ? R.drawable.icon_history : R.drawable.icon_hometube_search);
        int iconSize = (int) (22 * density);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMarginEnd((int) (16 * density));
        item.addView(icon, iconParams);

        TextView label = new TextView(context);
        label.setText(text);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextColor(Color.WHITE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        item.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        GradientDrawable focused = rounded(Color.WHITE, 24 * density);
        item.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(hasFocus ? focused : null);
            label.setTextColor(hasFocus ? Color.BLACK : Color.WHITE);
            if (hasFocus) {
                icon.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
            } else {
                icon.clearColorFilter();
            }
        });
        item.setOnClickListener(v -> onClick.onClick(text));

        return item;
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }
}
