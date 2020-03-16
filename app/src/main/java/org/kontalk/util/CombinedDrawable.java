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

package org.kontalk.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

public class CombinedDrawable extends Drawable implements Drawable.Callback {

    private Drawable background;
    private Drawable icon;
    private int left;
    private int top;
    private int iconWidth;
    private int iconHeight;
    private int backWidth;
    private int backHeight;
    private boolean fullSize;

    public CombinedDrawable(Drawable backgroundDrawable, Drawable iconDrawable, int leftOffset, int topOffset) {
        background = backgroundDrawable;
        icon = iconDrawable;
        left = leftOffset;
        top = topOffset;
        iconDrawable.setCallback(this);
    }

    public void setIconSize(int width, int height) {
        iconWidth = width;
        iconHeight = height;
    }

    public CombinedDrawable(Drawable backgroundDrawable, Drawable iconDrawable) {
        background = backgroundDrawable;
        icon = iconDrawable;
        iconDrawable.setCallback(this);
    }

    public void setCustomSize(int width, int height) {
        backWidth = width;
        backHeight = height;
    }

    public Drawable getIcon() {
        return icon;
    }

    public Drawable getBackground() {
        return background;
    }

    public void setFullsize(boolean value) {
        fullSize = value;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        icon.setColorFilter(colorFilter);
    }

    @Override
    public boolean isStateful() {
        return icon.isStateful();
    }

    @Override
    public boolean setState(int[] stateSet) {
        icon.setState(stateSet);
        return true;
    }

    @Override
    public int[] getState() {
        return icon.getState();
    }

    @Override
    protected boolean onStateChange(int[] state) {
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void jumpToCurrentState() {
        icon.jumpToCurrentState();
    }

    @Override
    public ConstantState getConstantState() {
        return icon.getConstantState();
    }

    @Override
    public void draw(Canvas canvas) {
        background.setBounds(getBounds());
        background.draw(canvas);
        int x;
        int y;
        if (fullSize) {
            icon.setBounds(getBounds());
        } else {
            if (iconWidth != 0) {
                x = getBounds().centerX() - iconWidth / 2 + left;
                y = getBounds().centerY() - iconHeight / 2 + top;
                icon.setBounds(x, y, x + iconWidth, y + iconHeight);
            } else {
                x = getBounds().centerX() - icon.getIntrinsicWidth() / 2 + left;
                y = getBounds().centerY() - icon.getIntrinsicHeight() / 2 + top;
                icon.setBounds(x, y, x + icon.getIntrinsicWidth(), y + icon.getIntrinsicHeight());
            }
        }
        icon.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        icon.setAlpha(alpha);
        background.setAlpha(alpha);
    }

    @Override
    public int getIntrinsicWidth() {
        return backWidth != 0 ? backWidth : background.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return backHeight != 0 ? backHeight : background.getIntrinsicHeight();
    }

    @Override
    public int getMinimumWidth() {
        return backWidth != 0 ? backWidth : background.getMinimumWidth();
    }

    @Override
    public int getMinimumHeight() {
        return backHeight != 0 ? backHeight : background.getMinimumHeight();
    }

    @Override
    public int getOpacity() {
        return icon.getOpacity();
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        unscheduleSelf(what);
    }
}

