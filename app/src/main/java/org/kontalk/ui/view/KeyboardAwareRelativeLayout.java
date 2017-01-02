/**
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.kontalk.ui.view;

import java.lang.reflect.Field;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.kontalk.R;


/**
 * RelativeLayout that, when a view container, will report back when it thinks a soft keyboard
 * has been opened and what its height would be.
 * https://github.com/WhisperSystems/TextSecure
 */
public class KeyboardAwareRelativeLayout extends FrameLayout {
    private static final Rect   rect = new Rect();
    private boolean mKeyboardVisible;

    private OnKeyboardShownListener mListener;

    public KeyboardAwareRelativeLayout(Context context) {
        super(context);
    }

    public KeyboardAwareRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public KeyboardAwareRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setOnKeyboardShownListener(OnKeyboardShownListener listener) {
        mListener = listener;
    }

    /**
     * inspired by http://stackoverflow.com/a/7104303
     * @param widthMeasureSpec width measure
     * @param heightMeasureSpec height measure
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int res = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int statusBarHeight = res > 0 ? getResources().getDimensionPixelSize(res) : 0;

        View rootView = getRootView();
        final int availableHeight = rootView.getHeight() - (rect.top != 0 ? statusBarHeight : 0) - getViewInset(rootView);
        getWindowVisibleDisplayFrame(rect);

        final int keyboardHeight = availableHeight - (rect.bottom - rect.top);

        if (keyboardHeight > getResources().getDimensionPixelSize(R.dimen.min_emoji_drawer_height)) {
            onKeyboardShown(keyboardHeight);
            mKeyboardVisible = true;
        }
        else {
            mKeyboardVisible = false;
        }

        if (mListener != null)
            mListener.onKeyboardShown(mKeyboardVisible);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    // waiting for a fix: https://code.google.com/p/android/issues/detail?id=88256
    public static int getViewInset(View view) {
        if (view == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return 0;
        }
        try {
            Field mAttachInfoField = View.class.getDeclaredField("mAttachInfo");
            mAttachInfoField.setAccessible(true);
            Object mAttachInfo = mAttachInfoField.get(view);
            if (mAttachInfo != null) {
                Field mStableInsetsField = mAttachInfo.getClass().getDeclaredField("mStableInsets");
                mStableInsetsField.setAccessible(true);
                Rect insets = (Rect)mStableInsetsField.get(mAttachInfo);
                return insets.bottom;
            }
        }
        catch (Exception ignored) {
        }
        return 0;
    }

    protected void onKeyboardShown(int keyboardHeight) {
        WindowManager wm = (WindowManager) getContext().getSystemService(Activity.WINDOW_SERVICE);
        if (wm == null || wm.getDefaultDisplay() == null) {
            return;
        }
        int rotation = wm.getDefaultDisplay().getRotation();

        switch (rotation) {
            case Surface.ROTATION_270:
            case Surface.ROTATION_90:
                setKeyboardLandscapeHeight(keyboardHeight);
                break;
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                setKeyboardPortraitHeight(keyboardHeight);
        }
    }

    public boolean isKeyboardVisible() {
        return mKeyboardVisible;
    }

    public int getKeyboardHeight() {
        WindowManager      wm    = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
        if (wm == null || wm.getDefaultDisplay() == null) {
            throw new AssertionError("WindowManager was null or there is no default display");
        }

        int rotation = wm.getDefaultDisplay().getRotation();

        switch (rotation) {
            case Surface.ROTATION_270:
            case Surface.ROTATION_90:
                return getKeyboardLandscapeHeight();
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
            default:
                return getKeyboardPortraitHeight();
        }
    }

    public int getKeyboardLandscapeHeight() {
        return PreferenceManager.getDefaultSharedPreferences(getContext())
            .getInt("keyboard_height_landscape",
                getResources().getDimensionPixelSize(R.dimen.emoji_drawer_default_height));
    }

    public int getKeyboardPortraitHeight() {
        return PreferenceManager.getDefaultSharedPreferences(getContext())
            .getInt("keyboard_height_portrait",
                getResources().getDimensionPixelSize(R.dimen.emoji_drawer_default_height));
    }

    private void setKeyboardLandscapeHeight(int height) {
        SharedPreferences.Editor editor = PreferenceManager
            .getDefaultSharedPreferences(getContext())
            .edit().putInt("keyboard_height_landscape", height);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD)
            editor.commit();
        else
            editor.apply();
    }

    private void setKeyboardPortraitHeight(int height) {
        SharedPreferences.Editor editor = PreferenceManager
            .getDefaultSharedPreferences(getContext())
            .edit().putInt("keyboard_height_portrait", height);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD)
            editor.commit();
        else
            editor.apply();
    }

    public interface OnKeyboardShownListener {
        public void onKeyboardShown(boolean visible);
    }

}
