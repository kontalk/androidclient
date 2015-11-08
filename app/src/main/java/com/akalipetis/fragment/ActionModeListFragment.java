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

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AppCompatActivity;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;

/**
 * Created by Antonis Kalipetis on 31.07.2013.
 */
public abstract class ActionModeListFragment extends ListFragment implements AdapterView.OnItemLongClickListener {

    private MultiChoiceModeListener mListener = null;
    private int mLastCheckedItem = ListView.INVALID_POSITION;
    private int mLastChoiceMode = ListView.CHOICE_MODE_NONE;
    private ActionModeWrapper mWrapper;
    private Runnable restoreList = new Runnable() {
        @Override
        public void run() {
            try {
                ListView list = getListView();
                list.setChoiceMode(mLastChoiceMode);
                list.setItemChecked(mLastCheckedItem, true);
            }
            catch (IllegalStateException e) {
                // recreating configuration - just ignore it
            }
        }
    };

    public void setMultiChoiceModeListener(MultiChoiceModeListener listener) {
        mListener = listener;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ListView list = getListView();
        list.setOnItemLongClickListener(this);
        if (Build.VERSION.SDK_INT >= 11) list.setMultiChoiceModeListener(new InternalV11Listener());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mWrapper != null) mWrapper.finish();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        int choiceMode = l.getChoiceMode();
        if (!(choiceMode == ListView.CHOICE_MODE_MULTIPLE)) return;
        boolean checked = l.isItemChecked(position);
        if (mListener != null) mListener.onItemCheckedStateChanged(mWrapper, position, id, checked);
        int checkedCount = calculateCheckedItems();
        if (checkedCount <= 0) {
            if (mWrapper != null) mWrapper.finish();
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        ListView list = getListView();
        int choiceMode = list.getChoiceMode();
        mLastChoiceMode = choiceMode;
        if (choiceMode == ListView.CHOICE_MODE_SINGLE) mLastCheckedItem = list.getCheckedItemPosition();
        else mLastCheckedItem = ListView.INVALID_POSITION;
        if (Build.VERSION.SDK_INT < 11) {
            list.setItemChecked(mLastCheckedItem, false);

            list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            list.setLongClickable(false);
            ((AppCompatActivity) getActivity()).startSupportActionMode(new InternalOlderListener());
            list.setItemChecked(position, true);
            if (mListener != null) mListener.onItemCheckedStateChanged(mWrapper, position, id, true);
        } else {
            list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
            list.setItemChecked(position, true);
        }
        return true;
    }

    public int calculateCheckedItems() {
        ListView l = getListView();
        if (Build.VERSION.SDK_INT >= 11) return l.getCheckedItemCount();
        SparseBooleanArray checkedItems = l.getCheckedItemPositions();
        if (checkedItems == null) return 0;
        int cnt = 0;
        for (int i = 0, lim = checkedItems.size(); i < lim; ++i) {
            int key = checkedItems.keyAt(i);
            if (checkedItems.get(key, false)) cnt++;
        }
        return cnt;
    }

    @SuppressLint ( "NewApi" )
    private class InternalV11Listener implements AbsListView.MultiChoiceModeListener {

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
            if (mListener != null) mListener.onItemCheckedStateChanged(mWrapper, position, id, checked);
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mWrapper = new ActionModeWrapper(mode);
            if (mListener != null) return mListener.onCreateActionMode(mWrapper, menu);
            else return false;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            if (mListener != null) return mListener.onPrepareActionMode(mWrapper, menu);
            else return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (mListener != null) return mListener.onActionItemClicked(mWrapper, item);
            else return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            if (mListener != null) mListener.onDestroyActionMode(mWrapper);
            ListView list = getListView();
            list.post(restoreList);
        }
    }

    private class InternalOlderListener implements android.support.v7.view.ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(android.support.v7.view.ActionMode mode, Menu menu) {
            mWrapper = new ActionModeWrapper(mode);
            if (mListener != null) return mListener.onCreateActionMode(mWrapper, menu);
            else return false;
        }

        @Override
        public boolean onPrepareActionMode(android.support.v7.view.ActionMode mode, Menu menu) {
            if (mListener != null) return mListener.onPrepareActionMode(mWrapper, menu);
            else return false;
        }

        @Override
        public boolean onActionItemClicked(android.support.v7.view.ActionMode mode, MenuItem item) {
            if (mListener != null) return mListener.onActionItemClicked(mWrapper, item);
            else return false;
        }

        @Override
        public void onDestroyActionMode(android.support.v7.view.ActionMode mode) {
            if (mListener != null) mListener.onDestroyActionMode(mWrapper);
            ListView list = getListView();
            list.setLongClickable(true);
            list.clearChoices();
            list.requestLayout();
            list.post(restoreList);
        }
    }
}
