package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * HomeTube: "More" button of the player, opens a menu with the less used actions.
 */
public class MoreAction extends Action {
    public MoreAction(Context context) {
        super(R.id.action_more);
        setIcon(ContextCompat.getDrawable(context, R.drawable.hometube_action_more));
        setLabel1(context.getString(R.string.hometube_more));
    }
}
