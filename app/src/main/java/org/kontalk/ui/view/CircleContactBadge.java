/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.AttributeSet;
import android.view.View;

import de.hdodenhof.circleimageview.CircleImageView;


/**
 * A circle image view supporting the quick contact badge.
 * @author Daniele Ricci
 */
public class CircleContactBadge extends CircleImageView implements View.OnClickListener {
    private Uri mContactUri;
    protected String[] mExcludeMimes = null;

    public CircleContactBadge(Context context) {
        this(context, null);
    }

    public CircleContactBadge(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleContactBadge(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setOnClickListener(this);
    }

    public void assignContactUri(Uri contactUri) {
        mContactUri = contactUri;
        onContactUriChanged();
    }

    private void onContactUriChanged() {
        setEnabled(isAssigned());
    }

    private boolean isAssigned() {
        return mContactUri != null;
    }

    @Override
    public void onClick(View v) {
        if (mContactUri != null) {
            ContactsContract.QuickContact.showQuickContact(getContext(), this,
                mContactUri,  ContactsContract.QuickContact.MODE_LARGE, mExcludeMimes);
        }
    }

    /**
     * Set a list of specific MIME-types to exclude and not display. For
     * example, this can be used to hide the {@link ContactsContract.Contacts#CONTENT_ITEM_TYPE}
     * profile icon.
     */
    public void setExcludeMimes(String[] excludeMimes) {
        mExcludeMimes = excludeMimes;
    }

}
