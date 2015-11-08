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

import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

/**
 * Created by Antonis Kalipetis on 31.07.2013.
 */
public interface MultiChoiceModeListener {

    public abstract void onItemCheckedStateChanged(ActionMode actionMode, int position, long id, boolean checked);

    public abstract boolean onCreateActionMode(ActionMode actionMode, Menu menu);

    public abstract boolean onPrepareActionMode(ActionMode actionMode, Menu menu);

    public abstract boolean onActionItemClicked(ActionMode actionMode, MenuItem item);

    public abstract void onDestroyActionMode(ActionMode actionMode);
}
