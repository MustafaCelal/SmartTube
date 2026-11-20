/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.leanback.R;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.PaddingAction;

/**
 * Displays primary and secondary controls for a {@link PlaybackControlsRow}.
 *
 * Binds to items of type {@link Action}.
 */
public class ControlButtonPresenterSelector extends PresenterSelector {
    private final ControlButtonPresenter mPrimaryPresenter =
            new ControlButtonPresenter(R.layout.lb_control_button_primary);
    private final ControlButtonPresenter mSecondaryPresenter =
            new ControlButtonPresenter(R.layout.lb_control_button_secondary);
    private final Presenter[] mPresenters = new Presenter[]{mPrimaryPresenter};

    public ControlButtonPresenterSelector(boolean tooltipsEnabled) {
        mPrimaryPresenter.tooltipsEnabled = mSecondaryPresenter.tooltipsEnabled = tooltipsEnabled;
    }

    /**
     * Returns the presenter for primary controls.
     */
    public Presenter getPrimaryPresenter() {
        return mPrimaryPresenter;
    }

    /**
     * Returns the presenter for secondary controls.
     */
    public Presenter getSecondaryPresenter() {
        return mSecondaryPresenter;
    }

    /**
     * Always returns the presenter for primary controls.
     */
    @Override
    public Presenter getPresenter(Object item) {
        return mPrimaryPresenter;
    }

    @Override
    public Presenter[] getPresenters() {
        return mPresenters;
    }

    static class ActionViewHolder extends Presenter.ViewHolder {
        ImageView mIcon;
        TextView mLabel;
        View mFocusableView;
        TextView mCaption; // HomeTube: name (focused) or value (speed, quality) under the button
        Action mAction;

        public ActionViewHolder(View view) {
            super(view);
            mIcon = (ImageView) view.findViewById(R.id.icon);
            mLabel = (TextView) view.findViewById(R.id.label);
            mFocusableView = view.findViewById(R.id.button);
        }
    }

    public static class ControlButtonPresenter extends Presenter {
        private int mLayoutResourceId;
        private boolean tooltipsEnabled;

        ControlButtonPresenter(int layoutResourceId) {
            mLayoutResourceId = layoutResourceId;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(mLayoutResourceId, parent, false);

            if (isHomeTube(parent.getContext())) {
                return createHomeTubeHolder(v);
            }

            return new ActionViewHolder(v);
        }

        private static boolean isHomeTube(android.content.Context context) {
            return context.getResources().getBoolean(com.liskovsoft.smartyoutubetv2.tv.R.bool.hometube_rail_actions);
        }

        /**
         * HomeTube: white circle with a black icon on focus (like the sidebar), the button name under it.
         */
        private ActionViewHolder createHomeTubeHolder(View button) {
            android.content.Context context = button.getContext();
            float density = context.getResources().getDisplayMetrics().density;
            int captionWidth = (int) (160 * density);
            int captionHeight = (int) (22 * density);

            android.widget.FrameLayout wrapper = new android.widget.FrameLayout(context);
            wrapper.setClipChildren(false);
            wrapper.setClipToPadding(false);
            wrapper.setPadding(0, 0, 0, captionHeight);
            wrapper.addView(button);

            int buttonWidth = button.getLayoutParams() != null && button.getLayoutParams().width > 0 ?
                    button.getLayoutParams().width : context.getResources().getDimensionPixelSize(R.dimen.lb_control_button_diameter);
            TextView caption = new TextView(context);
            caption.setGravity(android.view.Gravity.CENTER);
            caption.setSingleLine(true);
            caption.setEllipsize(TextUtils.TruncateAt.END);
            caption.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            caption.setTextColor(0xFFFFFFFF);
            caption.setShadowLayer(4 * density, 0, 0, 0xCC000000);
            // Wider than the button without making the button wider (negative margins)
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                    captionWidth, captionHeight, android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            int overhang = (captionWidth - buttonWidth) / 2;
            params.leftMargin = -overhang;
            params.rightMargin = -overhang;
            params.bottomMargin = -captionHeight;
            wrapper.addView(caption, params);

            ActionViewHolder holder = new ActionViewHolder(wrapper);
            holder.mCaption = caption;

            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(0xFFFFFFFF);
            android.graphics.drawable.StateListDrawable background = new android.graphics.drawable.StateListDrawable();
            background.addState(new int[] {android.R.attr.state_focused}, circle);
            background.addState(new int[] {}, new android.graphics.drawable.ColorDrawable(0));
            if (holder.mFocusableView instanceof ImageView) {
                ((ImageView) holder.mFocusableView).setImageDrawable(null);
                ((ImageView) holder.mFocusableView).setScaleType(ImageView.ScaleType.FIT_XY);
            }
            holder.mFocusableView.setBackground(background);
            holder.mFocusableView.setOnFocusChangeListener((v, hasFocus) -> updateHomeTubeState(holder, hasFocus));

            return holder;
        }

        private static void updateHomeTubeState(ActionViewHolder holder, boolean hasFocus) {
            if (holder.mIcon != null) {
                boolean isBitmap = holder.mIcon.getDrawable() instanceof android.graphics.drawable.BitmapDrawable &&
                        holder.mAction != null && holder.mAction.getId() == com.liskovsoft.smartyoutubetv2.tv.R.id.action_channel;
                if (hasFocus && !isBitmap) {
                    holder.mIcon.setColorFilter(0xFF000000, android.graphics.PorterDuff.Mode.SRC_IN);
                } else {
                    holder.mIcon.clearColorFilter();
                }
            }

            if (holder.mCaption != null && holder.mAction != null) {
                CharSequence name = holder.mAction.getLabel1();
                CharSequence value = holder.mAction.getLabel2();
                CharSequence text;
                if (hasFocus) {
                    text = !TextUtils.isEmpty(value) ? name + " \u00B7 " + value : name;
                } else {
                    text = value; // speed / quality stay visible
                }
                holder.mCaption.setText(text);
                holder.mCaption.setVisibility(TextUtils.isEmpty(text) ? View.INVISIBLE : View.VISIBLE);
            }
        }

        // Used inside: com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.ControlBarPresenter.ViewHolder.bindControlToAction()
        // Restore focus: com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.ControlBar.onRequestFocusInDescendants()
        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            Action action = (Action) item;
            ActionViewHolder vh = (ActionViewHolder) viewHolder;
            vh.mAction = action;

            vh.mIcon.setImageDrawable(action.getIcon());
            if (action instanceof PaddingAction) {
                int padding = ((PaddingAction) action).getPadding();
                if (padding > 0) {
                    vh.mIcon.setPadding(padding, padding, padding, padding);
                }
            }
            if (vh.mLabel != null) {
                if (action.getIcon() == null) {
                    vh.mLabel.setText(action.getLabel1());
                } else {
                    vh.mLabel.setText(null);
                }
            }
            CharSequence contentDescription = TextUtils.isEmpty(action.getLabel2())
                    ? action.getLabel1() : action.getLabel2();
            if (!TextUtils.equals(vh.mFocusableView.getContentDescription(), contentDescription)) {
                vh.mFocusableView.setContentDescription(contentDescription);
                vh.mFocusableView.sendAccessibilityEvent(
                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);

                // MOD: enable control tooltips
                if (tooltipsEnabled && vh.mCaption == null) {
                    TooltipCompatHandler.setTooltipText(vh.mFocusableView, action.getLabel1());
                }
            }

            if (vh.mCaption != null) {
                updateHomeTubeState(vh, vh.mFocusableView.hasFocus());
                // captions are wider than buttons: don't cut them at the control bar / dock edges
                android.view.ViewParent parent = vh.view.getParent();
                for (int i = 0; i < 3 && parent instanceof ViewGroup; i++) {
                    ((ViewGroup) parent).setClipChildren(false);
                    ((ViewGroup) parent).setClipToPadding(false);
                    parent = parent.getParent();
                }
            }
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
            ActionViewHolder vh = (ActionViewHolder) viewHolder;
            vh.mIcon.setImageDrawable(null);
            if (vh.mLabel != null) {
                vh.mLabel.setText(null);
            }
            vh.mFocusableView.setContentDescription(null);
        }

        @Override
        public void setOnClickListener(ViewHolder viewHolder,
                                       View.OnClickListener listener) {
            ((ActionViewHolder) viewHolder).mFocusableView.setOnClickListener(listener);
        }

        public void setOnLongClickListener(ViewHolder viewHolder,
                                       View.OnLongClickListener listener) {
            ((ActionViewHolder) viewHolder).mFocusableView.setOnLongClickListener(listener);
        }
    }
}
