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

package org.kontalk.ui.view;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import android.util.AttributeSet;
import android.widget.Checkable;

import de.hdodenhof.circleimageview.CircleImageView;

import org.kontalk.R;


/**
 * A simple image view to handle checked state through use of checkbox image.
 * @author Andrea Cappelli
 */
public class CircleCheckBox extends CircleImageView implements Checkable {

    private static final String KEY_INSTANCE_STATE = "InstanceState";

    private boolean mChecked;
    private OnCheckedChangeListener mListener;

    public CircleCheckBox(Context context) {
        this(context, null);
    }

    public CircleCheckBox(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        init();
    }

    public CircleCheckBox(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            Drawable d = VectorDrawableCompat.create(getResources(), R.drawable.ic_checkbox, getContext().getTheme());
            setImageDrawable(d);
        }
        else {
            setImageResource(R.drawable.ic_checkbox);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_INSTANCE_STATE, super.onSaveInstanceState());
        bundle.putBoolean(KEY_INSTANCE_STATE, isChecked());
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            boolean isChecked = bundle.getBoolean(KEY_INSTANCE_STATE);
            setChecked(isChecked);
            super.onRestoreInstanceState(bundle.getParcelable(KEY_INSTANCE_STATE));
            return;
        }
        super.onRestoreInstanceState(state);
    }


    @Override
    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void toggle() {
        this.setChecked(!isChecked());
    }

    @Override
    public void setChecked(boolean checked) {
        setVisibility(checked ? VISIBLE : GONE);
        mChecked = checked;
        if (mListener != null) {
            mListener.onCheckedChanged(CircleCheckBox.this, mChecked);
        }
    }

    public void setOnCheckedChangeListener(CircleCheckBox.OnCheckedChangeListener l) {
        this.mListener = l;
    }

    public interface OnCheckedChangeListener {
        void onCheckedChanged(CircleCheckBox checkBox, boolean isChecked);
    }
}
