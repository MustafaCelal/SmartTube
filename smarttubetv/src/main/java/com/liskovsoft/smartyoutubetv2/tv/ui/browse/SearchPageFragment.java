package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.BrowseSupportFragment;

import com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog.InlineSearchDialog;

/**
 * HomeTube: Search as a page of the sidebar (YouTube TV-like).<br/>
 * Shown as soon as the sidebar moves onto Search; right from the sidebar enters on the mic,
 * right again is the field. Recent searches, suggestions and results live on this page.
 */
public class SearchPageFragment extends Fragment implements BrowseSupportFragment.MainFragmentAdapterProvider {
    private InlineSearchDialog mPage;
    private final BrowseSupportFragment.MainFragmentAdapter<Fragment> mMainFragmentAdapter =
            new BrowseSupportFragment.MainFragmentAdapter<Fragment>(this) {
                @Override
                public void setEntranceTransitionState(boolean state) {
                    // NOP
                }
            };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mPage = InlineSearchDialog.createPage(getContext());
        return mPage.getView();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Tell the browse fragment the page is ready (no loading)
        if (mMainFragmentAdapter.getFragmentHost() != null) {
            mMainFragmentAdapter.getFragmentHost().notifyViewCreated(mMainFragmentAdapter);
            mMainFragmentAdapter.getFragmentHost().notifyDataReady(mMainFragmentAdapter);
        }
    }

    @Override
    public void onDestroyView() {
        if (mPage != null) {
            mPage.release();
            mPage = null;
        }
        super.onDestroyView();
    }

    @Override
    public BrowseSupportFragment.MainFragmentAdapter<Fragment> getMainFragmentAdapter() {
        return mMainFragmentAdapter;
    }
}
