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
import com.liskovsoft.mediaserviceinterfaces.data.SearchOptions;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
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
    private LinearLayout mResults;      // chips + result rows, under the keyboard
    private LinearLayout mChips;
    private LinearLayout mRows;
    private android.widget.ScrollView mScroll;
    private Disposable mVideosAction;
    private Disposable mResultsAction;
    private String mSubmittedQuery;     // results on the page are for this query
    private long mSubmitTimeMs;
    private java.lang.ref.WeakReference<View> mLastFocus;
    private View mLastFocusBeforeLeave;
    private boolean mIsRestoringFocus;
    private int mSearchOptions;
    private String mRecentTitle;        // videos for the last search, shown while the field is empty
    private List<Video> mRecentVideos;
    private final Runnable mLoadSuggestions = this::loadSuggestions;

    private final boolean mIsPage;      // embedded as the Search page of the sidebar (no dialog)
    private boolean mIsActive;
    private View mRoot;
    private View mMic;

    private InlineSearchDialog(Context context, boolean isPage) {
        mContext = context;
        mDensity = context.getResources().getDisplayMetrics().density;
        mIsPage = isPage;
        mDialog = isPage ? null : new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    /**
     * HomeTube: the search as a page of the sidebar (YouTube TV-like). Focus enters on the mic,
     * right from it is the field. Call {@link #release()} when the page goes away.
     */
    public static InlineSearchDialog createPage(Context context) {
        InlineSearchDialog page = new InlineSearchDialog(context, true);
        page.mRoot = page.buildView();
        page.mIsActive = true;
        page.loadHistory();
        return page;
    }

    public View getView() {
        return mRoot;
    }

    public void release() {
        mIsActive = false;
        if (mVideosAction != null) {
            mVideosAction.dispose();
        }
        if (mResultsAction != null) {
            mResultsAction.dispose();
        }
        mHandler.removeCallbacks(mLoadSuggestions);
        mHandler.removeCallbacks(mRetryHistory);
    }

    private boolean isActive() {
        return mIsPage ? mIsActive : mDialog.isShowing();
    }

    private View currentFocus() {
        return mRoot != null ? mRoot.findFocus() : null;
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

        InlineSearchDialog dialog = new InlineSearchDialog(context, false);
        dialog.showInt(onClosed);
        if (!TextUtils.isEmpty(initialQuery)) {
            dialog.setQuery(initialQuery);
        }
    }

    private int dp(float value) {
        return (int) (value * mDensity);
    }

    private View buildView() {
        FrameLayout root = new FrameLayout(mContext) {
            @Override
            protected boolean onRequestFocusInDescendants(int direction, android.graphics.Rect previouslyFocusedRect) {
                // Coming from the sidebar: start on the mic (like YouTube), right from it is the field
                if (mIsPage && mMic != null && findFocus() == null && mMic.requestFocus()) {
                    return true;
                }
                return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
            }
        };
        root.setBackgroundColor(mIsPage ? Color.TRANSPARENT : 0xFF0F0F0F);
        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        LinearLayout page = new LinearLayout(mContext);
        mPage = page;
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipToPadding(false); // the focused (zoomed) card may go a bit beyond the left edge
        page.setClipChildren(false);
        page.setPadding(mIsPage ? dp(28) : dp(80), dp(32), dp(48), dp(24));
        // The page is taller than the screen (videos at the bottom): scrollable, moved by the code below
        android.widget.ScrollView scroll = new android.widget.ScrollView(mContext);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        scroll.setSmoothScrollingEnabled(true);
        scroll.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(mContext);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(createMicButton(), new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(520), dp(44));
        barParams.setMarginStart(dp(12));
        top.addView(createSearchBar(), barParams);
        page.addView(top);
        // Mic <-> field with left/right (the field sits inside the bar, default focus search misses it)
        mMic.setId(View.generateViewId());
        mInput.setId(View.generateViewId());
        mMic.setNextFocusRightId(mInput.getId());
        mInput.setNextFocusLeftId(mMic.getId());
        if (mFirstKey != null) {
            if (mFirstKey.getId() == View.NO_ID) {
                mFirstKey.setId(View.generateViewId());
            }
            mInput.setNextFocusDownId(mFirstKey.getId());
            mMic.setNextFocusDownId(mFirstKey.getId());
        }

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

        page.addView(createResults(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mScroll = scroll;

        // Going down to the results scrolls the page (the focused row near the top), back up shows the keyboard again
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus != null && isDescendant(newFocus, root)) {
                if (mIsRestoringFocus) {
                    return;
                }
                mLastFocus = new java.lang.ref.WeakReference<>(newFocus);
                scrollToFocus(newFocus);
            }
        });
        // Back from a video: focus the card that was opened (like YouTube), not the first item
        root.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
            if (!hasFocus) {
                mLastFocusBeforeLeave = mLastFocus != null ? mLastFocus.get() : null;
                return;
            }
            View last = mLastFocusBeforeLeave;
            mLastFocusBeforeLeave = null;
            if (last != null && last.isAttachedToWindow() && isDescendant(last, mResults)) {
                mIsRestoringFocus = true;
                root.post(() -> {
                    last.requestFocus();
                    mIsRestoringFocus = false;
                    mLastFocus = new java.lang.ref.WeakReference<>(last);
                    scrollToFocus(last);
                });
            }
        });

        return root;
    }

    private void showInt(Runnable onClosed) {
        mRoot = buildView();
        mDialog.setContentView(mRoot);

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
            if (mResultsAction != null) {
                mResultsAction.dispose();
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
        bar.setOnClickListener(v -> openSystemKeyboard()); // touch / mouse on the bar
        bar.setFocusable(false); // the field inside takes the focus, not the bar

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
        // Reachable with the remote (up from the keys). Moving onto it doesn't pop up the TV keyboard,
        // OK on it does (voice typing, other languages, or a remote app/keyboard typing into it)
        mInput.setFocusable(true);
        mInput.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 21) {
            mInput.setShowSoftInputOnFocus(false);
        }
        GradientDrawable barNormal = rounded(FIELD_COLOR, dp(22));
        GradientDrawable barFocused = rounded(0xFF3A3A3A, dp(22));
        barFocused.setStroke(dp(2), Color.WHITE);
        mInput.setOnFocusChangeListener((v, hasFocus) -> {
            bar.setBackground(hasFocus ? barFocused : barNormal);
            icon.setColorFilter(hasFocus ? TEXT_COLOR : SECONDARY_COLOR, PorterDuff.Mode.SRC_IN);
        });
        mInput.setOnClickListener(v -> openSystemKeyboard());
        mInput.setOnKeyListener((v, keyCode, event) -> {
            boolean isOk = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (isOk && System.currentTimeMillis() - mSubmitTimeMs < 1_000) {
                return true; // the rest of the key press that submitted the search
            }
            if (event.getAction() == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                openSystemKeyboard();
                return true;
            }
            return false;
        });
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

    /**
     * Results show on this page under the keyboard (like YouTube); up from them is the keyboard again
     */
    private void submit(String query) {
        String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            return;
        }

        if (!TextUtils.equals(getQuery(), trimmed)) {
            setQuery(trimmed);
        }
        mHistory.remove(trimmed);
        mHistory.add(0, trimmed);
        mSearchOptions = 0;
        mSubmitTimeMs = System.currentTimeMillis();
        // Close the TV keyboard, the results come under our keyboard
        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mInput.getWindowToken(), 0);
        }
        runSearch(trimmed, true);
    }

    // ---------- Voice ----------

    private View createMicButton() {
        ImageView mic = new ImageView(mContext);
        mic.setImageResource(R.drawable.lb_ic_search_mic_out);
        mic.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mic.setPadding(dp(10), dp(10), dp(10), dp(10));
        mic.setColorFilter(TEXT_COLOR, PorterDuff.Mode.SRC_IN);
        mic.setFocusable(true);
        mic.setClickable(true);
        GradientDrawable normal = rounded(FIELD_COLOR, dp(22));
        GradientDrawable focused = rounded(Color.WHITE, dp(22));
        mic.setBackground(normal);
        mic.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(hasFocus ? focused : normal);
            mic.setColorFilter(hasFocus ? Color.BLACK : TEXT_COLOR, PorterDuff.Mode.SRC_IN);
            mInput.setHint(hasFocus ? R.string.hometube_voice_search : R.string.action_search);
        });
        mMic = mic;
        mic.setOnClickListener(v -> {
            // The TV's voice search; its results open in the search results page
            if (!mIsPage) {
                mDialog.dismiss();
            }
            SearchPresenter.instance(mContext).startVoice();
        });
        return mic;
    }

    /**
     * Switch to the TV's own keyboard (voice typing, other languages)
     */
    private void openSystemKeyboard() {
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
        View focused = currentFocus();
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

        // Down from the field goes to the keyboard
        if (mInput != null && mFirstKey != null) {
            if (mFirstKey.getId() == View.NO_ID) {
                mFirstKey.setId(View.generateViewId());
            }
            mInput.setNextFocusDownId(mFirstKey.getId());
            if (mMic != null) {
                mMic.setNextFocusDownId(mFirstKey.getId());
            }
        }

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

    // ---------- History ----------

    /**
     * Long press on a recent search: remove it or clear all (YouTube TV can't do this from the search page)
     */
    private void showHistoryMenu(String tag) {
        com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter dialog =
                com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter.instance(mContext);
        dialog.appendSingleButton(com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem.from(
                mContext.getString(R.string.remove_from_search_history), option -> {
                    com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.instance().removeSearchTag(tag);
                    mHistory.remove(tag);
                    dialog.closeDialog();
                    loadSuggestions();
                }));
        dialog.appendSingleButton(com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem.from(
                mContext.getString(R.string.clear_search_history), option -> {
                    com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.instance().clearSearchHistory();
                    mHistory.clear();
                    mRecentVideos = null;
                    dialog.closeDialog();
                    loadSuggestions();
                }));
        dialog.showDialog(tag);
    }

    // ---------- Results under the keyboard ----------

    private static final int[][] FILTERS = {
            {R.string.hometube_filter_all, 0},
            {R.string.hometube_filter_newest, SearchOptions.SORT_BY_UPLOAD_DATE},
            {R.string.hometube_filter_today, SearchOptions.UPLOAD_DATE_TODAY},
            {R.string.hometube_filter_week, SearchOptions.UPLOAD_DATE_THIS_WEEK},
            {R.string.hometube_filter_popular, SearchOptions.SORT_BY_VIEW_COUNT},
            {R.string.hometube_filter_long, SearchOptions.DURATION_OVER_20},
            {R.string.hometube_filter_live, SearchOptions.FEATURE_LIVE},
            {R.string.hometube_filter_channels, SearchOptions.TYPE_CHANNEL},
            {R.string.hometube_filter_playlists, SearchOptions.TYPE_PLAYLIST},
    };

    private View createResults() {
        mResults = new LinearLayout(mContext);
        mResults.setOrientation(LinearLayout.VERTICAL);
        mResults.setVisibility(View.GONE);
        mResults.setClipChildren(false);
        mResults.setClipToPadding(false);

        // Filters: something YouTube TV doesn't have
        android.widget.HorizontalScrollView chipsScroll = new android.widget.HorizontalScrollView(mContext);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setClipToPadding(false);
        chipsScroll.setPadding(0, dp(4), dp(48), dp(4));
        mChips = new LinearLayout(mContext);
        mChips.setOrientation(LinearLayout.HORIZONTAL);
        for (int[] filter : FILTERS) {
            mChips.addView(createChip(mContext.getString(filter[0]), filter[1]));
        }
        chipsScroll.addView(mChips);
        chipsScroll.setVisibility(View.GONE);
        LinearLayout.LayoutParams chipsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsParams.bottomMargin = dp(8);
        mResults.addView(chipsScroll, chipsParams);

        mRows = new LinearLayout(mContext);
        mRows.setOrientation(LinearLayout.VERTICAL);
        mRows.setClipChildren(false);
        mRows.setClipToPadding(false);
        mResults.addView(mRows, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return mResults;
    }

    private View createChip(String label, int options) {
        TextView chip = new TextView(mContext);
        chip.setText(label);
        chip.setTag(options);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setPadding(dp(16), dp(8), dp(16), dp(8));
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setOnFocusChangeListener((v, hasFocus) -> updateChip(chip));
        chip.setOnClickListener(v -> {
            if (mSubmittedQuery != null && mSearchOptions != options) {
                mSearchOptions = options;
                runSearch(mSubmittedQuery, false);
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(10));
        chip.setLayoutParams(params);
        updateChip(chip);
        return chip;
    }

    private void updateChip(TextView chip) {
        boolean active = chip.getTag() instanceof Integer && (Integer) chip.getTag() == mSearchOptions;
        boolean focused = chip.hasFocus();
        GradientDrawable background = rounded(focused ? Color.WHITE : active ? 0xFFD9D9D9 : FIELD_COLOR, dp(18));
        chip.setBackground(background);
        chip.setTextColor(focused || active ? Color.BLACK : TEXT_COLOR);
    }

    private void updateChips() {
        for (int i = 0; i < mChips.getChildCount(); i++) {
            updateChip((TextView) mChips.getChildAt(i));
        }
    }

    /**
     * Results: the focused block (filters or a row) near the top of the screen. Keyboard: back to the top.
     */
    private void scrollToFocus(View focused) {
        int target = 0;
        if (focused != null && isDescendant(focused, mResults)) {
            View block = focused;
            while (block.getParent() != mRows && block.getParent() != mResults && block.getParent() instanceof View) {
                block = (View) block.getParent();
            }
            int blockTop = block.getParent() == mRows ? mRows.getTop() + block.getTop() : block.getTop();
            target = Math.max(0, mResults.getTop() + blockTop - dp(32));
        }
        int scrollTarget = target;
        mScroll.post(() -> mScroll.smoothScrollTo(0, scrollTarget));
    }

    private void runSearch(String query, boolean focusResults) {
        mSubmittedQuery = query;
        updateChips();
        mHandler.removeCallbacks(mLoadSuggestions);
        if (mResultsAction != null) {
            mResultsAction.dispose();
        }

        // Keep the page height while reloading (a filter), otherwise the page jumps to the top
        mRows.setMinimumHeight(focusResults ? 0 : mRows.getHeight());
        mRows.removeAllViews();
        TextView loading = createRowTitle(mContext.getString(R.string.hometube_searching));
        mRows.addView(loading);
        ((View) mChips.getParent()).setVisibility(View.VISIBLE);
        mResults.setVisibility(View.VISIBLE);

        io.reactivex.Observable<List<MediaGroup>> observable = mSearchOptions == 0 ?
                YouTubeServiceManager.instance().getContentService().getSearchObserve(query) :
                YouTubeServiceManager.instance().getContentService().getSearchObserve(query, mSearchOptions);
        mResultsAction = observable.subscribe(
                groups -> mRows.post(() -> showResults(query, groups, focusResults)),
                error -> mRows.post(() -> {
                    if (TextUtils.equals(query, mSubmittedQuery)) {
                        loading.setText(R.string.hometube_search_failed);
                    }
                }));
    }

    private void showResults(String query, List<MediaGroup> groups, boolean focusResults) {
        if (!isActive() || !TextUtils.equals(query, mSubmittedQuery)) {
            return;
        }

        mRows.removeAllViews();
        mRows.setMinimumHeight(0);
        boolean isFirst = true;
        if (groups != null) {
            for (MediaGroup group : groups) {
                List<Video> videos = toVideos(group);
                if (videos.isEmpty() || mRows.getChildCount() >= 5) {
                    continue;
                }
                String title = isFirst || TextUtils.isEmpty(group.getTitle()) ?
                        mContext.getString(R.string.hometube_search_results_for, query) : group.getTitle();
                mRows.addView(createRow(title, videos));
                isFirst = false;
            }
        }

        if (mRows.getChildCount() == 0) {
            mRows.addView(createRowTitle(mContext.getString(R.string.hometube_search_no_results)));
            return;
        }

        if (focusResults) {
            // After the key that submitted is fully released, so it doesn't open the first result
            long delay = Math.max(0, 600 - (System.currentTimeMillis() - mSubmitTimeMs));
            mRows.postDelayed(() -> {
                View first = findFirstCard();
                if (first != null && isActive() && TextUtils.equals(query, mSubmittedQuery)) {
                    first.requestFocus();
                }
            }, delay);
        } else {
            mRows.post(() -> scrollToFocus(currentFocus())); // stay at the filters
        }
    }

    private View findFirstCard() {
        if (mRows.getChildCount() == 0) {
            return null;
        }
        View row = mRows.getChildAt(0);
        if (row instanceof ViewGroup && ((ViewGroup) row).getChildCount() > 1) {
            View grid = ((ViewGroup) row).getChildAt(1);
            if (grid instanceof ViewGroup && ((ViewGroup) grid).getChildCount() > 0) {
                return ((ViewGroup) grid).getChildAt(0);
            }
            return grid;
        }
        return null;
    }

    private void clearResults() {
        mSubmittedQuery = null;
        mSearchOptions = 0;
        if (mResultsAction != null) {
            mResultsAction.dispose();
        }
        mRows.removeAllViews();
        ((View) mChips.getParent()).setVisibility(View.GONE);
        mResults.setVisibility(View.GONE);
    }

    private static List<Video> toVideos(MediaGroup group) {
        List<Video> videos = new ArrayList<>();
        if (group == null || group.getType() == MediaGroup.TYPE_SHORTS || group.getMediaItems() == null ||
                "shorts".equalsIgnoreCase(group.getTitle() != null ? group.getTitle().trim() : null)) {
            return videos; // HomeTube hides Shorts
        }
        for (Video video : VideoGroup.from(group).getVideos()) {
            if (!video.isShorts) {
                videos.add(video);
            }
        }
        return videos;
    }

    // ---------- Videos for the last search (shown while the field is empty) ----------

    private void loadVideos(String query) {
        loadVideos(query, true);
    }

    private void loadVideos(String query, boolean retry) {
        mVideosAction = YouTubeServiceManager.instance().getContentService().getSearchObserve(query)
                .subscribe(groups -> mRows.post(() -> {
                    for (MediaGroup group : groups != null ? groups : new ArrayList<MediaGroup>()) {
                        List<Video> videos = toVideos(group);
                        if (videos.size() >= 3) {
                            mRecentTitle = mContext.getString(R.string.hometube_search_recent_videos, query);
                            mRecentVideos = videos;
                            showRecentVideos();
                            return;
                        }
                    }
                }), error -> {
                    if (retry) { // one more try, otherwise the page works without the row
                        mHandler.postDelayed(() -> {
                            if (isActive()) {
                                loadVideos(query, false);
                            }
                        }, 1_500);
                    }
                });
    }

    private void showRecentVideos() {
        if (mSubmittedQuery != null || !getQuery().trim().isEmpty() || mRecentVideos == null) {
            return;
        }
        mRows.removeAllViews();
        mRows.addView(createRow(mRecentTitle, mRecentVideos));
        ((View) mChips.getParent()).setVisibility(View.GONE);
        mResults.setVisibility(View.VISIBLE);
    }

    // ---------- Rows ----------

    private TextView createRowTitle(String title) {
        TextView view = new TextView(mContext);
        view.setText(title);
        view.setTextColor(TEXT_COLOR);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setPadding(0, dp(16), 0, dp(12));
        return view;
    }

    private View createRow(String title, List<Video> videos) {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.addView(createRowTitle(title));

        HorizontalGridView grid = new HorizontalGridView(mContext);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setHorizontalSpacing(mContext.getResources().getDimensionPixelSize(R.dimen.grid_item_horizontal_spacing));
        grid.setWindowAlignment(HorizontalGridView.WINDOW_ALIGN_LOW_EDGE);
        grid.setWindowAlignmentOffsetPercent(0);
        grid.setItemAlignmentOffsetPercent(0);
        grid.setPadding(0, dp(8), dp(48), dp(8));

        ArrayObjectAdapter adapter = new ArrayObjectAdapter(new RowVideoCardPresenter());
        adapter.addAll(0, videos);
        ItemBridgeAdapter bridge = new ItemBridgeAdapter(adapter);
        bridge.setAdapterListener(new ItemBridgeAdapter.AdapterListener() {
            @Override
            public void onBind(ItemBridgeAdapter.ViewHolder viewHolder) {
                viewHolder.itemView.setActivated(true); // cards show their text only when activated (normally set by the row)
                viewHolder.itemView.setOnClickListener(v -> {
                    Object item = viewHolder.getItem();
                    if (item instanceof Video) {
                        // Video, channel or playlist; back returns to this page with the results
                        com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter.instance(mContext).apply((Video) item);
                    }
                });
                viewHolder.itemView.setOnLongClickListener(v -> {
                    Object item = viewHolder.getItem();
                    if (item instanceof Video) {
                        com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.instance(mContext).showMenu((Video) item);
                    }
                    return true;
                });
            }
        });
        FocusHighlightHelper.setupBrowseItemFocusHighlight(bridge, FocusHighlight.ZOOM_FACTOR_SMALL, false);
        grid.setAdapter(bridge);

        // Fixed height: the grid is measured before the cards arrive (thumbnail 16:9 + two text blocks)
        float scale = com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData.instance(mContext).getVideoGridScale();
        int cardWidth = (int) (mContext.getResources().getDimensionPixelSize(R.dimen.hometube_row_card_width) * scale);
        row.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cardWidth * 9 / 16 + dp(120)));
        return row;
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

        // Typing again replaces the results with suggestions (like YouTube)
        if (mSubmittedQuery != null && !TextUtils.equals(query, mSubmittedQuery)) {
            clearResults();
        }
        if (mSubmittedQuery == null) {
            if (query.isEmpty()) {
                showRecentVideos();
            } else {
                mResults.setVisibility(View.GONE);
            }
        }

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
            if (!isActive() || !TextUtils.equals(query, getQuery().trim())) {
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
        if (isHistory) {
            item.setOnLongClickListener(v -> {
                showHistoryMenu(text);
                return true;
            });
        }

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
