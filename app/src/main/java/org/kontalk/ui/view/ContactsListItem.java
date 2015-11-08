/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

import org.kontalk.R;
import org.kontalk.data.Contact;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.TextView;


public class ContactsListItem extends AvatarListItem {

    private Contact mContact;
    private TextView mHeader;
    private TextView mText1;
    private TextView mText2;

    public ContactsListItem(Context context) {
        super(context);
    }

    public ContactsListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mHeader = (TextView) findViewById(R.id.header_text);
        mText1 = (TextView) findViewById(android.R.id.text1);
        mText2 = (TextView) findViewById(android.R.id.text2);

        if (isInEditMode()) {
            mText1.setText("Test contact");
            mText2.setText("+393375423981");
        }
    }

    public final void bind(Context context, final Contact contact) {
        mContact = contact;

        loadAvatar(contact);

        if (!TextUtils.isEmpty(contact.getName())) {
            mText1.setText(contact.getName());
        } else {
            mText1.setText(contact.getNumber());
        }

        String text2 = contact.getStatus();
        if (text2 == null) {
            text2 = contact.getNumber();
            mText2.setTextColor(getResources().getColor(R.color.grayed_out));
        }
        else {
            mText2.setTextColor(getResources().getColor(android.R.color.secondary_text_light));
        }
        mText2.setText(text2);
    }

    public final void unbind() {
        mContact = null;
        /*
        mAvatarView.setImageDrawable(null);
        BitmapDrawable d = (BitmapDrawable) mAvatarView.getDrawable();
        if (d != null) {
            Bitmap b = d.getBitmap();
            if (b != null) b.recycle();
        }
        */
    }

    public Contact getContact() {
        return mContact;
    }

}
