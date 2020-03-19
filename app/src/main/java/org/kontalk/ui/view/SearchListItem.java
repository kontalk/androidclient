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

import org.kontalk.data.SearchItem;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import android.widget.TextView;


public class SearchListItem extends RelativeLayout {

    private SearchItem mFound;
    private TextView mText1;
    private TextView mText2;

    public SearchListItem(Context context) {
        super(context);
    }

    public SearchListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mText1 = findViewById(android.R.id.text1);
        mText2 = findViewById(android.R.id.text2);

        if (isInEditMode()) {
            mText1.setText("Test contact");
            mText2.setText("...hello buddy! How...");
        }
    }

    public final void bind(Context context, final SearchItem found) {
        mFound = found;
        mText1.setText(found.getUserDisplayName());
        mText2.setText(found.getText());
    }

    public final void unbind() {
        // TODO unbind (?)
    }

    public SearchItem getSearchItem() {
        return mFound;
    }

}
