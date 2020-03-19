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
import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.util.ViewUtils;

/**
 * Gray divider row
 * @author andreacappelli
 */

public class GrayDividerRow extends FrameLayout {

    private TextView mTextView;

    public GrayDividerRow(Context context) {
        this(context, null);
    }

    public GrayDividerRow(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GrayDividerRow(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public GrayDividerRow(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        init();
    }

    private void init() {
        inflate(getContext(), R.layout.gray_divider_row, this);

        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.gray_background));

        mTextView = findViewById(R.id.text);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ViewUtils.dp(getContext(), 32), MeasureSpec.EXACTLY));
    }

    public void setText(String text) {
        mTextView.setText(text);
    }

}

