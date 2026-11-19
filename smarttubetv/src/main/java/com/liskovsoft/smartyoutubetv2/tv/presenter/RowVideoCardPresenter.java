package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.Context;
import android.util.Pair;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * HomeTube: bigger cards in horizontal rows (Home, search results), YouTube TV-like.<br/>
 * Rows may show a part of the next card, so the size isn't stretched to fill the screen like in grids.
 */
public class RowVideoCardPresenter extends VideoCardPresenter {
    @Override
    protected Pair<Integer, Integer> getCardDimensPx(Context context) {
        if (!context.getResources().getBoolean(R.bool.hometube_rail_actions)) {
            return super.getCardDimensPx(context);
        }

        float scale = MainUIData.instance(context).getVideoGridScale();
        int width = Math.round(context.getResources().getDimensionPixelSize(R.dimen.hometube_row_card_width) * scale);
        int height = Math.round(width * 9f / 16f); // thumbnails are 16:9

        return new Pair<>(width, height);
    }
}
