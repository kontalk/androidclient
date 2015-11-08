/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Antonis Kalipetis <akalipetis@gmail.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.akalipetis.fragment;

import android.os.Build;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;

/**
 * Created by Antonis Kalipetis on 01.08.2013.
 */
class ActionModeWrapper extends ActionMode {

    private android.view.ActionMode mode;
    private ActionMode modeCompat;

    ActionModeWrapper(android.view.ActionMode mode) {
        this.mode = mode;
    }

    ActionModeWrapper(ActionMode modeCompat) {
        this.modeCompat = modeCompat;
    }

    @Override
    public void setTitle(CharSequence charSequence) {
        if (Build.VERSION.SDK_INT < 11) modeCompat.setTitle(charSequence);
        else mode.setTitle(charSequence);
    }

    @Override
    public void setSubtitle(CharSequence charSequence) {
        if (Build.VERSION.SDK_INT < 11) modeCompat.setSubtitle(charSequence);
        else mode.setSubtitle(charSequence);
    }

    @Override
    public void invalidate() {
        if (Build.VERSION.SDK_INT < 11) modeCompat.invalidate();
        else mode.invalidate();
    }

    @Override
    public void finish() {
        if (Build.VERSION.SDK_INT < 11) modeCompat.finish();
        else mode.finish();
    }

    @Override
    public Menu getMenu() {
        if (Build.VERSION.SDK_INT < 11) return modeCompat.getMenu();
        else return mode.getMenu();
    }

    @Override
    public CharSequence getTitle() {
        if (Build.VERSION.SDK_INT < 11) return modeCompat.getTitle();
        else return mode.getTitle();
    }

    @Override
    public void setTitle(int i) {
        if (Build.VERSION.SDK_INT < 11) modeCompat.setTitle(i);
        else mode.setTitle(i);
    }

    @Override
    public CharSequence getSubtitle() {
        if (Build.VERSION.SDK_INT < 11) return modeCompat.getSubtitle();
        else return mode.getSubtitle();
    }

    @Override
    public void setSubtitle(int i) {
        if (Build.VERSION.SDK_INT < 11) modeCompat.setSubtitle(i);
        else mode.setSubtitle(i);
    }

    @Override
    public View getCustomView() {
        if (Build.VERSION.SDK_INT < 11) return modeCompat.getCustomView();
        else return mode.getCustomView();
    }

    @Override
    public void setCustomView(View view) {
        if (Build.VERSION.SDK_INT < 11) modeCompat.setCustomView(view);
        else mode.setCustomView(view);
    }

    @Override
    public MenuInflater getMenuInflater() {
        if (Build.VERSION.SDK_INT < 11) return modeCompat.getMenuInflater();
        else return mode.getMenuInflater();
    }
}
