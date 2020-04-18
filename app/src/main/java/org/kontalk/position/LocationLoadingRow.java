/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.position;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.TextView;

import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

import org.kontalk.R;
import org.kontalk.util.ViewUtils;


/**
 * Location loading row
 *
 * @author Andrea Cappelli
 */
public class LocationLoadingRow extends FrameLayout {

    private MaterialProgressBar mProgressBar;
    private TextView mText;

    public LocationLoadingRow(Context context) {
        this(context, null);
    }

    public LocationLoadingRow(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LocationLoadingRow(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public LocationLoadingRow(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.location_loading_row, this);

        mProgressBar = findViewById(R.id.progress_bar);
        mText = findViewById(R.id.text);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (ViewUtils.dp(getContext(), 56) * 2.5f), MeasureSpec.EXACTLY));
    }

    public void setLoading(boolean value) {
        mProgressBar.setVisibility(value ? VISIBLE : INVISIBLE);
        mText.setVisibility(value ? INVISIBLE : VISIBLE);
    }

}
