/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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
import android.util.AttributeSet;
import android.widget.TextView;

import org.kontalk.data.Contact;


public class ContactInfoBanner extends AvatarListItem {

    private Contact mContact;
    private TextView mText1;
    private TextView mText2;

    public ContactInfoBanner(Context context) {
        super(context);
    }

    public ContactInfoBanner(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mText1 = findViewById(android.R.id.text1);
        mText2 = findViewById(android.R.id.text2);
    }

    public final void bind(Context context, final Contact contact) {
        mContact = contact;

        loadAvatar(contact);

        mText1.setText(contact.getDisplayName());
    }

    public final void unbind() {
        mContact = null;
    }

    public void setSummary(CharSequence summary) {
        mText2.setText(summary);
    }

    public Contact getContact() {
        return mContact;
    }

    @Override
    protected boolean isGroupChat() {
        return false;
    }

}
