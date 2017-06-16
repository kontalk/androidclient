package org.kontalk.util;

import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.view.View;

/**
 * @author Andrea Cappelli
 *         An empty header decoration for RecyclerView, since RecyclerView can't clipToPadding
 */
public class SimpleHeaderDecoration extends RecyclerView.ItemDecoration {
    private final int headerHeight;

    public SimpleHeaderDecoration(int headerHeight) {
        this.headerHeight = headerHeight;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
        RecyclerView.State state) {
        int childAdapterPosition = parent.getChildAdapterPosition(view);
        if (childAdapterPosition == 0) {
            outRect.top = headerHeight;
        }
        else {
            outRect.set(0, 0, 0, 0);
        }
    }
}