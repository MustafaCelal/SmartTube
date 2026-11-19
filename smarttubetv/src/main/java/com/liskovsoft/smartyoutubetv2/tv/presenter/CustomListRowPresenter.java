package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.util.TypedValue;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class CustomListRowPresenter extends ListRowPresenter {
    public CustomListRowPresenter() {
        super(ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.FOCUS_DIMMER_ENABLED);
        setSelectEffectEnabled(ViewUtil.ROW_SELECT_EFFECT_ENABLED);
    }

    @Override
    protected void initializeRowViewHolder(RowPresenter.ViewHolder holder) {
        TypedValue railWidth = new TypedValue();
        boolean isHomeTube = holder.view.getContext().getTheme().resolveAttribute(R.attr.browseCollapsedHeadersWidth, railWidth, true);
        if (isHomeTube) {
            setShadowEnabled(false); // HomeTube: flat cards, no dark box around the focused one
        }

        super.initializeRowViewHolder(holder);

        // HomeTube: cards scrolled to the left fade out next to the sidebar rail instead of being cut off
        if (isHomeTube) {
            HorizontalGridView gridView = ((ListRowPresenter.ViewHolder) holder).getGridView();
            int fadeLength = gridView.getPaddingStart();
            if (fadeLength > 0) {
                gridView.setFadingLeftEdge(true);
                gridView.setFadingLeftEdgeLength(fadeLength);
                gridView.setFadingLeftEdgeOffset(0);
            }
        }
    }
}
