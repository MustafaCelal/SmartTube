package com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.FocusHighlightHelper;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.tv.presenter.RowVideoCardPresenter;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.reactivex.disposables.Disposable;

/**
 * HomeTube: full page search, YouTube TV-like.<br/>
 * Left: recent searches (clock) and suggestions (magnifier), typed part normal, the rest bold.<br/>
 * Right: own on-screen keyboard (all Turkish letters, numbers), so it looks and works the same on every TV.
 * The TV's own keyboard is one key away.
 */
public final class InlineSearchDialog {
    private static final int MAX_ITEMS = 9;
    private static final int MAX_HISTORY_MATCHES = 3;
    private static final long SUGGEST_DELAY_MS = 250;
    private static final int TEXT_COLOR = 0xFFF1F1F1;
    private static final int SECONDARY_COLOR = 0xFFAAAAAA;
    private static final int FIELD_COLOR = 0xFF272727;
    private static final int KEY_COLOR = 0xFF222222;
    private static final String[][] LETTERS = {
            {"a", "b", "c", "ç", "d", "e", "f", "g", "ğ"},
            {"h", "ı", "i", "j", "k", "l", "m", "n", "o"},
            {"ö", "p", "q", "r", "s", "ş", "t", "u", "ü"},
            {"v", "w", "x", "y", "z", "-", "'", ".", "&"},
    };
    private static final String[][] SYMBOLS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9"},
            {"0", "@", "#", "$", "%", "&", "*", "+", "="},
            {"(", ")", "!", "?", ",", ":", ";", "/", "\""},
            {"_", "~", "<", ">", "[", "]", "{", "}", "|"},
    };
    private static boolean sIsShowing;

    private final Context mContext;
    private final Dialog mDialog;
    private final float mDensity;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final MediaServiceSearchTagProvider mProvider = new MediaServiceSearchTagProvider(false);
    private final List<String> mHistory = new ArrayList<>();
    private EditText mInput;
    private LinearLayout mList;
    private LinearLayout mKeyRows;
    private TextView mModeKey;
    private boolean mIsSymbols;
    private View mFirstKey;
    private LinearLayout mPage;
    private LinearLayout mVideoRow;
    private TextView mVideoRowTitle;
    private ArrayObjectAdapter mVideoAdapter;
    private Disposable mVideosAction;
    private final Runnable mLoadSuggestions = this::loadSuggestions;

    private InlineSearchDialog(Context context) {
        mContext = context;
        mDensity = context.getResources().getDisplayMetrics().density;
        mDialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    public static void show(Context context) {
        show(context, null);
    }

    /**
     * @param onClosed called when the search page is closed (with or without a search)
     */
    public static void show(Context context, Runnable onClosed) {
        show(context, null, onClosed);
    }

    /**
     * @param initialQuery text already in the field (e.g. editing the current search)
     */
    public static void show(Context context, String initialQuery, Runnable onClosed) {
        if (context == null || sIsShowing) {
            return;
        }

        InlineSearchDialog dialog = new InlineSearchDialog(context);
        dialog.showInt(onClosed);
        if (!TextUtils.isEmpty(initialQuery)) {
            dialog.setQuery(initialQuery);
        }
    }

    private int dp(float value) {
        return (int) (value * mDensity);
    }

    private void showInt(Runnable onClosed) {
        FrameLayout root = new FrameLayout(mContext);
        root.setBackgroundColor(0xFF0F0F0F);

        LinearLayout page = new LinearLayout(mContext);
        mPage = page;
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipToPadding(false); // the focused (zoomed) card may go a bit beyond the left edge
        page.setClipChildren(false);
        page.setPadding(dp(80), dp(32), dp(48), dp(24));
        // The page is taller than the screen (videos at the bottom): scrollable, moved by the code below
        android.widget.ScrollView scroll = new android.widget.ScrollView(mContext);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        scroll.setSmoothScrollingEnabled(true);
        scroll.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        page.addView(createSearchBar(), new LinearLayout.LayoutParams(dp(560), dp(44)));

        LinearLayout body = new LinearLayout(mContext);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330));
        bodyParams.topMargin = dp(24);
        page.addView(body, bodyParams);

        mList = new LinearLayout(mContext);
        mList.setOrientation(LinearLayout.VERTICAL);
        body.addView(mList, new LinearLayout.LayoutParams(dp(280), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout keyboard = createKeyboard();
        LinearLayout.LayoutParams keyboardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        keyboardParams.setMarginStart(dp(24));
        body.addView(keyboard, keyboardParams);

        page.addView(createVideoRow(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Going down to the videos scrolls the page up, like YouTube
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            boolean inRow = newFocus != null && isDescendant(newFocus, mVideoRow);
            // the row title moves near the top of the screen and the whole row is visible, back to the top otherwise
            scroll.post(() -> scroll.smoothScrollTo(0, inRow ? Math.max(0, mVideoRow.getTop() - dp(40)) : 0));
        });

        mDialog.setContentView(root);

        Window window = mDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0xFF0F0F0F));
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN |
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }

        mDialog.setOnShowListener(d -> {
            if (mFirstKey != null) {
                mFirstKey.requestFocus();
            }
            loadHistory();
        });

        mDialog.setOnDismissListener(d -> {
            sIsShowing = false;
            if (mVideosAction != null) {
                mVideosAction.dispose();
            }
            mHandler.removeCallbacks(mLoadSuggestions);
            mHandler.removeCallbacks(mRetryHistory);
            if (onClosed != null) {
                onClosed.run();
            }
        });

        sIsShowing = true;
        mDialog.show();
    }

    // ---------- Search bar ----------

    private View createSearchBar() {
        LinearLayout bar = new LinearLayout(mContext);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), 0, dp(18), 0);
        bar.setBackground(rounded(FIELD_COLOR, dp(22)));

        ImageView icon = new ImageView(mContext);
        icon.setImageResource(R.drawable.icon_hometube_search);
        icon.setColorFilter(SECONDARY_COLOR, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconParams.setMarginEnd(dp(12));
        bar.addView(icon, iconParams);

        mInput = new EditText(mContext);
        mInput.setSingleLine(true);
        mInput.setInputType(InputType.TYPE_CLASS_TEXT);
        mInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        mInput.setHint(R.string.action_search);
        mInput.setTextColor(TEXT_COLOR);
        mInput.setHintTextColor(0xFF8A8A8A);
        mInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        mInput.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mInput.setBackground(null);
        mInput.setPadding(0, 0, 0, 0);
        // Typing is done with the on-screen keys below; the TV keyboard only on request
        mInput.setFocusable(false);
        mInput.setFocusableInTouchMode(false);
        if (Build.VERSION.SDK_INT >= 21) {
            mInput.setShowSoftInputOnFocus(false);
        }
        mInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mHandler.removeCallbacks(mLoadSuggestions);
                mHandler.postDelayed(mLoadSuggestions, SUGGEST_DELAY_MS);
            }
        });
        mInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSubmit = actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (isSubmit) {
                submit(getQuery());
            }
            return isSubmit;
        });
        bar.addView(mInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        return bar;
    }

    private String getQuery() {
        return mInput.getText().toString();
    }

    private void setQuery(String query) {
        mInput.setText(query);
        mInput.setSelection(query.length());
    }

    private void submit(String query) {
        String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            return;
        }

        mDialog.dismiss();
        SearchPresenter.instance(mContext).startSearch(trimmed);
    }

    /**
     * Switch to the TV's own keyboard (voice typing, other languages)
     */
    private void openSystemKeyboard() {
        mInput.setFocusable(true);
        mInput.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 21) {
            mInput.setShowSoftInputOnFocus(true);
        }
        mInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    // ---------- Keyboard ----------

    private LinearLayout createKeyboard() {
        LinearLayout keyboard = new LinearLayout(mContext);
        keyboard.setOrientation(LinearLayout.HORIZONTAL);

        mKeyRows = new LinearLayout(mContext);
        mKeyRows.setOrientation(LinearLayout.VERTICAL);
        keyboard.addView(mKeyRows);
        fillKeys();

        // Side column: backspace, letters/numbers, TV keyboard
        LinearLayout side = new LinearLayout(mContext);
        side.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sideParams.setMarginStart(dp(10));
        keyboard.addView(side, sideParams);

        side.addView(createKey("⌫", dp(64), true, () -> {
            String query = getQuery();
            if (!query.isEmpty()) {
                setQuery(query.substring(0, query.length() - 1));
            }
        }));
        mModeKey = createKey("&123", dp(64), true, () -> {
            mIsSymbols = !mIsSymbols;
            mModeKey.setText(mIsSymbols ? "abc" : "&123");
            fillKeys();
        });
        side.addView(mModeKey);
        side.addView(createKey("⌨", dp(64), true, this::openSystemKeyboard));

        return keyboard;
    }

    private void fillKeys() {
        View focused = mDialog.getCurrentFocus();
        int focusedIndex = focused != null ? indexInKeys(focused) : -1;

        mKeyRows.removeAllViews();
        String[][] keys = mIsSymbols ? SYMBOLS : LETTERS;
        mFirstKey = null;

        for (String[] row : keys) {
            LinearLayout rowView = new LinearLayout(mContext);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            for (String key : row) {
                TextView keyView = createKey(key, dp(40), false, () -> setQuery(getQuery() + key));
                if (mFirstKey == null) {
                    mFirstKey = keyView;
                }
                rowView.addView(keyView);
            }
            mKeyRows.addView(rowView);
        }

        // Space, clear, search
        LinearLayout actions = new LinearLayout(mContext);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(8);
        actions.addView(createKey(mContext.getString(R.string.hometube_key_space), dp(140), true, () -> {
            String query = getQuery();
            if (!query.isEmpty() && !query.endsWith(" ")) {
                setQuery(query + " ");
            }
        }));
        actions.addView(createKey(mContext.getString(R.string.hometube_key_clear), dp(130), true, () -> setQuery("")));
        TextView search = createKey(mContext.getString(R.string.action_search), dp(130), true, () -> submit(getQuery()));
        search.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        actions.addView(search);
        mKeyRows.addView(actions, actionsParams);

        if (focusedIndex >= 0) {
            View same = keyAt(focusedIndex);
            if (same != null) {
                same.requestFocus();
            }
        }
    }

    private int indexInKeys(View key) {
        int index = 0;
        for (int r = 0; r < mKeyRows.getChildCount(); r++) {
            ViewGroup row = (ViewGroup) mKeyRows.getChildAt(r);
            for (int c = 0; c < row.getChildCount(); c++) {
                if (row.getChildAt(c) == key) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }

    private View keyAt(int index) {
        for (int r = 0; r < mKeyRows.getChildCount(); r++) {
            ViewGroup row = (ViewGroup) mKeyRows.getChildAt(r);
            if (index < row.getChildCount()) {
                return row.getChildAt(index);
            }
            index -= row.getChildCount();
        }
        return null;
    }

    private TextView createKey(String label, int width, boolean isAction, Runnable action) {
        TextView key = new TextView(mContext);
        key.setText(label);
        key.setGravity(Gravity.CENTER);
        key.setTextColor(TEXT_COLOR);
        key.setTextSize(TypedValue.COMPLEX_UNIT_SP, label.length() > 1 ? 14 : 19);
        key.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        key.setFocusable(true);
        key.setClickable(true);
        GradientDrawable normal = rounded(isAction ? KEY_COLOR : Color.TRANSPARENT, dp(10));
        GradientDrawable focused = rounded(Color.WHITE, dp(10));
        key.setBackground(normal);
        key.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(hasFocus ? focused : normal);
            key.setTextColor(hasFocus ? Color.BLACK : TEXT_COLOR);
        });
        key.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(42));
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        key.setLayoutParams(params);
        return key;
    }

    // ---------- Recent searches and suggestions ----------

    private int mHistoryTries;
    private boolean mIsHistoryLoaded;
    private final Runnable mRetryHistory = this::loadHistory;

    private void loadHistory() {
        // The request may fail without a callback (network hiccup): try again a couple of times
        if (mIsHistoryLoaded || mHistoryTries++ >= 3) {
            return;
        }
        mHandler.postDelayed(mRetryHistory, 3_000);

        mProvider.search("", results -> mList.post(() -> {
            if (mIsHistoryLoaded) {
                return;
            }
            mIsHistoryLoaded = true;
            mHandler.removeCallbacks(mRetryHistory);
            mHistory.clear();
            if (results != null) {
                for (Tag tag : results) {
                    if (tag != null && !TextUtils.isEmpty(tag.tag)) {
                        mHistory.add(tag.tag);
                    }
                }
            }
            if (getQuery().trim().isEmpty()) {
                showItems(new ArrayList<>(mHistory.subList(0, Math.min(mHistory.size(), MAX_ITEMS))), mHistory, "");
            }
            if (!mHistory.isEmpty()) {
                loadVideos(mHistory.get(0));
            }
        }));
    }

    // ---------- Videos for the last search (shown while the field is empty) ----------

    private View createVideoRow() {
        mVideoRow = new LinearLayout(mContext);
        mVideoRow.setOrientation(LinearLayout.VERTICAL);
        mVideoRow.setVisibility(View.GONE);
        mVideoRow.setClipChildren(false);
        mVideoRow.setClipToPadding(false);

        mVideoRowTitle = new TextView(mContext);
        mVideoRowTitle.setTextColor(TEXT_COLOR);
        mVideoRowTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        mVideoRowTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(12);
        mVideoRow.addView(mVideoRowTitle, titleParams);

        HorizontalGridView grid = new HorizontalGridView(mContext);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setHorizontalSpacing(mContext.getResources().getDimensionPixelSize(R.dimen.grid_item_horizontal_spacing));
        grid.setWindowAlignment(HorizontalGridView.WINDOW_ALIGN_LOW_EDGE);
        grid.setWindowAlignmentOffsetPercent(0);
        grid.setItemAlignmentOffsetPercent(0);
        grid.setPadding(0, dp(8), dp(48), dp(8));

        mVideoAdapter = new ArrayObjectAdapter(new RowVideoCardPresenter());
        ItemBridgeAdapter bridge = new ItemBridgeAdapter(mVideoAdapter);
        bridge.setAdapterListener(new ItemBridgeAdapter.AdapterListener() {
            @Override
            public void onBind(ItemBridgeAdapter.ViewHolder viewHolder) {
                viewHolder.itemView.setActivated(true); // cards show their text only when activated (normally set by the row)
                viewHolder.itemView.setOnClickListener(v -> {
                    Object item = viewHolder.getItem();
                    if (item instanceof Video) {
                        mDialog.dismiss();
                        PlaybackPresenter.instance(mContext).openVideo((Video) item);
                    }
                });
            }
        });
        FocusHighlightHelper.setupBrowseItemFocusHighlight(bridge, FocusHighlight.ZOOM_FACTOR_SMALL, false);
        grid.setAdapter(bridge);
        // Fixed height: the grid is measured before the cards arrive (thumbnail 16:9 + two text blocks)
        float scale = com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData.instance(mContext).getVideoGridScale();
        int cardWidth = (int) (mContext.getResources().getDimensionPixelSize(R.dimen.hometube_row_card_width) * scale);
        int gridHeight = cardWidth * 9 / 16 + dp(120);
        mVideoRow.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, gridHeight));

        return mVideoRow;
    }

    private void loadVideos(String query) {
        loadVideos(query, true);
    }

    private void loadVideos(String query, boolean retry) {
        mVideosAction = YouTubeServiceManager.instance().getContentService().getSearchObserve(query)
                .subscribe(groups -> mVideoRow.post(() -> showVideos(query, groups)), error -> {
                    if (retry) { // one more try, otherwise the page works without the row
                        mHandler.postDelayed(() -> {
                            if (mDialog.isShowing()) {
                                loadVideos(query, false);
                            }
                        }, 1_500);
                    }
                });
    }

    private void showVideos(String query, List<MediaGroup> groups) {
        if (!mDialog.isShowing() || groups == null) {
            return;
        }

        for (MediaGroup group : groups) {
            if (group == null || group.getType() == MediaGroup.TYPE_SHORTS || group.getMediaItems() == null) {
                continue;
            }
            List<Video> videos = new ArrayList<>();
            for (Video video : VideoGroup.from(group).getVideos()) {
                if (video.hasVideo() && !video.isShorts) {
                    videos.add(video);
                }
            }
            if (videos.size() >= 3) {
                mVideoAdapter.clear();
                mVideoAdapter.addAll(0, videos);
                mVideoRowTitle.setText(mContext.getString(R.string.hometube_search_recent_videos, query));
                mVideoRow.setVisibility(getQuery().trim().isEmpty() ? View.VISIBLE : View.GONE);
                return;
            }
        }
    }

    private static boolean isDescendant(View view, View parent) {
        android.view.ViewParent current = view.getParent();
        while (current != null) {
            if (current == parent) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void loadSuggestions() {
        String query = getQuery().trim();

        // Videos only while nothing is typed (like YouTube)
        mVideoRow.setVisibility(query.isEmpty() && mVideoAdapter.size() > 0 ? View.VISIBLE : View.GONE);

        if (query.isEmpty()) {
            showItems(new ArrayList<>(mHistory.subList(0, Math.min(mHistory.size(), MAX_ITEMS))), mHistory, "");
            return;
        }

        String lower = query.toLowerCase(Locale.getDefault());
        List<String> historyMatches = new ArrayList<>();
        for (String item : mHistory) {
            if (item.toLowerCase(Locale.getDefault()).startsWith(lower) && historyMatches.size() < MAX_HISTORY_MATCHES) {
                historyMatches.add(item);
            }
        }
        showItems(historyMatches, historyMatches, query); // right away, suggestions follow

        mProvider.search(query, results -> mList.post(() -> {
            if (!mDialog.isShowing() || !TextUtils.equals(query, getQuery().trim())) {
                return;
            }
            List<String> items = new ArrayList<>(historyMatches);
            if (results != null) {
                for (Tag tag : results) {
                    if (tag != null && !TextUtils.isEmpty(tag.tag) && !items.contains(tag.tag) && items.size() < MAX_ITEMS) {
                        items.add(tag.tag);
                    }
                }
            }
            showItems(items, historyMatches, query);
        }));
    }

    private void showItems(List<String> items, List<String> history, String query) {
        // The focus stays on the keyboard while typing
        mList.removeAllViews();
        for (String item : items) {
            mList.addView(createItem(item, history.contains(item), query));
        }
    }

    private View createItem(String text, boolean isHistory, String query) {
        LinearLayout item = new LinearLayout(mContext);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setFocusable(true);
        item.setClickable(true);
        item.setPadding(dp(14), dp(7), dp(14), dp(7));

        ImageView icon = new ImageView(mContext);
        icon.setImageResource(isHistory ? R.drawable.icon_history : R.drawable.icon_hometube_search);
        icon.setColorFilter(TEXT_COLOR, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconParams.setMarginEnd(dp(14));
        item.addView(icon, iconParams);

        TextView label = new TextView(mContext);
        label.setText(highlight(text, query));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextColor(TEXT_COLOR);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        item.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        GradientDrawable focused = rounded(Color.WHITE, dp(22));
        item.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(hasFocus ? focused : null);
            label.setTextColor(hasFocus ? Color.BLACK : TEXT_COLOR);
            icon.setColorFilter(hasFocus ? Color.BLACK : TEXT_COLOR, PorterDuff.Mode.SRC_IN);
        });
        item.setOnClickListener(v -> submit(text));

        return item;
    }

    /**
     * Typed part normal, the completion bold (like YouTube)
     */
    private static CharSequence highlight(String text, String query) {
        SpannableString result = new SpannableString(text);
        Locale locale = Locale.getDefault();
        if (!query.isEmpty() && text.toLowerCase(locale).startsWith(query.toLowerCase(locale)) && text.length() > query.length()) {
            result.setSpan(new StyleSpan(Typeface.BOLD), query.length(), text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (query.isEmpty()) {
            result.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return result;
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }
}
