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

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnTouchListener;

import android.widget.PopupWindow;
import android.content.Context;

/**
 * Custom popup window.
 *
 * @author Lorensius W. L. T <lorenz@londatiga.net>
 *
 * @deprecated to be replaced by the bottom popup window
 */
@Deprecated
public class PopupWindows {
    protected Context mContext;
    protected PopupWindow mWindow;
    protected View mRootView;
    protected Drawable mBackground = null;
    protected WindowManager mWindowManager;

    /**
     * Constructor.
     *
     * @param context Context
     */
    public PopupWindows(Context context) {
        mContext    = context;
        mWindow     = new PopupWindow(context);

        mWindow.setTouchInterceptor(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    mWindow.dismiss();

                    return true;
                }

                return false;
            }
        });

        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * On dismiss
     */
    protected void onDismiss() {
    }

    /**
     * On show
     */
    protected void onShow() {
    }

    /**
     * On pre show
     */
    protected void preShow() {
        if (mRootView == null)
            throw new IllegalStateException("setContentView was not called with a view to display.");

        onShow();

        if (mBackground == null)
            mWindow.setBackgroundDrawable(new BitmapDrawable());
        else
            mWindow.setBackgroundDrawable(mBackground);

        mWindow.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
        mWindow.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        mWindow.setTouchable(true);
        mWindow.setFocusable(true);
        mWindow.setOutsideTouchable(true);
        // FIXME workout some better solution
        mWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);

        mWindow.setContentView(mRootView);
    }

    /**
     * Set background drawable.
     *
     * @param background Background drawable
     */
    public void setBackgroundDrawable(Drawable background) {
        mBackground = background;
    }

    /**
     * Set content view.
     *
     * @param root Root view
     */
    public void setContentView(View root) {
        mRootView = root;

        mWindow.setContentView(root);
    }

    /**
     * Set content view.
     *
     * @param layoutResID Resource id
     */
    public void setContentView(int layoutResID) {
        LayoutInflater inflator = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        setContentView(inflator.inflate(layoutResID, null));
    }

    /**
     * Set listener on window dismissed.
     *
     * @param listener
     */
    public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
        mWindow.setOnDismissListener(listener);
    }

    /**
     * Dismiss the popup window.
     */
    public void dismiss() {
        mWindow.dismiss();
    }
}