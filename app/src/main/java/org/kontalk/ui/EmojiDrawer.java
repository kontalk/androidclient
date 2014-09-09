/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.ui;

import com.rockerhieu.emojicon.EmojiconsFragment;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;


/**
 * An emoji drawer.
 * @author Daniele Ricci
 */
public class EmojiDrawer extends KeyboardAwareFrameLayout {

    private FragmentManager mFragmentManager;

    public EmojiDrawer(Context context) {
        super(context);
    }

    public EmojiDrawer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public EmojiDrawer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public boolean isVisible() {
        return getVisibility() == View.VISIBLE;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mFragmentManager != null) {
            Fragment f = mFragmentManager.findFragmentById(getId());
            if (f != null) {
                try {
                    // remove fragment
                    mFragmentManager.beginTransaction()
                        .remove(f)
                        .commit();
                }
                catch (IllegalStateException e) {
                    // workaround for #209 (this will be removed anyway)
                }
            }
        }
    }

    public void hide() {
        setVisibility(View.GONE);
    }

    public void show(FragmentManager fm) {
        mFragmentManager = fm;

        int fragmentId = getId();
        Fragment f = fm.findFragmentById(fragmentId);
        if (f == null) {
            // add fragment
            f = new EmojiconsFragment();
            fm.beginTransaction()
                .replace(fragmentId, f)
                .commit();
        }

        int keyboardHeight = getKeyboardHeight();
        setLayoutParams(new LinearLayout.LayoutParams(LinearLayout
            .LayoutParams.MATCH_PARENT, keyboardHeight));
        requestLayout();
        setVisibility(View.VISIBLE);
    }

}
