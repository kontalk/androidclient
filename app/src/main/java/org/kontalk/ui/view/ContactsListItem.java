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

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.provider.Keyring;
import org.kontalk.util.SystemUtils;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.TextView;


public class ContactsListItem extends AvatarListItem implements Checkable {

    private static final int[] CHECKED_STATE_SET = { android.R.attr.state_checked };

    private Contact mContact;
    private TextView mText1;
    private TextView mText2;
    private ImageView mTrustStatus;
    private CircleCheckBox mCheckbox;

    private boolean mChecked;

    public ContactsListItem(Context context) {
        super(context);
    }

    public ContactsListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mText1 = findViewById(android.R.id.text1);
        mText2 = findViewById(android.R.id.text2);
        mTrustStatus = findViewById(R.id.trust_status);
        mCheckbox = findViewById(R.id.checkbox);

        if (isInEditMode()) {
            mText1.setText("Test contact");
            mText2.setText("+393375423981");
        }
    }

    public final void bind(Context context, final Contact contact) {
        bind(context, contact, null, null);
    }

    public final void bind(Context context, final Contact contact, String prependStatus, CharacterStyle prependStyle) {
        bind(context, contact, prependStatus, prependStyle, true);
    }

    public final void bind(Context context, final Contact contact, String prependStatus, CharacterStyle prependStyle, Boolean subscribed) {
        mContact = contact;

        setChecked(false);

        loadAvatar(contact);

        mText1.setText(contact.getDisplayName());

        CharSequence text2 = contact.getStatus();
        if (text2 == null) {
            text2 = contact.getNumber();
            if (text2 == null)
                text2 = contact.getJID();
            mText2.setTextColor(ContextCompat.getColor(context, R.color.grayed_out));
        }
        else {
            int color = ContextCompat.getColor(context,
                SystemUtils.getThemedResource(getContext(), android.R.attr.textColorSecondary));
            mText2.setTextColor(color);
        }
        if (prependStatus != null) {
            if (prependStyle != null) {
                text2 = new SpannableString(prependStatus + " " + text2);
                ((SpannableString) text2).setSpan(prependStyle, 0, prependStatus.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            else {
                text2 = prependStatus + " " + text2;
            }
        }
        mText2.setText(text2);

        if (mTrustStatus != null) {
            int resId;

            if (subscribed == null) {
                resId = 0;
            }
            else if (!subscribed) {
                resId = R.drawable.ic_denied;
            }
            else if (contact.isKeyChanged()) {
                // the key has changed and was not trusted yet
                resId = R.drawable.ic_trust_unknown;
            }
            else {
                switch (contact.getTrustedLevel()) {
                    case Keyring.TRUST_UNKNOWN:
                        resId = R.drawable.ic_trust_unknown;
                        break;
                    case Keyring.TRUST_IGNORED:
                        resId = R.drawable.ic_trust_ignored;
                        break;
                    case Keyring.TRUST_VERIFIED:
                        resId = R.drawable.ic_trust_verified;
                        break;
                    default:
                        resId = -1;
                }
            }

            if (resId > 0) {
                mTrustStatus.setImageResource(resId);
            }
            else {
                mTrustStatus.setImageDrawable(null);
            }
        }

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

    @Override
    protected boolean isGroupChat() {
        return false;
    }

    @Override
    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        if (checked != mChecked) {
            mChecked = checked;
            if (mCheckbox != null) {
                mCheckbox.setChecked(checked);
                mAvatarView.setVisibility(checked ? GONE : VISIBLE);
            }
            refreshDrawableState();
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked()) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        return drawableState;
    }

    @Override
    public void toggle() {
        setChecked(!mChecked);
    }

}
