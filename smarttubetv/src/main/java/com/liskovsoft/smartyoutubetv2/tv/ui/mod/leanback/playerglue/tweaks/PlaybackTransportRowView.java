/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.RestrictTo;
import androidx.leanback.R;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

/**
 * View for PlaybackTransportRowPresenter that has a custom focusSearch.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class PlaybackTransportRowView extends LinearLayout {

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public interface OnUnhandledKeyListener {
        /**
         * Returns true if the key event should be consumed.
         */
        boolean onUnhandledKey(KeyEvent event);
    }

    private OnUnhandledKeyListener mOnUnhandledKeyListener;

    public PlaybackTransportRowView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PlaybackTransportRowView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    void setOnUnhandledKeyListener(OnUnhandledKeyListener listener) {
        mOnUnhandledKeyListener = listener;
    }

    OnUnhandledKeyListener getOnUnhandledKeyListener() {
        return mOnUnhandledKeyListener;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (super.dispatchKeyEvent(event)) {
            return true;
        }
        return mOnUnhandledKeyListener != null && mOnUnhandledKeyListener.onUnhandledKey(event);
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        final View focused = findFocus();
        if (focused != null && focused.requestFocus(direction, previouslyFocusedRect)) {
            return true;
        }
        // MOD (HomeTube): start on play/pause, so LEFT/RIGHT don't seek by surprise
        if (getResources().getBoolean(com.liskovsoft.smartyoutubetv2.tv.R.bool.hometube_rail_actions)) {
            View primary = findViewById(R.id.controls_dock);
            if (primary != null && primary.requestFocus(direction, previouslyFocusedRect)) {
                return true;
            }
        }
        View progress = findViewById(R.id.playback_progress);
        if (progress != null && progress.isFocusable()) {
            if (progress.requestFocus(direction, previouslyFocusedRect)) {
                return true;
            }
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    /**
     * MOD (HomeTube): primary and secondary controls may share one row (single control row layout).
     * Move between them with LEFT/RIGHT instead of jumping to the progress bar.
     */
    private View findCrossDockFocus(View focused, int direction) {
        ViewGroup primary = findViewById(R.id.controls_dock);
        ViewGroup secondary = findViewById(R.id.secondary_controls_dock);

        if (primary == null || secondary == null || primary.getParent() != secondary.getParent()) {
            return null; // docks are in different rows (default layout)
        }

        ViewGroup from = isDescendant(primary, focused) ? primary : isDescendant(secondary, focused) ? secondary : null;
        if (from == null) {
            return null;
        }

        View next = FocusFinder.getInstance().findNextFocus(from, focused, direction);
        if (next != null) {
            return next; // stay inside the current dock
        }

        if (from == primary && direction == View.FOCUS_RIGHT) {
            return findEdgeFocusable(secondary, true);
        } else if (from == secondary && direction == View.FOCUS_LEFT) {
            return findEdgeFocusable(primary, false);
        }

        return null;
    }

    private static boolean isDescendant(ViewGroup parent, View view) {
        for (View v = view; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v == parent) {
                return true;
            }
        }
        return false;
    }

    private static View findEdgeFocusable(ViewGroup dock, boolean first) {
        java.util.ArrayList<View> focusables = new java.util.ArrayList<>();
        dock.addFocusables(focusables, View.FOCUS_FORWARD);
        if (focusables.isEmpty()) {
            return null;
        }
        View result = focusables.get(0);
        for (View v : focusables) {
            if (v.getVisibility() != View.VISIBLE) {
                continue;
            }
            int[] pos = new int[2];
            int[] best = new int[2];
            v.getLocationOnScreen(pos);
            result.getLocationOnScreen(best);
            if (first ? pos[0] < best[0] : pos[0] > best[0]) {
                result = v;
            }
        }
        return result;
    }

    @Override
    public View focusSearch(View focused, int direction) {
        // when focusSearch vertically, return the next immediate focusable child
        if (focused != null) {
            if (direction == View.FOCUS_UP) {
                int index = indexOfChild(getFocusedChild());
                for (index = index - 1; index >= 0; index--) {
                    View view = getChildAt(index);
                    if (view.hasFocusable()) {
                        return view;
                    }
                }
            } else if (direction == View.FOCUS_DOWN) {
                int index = indexOfChild(getFocusedChild());
                for (index = index + 1; index < getChildCount(); index++) {
                    View view = getChildAt(index);
                    if (view.hasFocusable()) {
                        return view;
                    }
                }
            } else if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                View crossDock = findCrossDockFocus(focused, direction);
                if (crossDock != null) {
                    return crossDock;
                }

                if (getFocusedChild() instanceof ViewGroup) {
                    return FocusFinder.getInstance().findNextFocus(
                            (ViewGroup) getFocusedChild(), focused, direction);
                }
            }
        }
        return super.focusSearch(focused, direction);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
