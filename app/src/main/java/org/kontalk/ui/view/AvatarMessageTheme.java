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

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.ViewStub;
import android.widget.LinearLayout;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.util.SystemUtils;


/**
 * Avatar-based message balloon theme.
 * @author Daniele Ricci
 */
public class AvatarMessageTheme extends BaseMessageTheme implements Contact.ContactCallback {

    private static Drawable sDefaultContactImage;

    private final int mDrawableId;
    /** If true, handles collapsed message blocks (hides adjacent avatars). */
    private final boolean mMessageBlocks;

    protected LinearLayout mBalloonView;

    protected CircleContactBadge mAvatar;

    private Handler mHandler;

    public AvatarMessageTheme(int layoutId, int drawableId, boolean messageBlocks) {
        super(layoutId);
        mDrawableId = drawableId;
        mMessageBlocks = messageBlocks;
    }

    @Override
    public View inflate(ViewStub stub) {
        View view = super.inflate(stub);

        mBalloonView = view.findViewById(R.id.balloon_view);

        mAvatar = view.findViewById(R.id.avatar);

        mHandler = new Handler();

        if (sDefaultContactImage == null) {
            sDefaultContactImage = ContextCompat
                .getDrawable(mContext, R.drawable.ic_default_contact);
        }

        return view;
    }

    @Override
    public boolean isFullWidth() {
        return false;
    }

    @Override
    public void processComponentView(MessageContentView<?> view) {
        if (view instanceof TextContentView) {
            ((TextContentView) view).enableMeasureHack(true);
        }
    }

    protected void setView(boolean sameMessageBlock) {
        if (mBalloonView != null) {
            mBalloonView.setBackgroundResource(mDrawableId);
        }
    }

    @Override
    public void setIncoming(Contact contact, boolean sameMessageBlock) {
        setView(sameMessageBlock);

        if (mAvatar != null) {
            if (mMessageBlocks && sameMessageBlock) {
                mAvatar.setVisibility(View.INVISIBLE);
                mAvatar.setImageDrawable(null);
            }
            else {
                mAvatar.setImageDrawable(sDefaultContactImage);
                if (contact != null) {
                    // we mark this with the contact's hash code for the async avatar
                    mAvatar.setTag(contact.hashCode());
                    mAvatar.assignContactUri(contact.getUri());
                    contact.getAvatarAsync(mContext, this);
                }
                else {
                    mAvatar.setTag(null);
                    mAvatar.assignContactUri(null);
                }
                mAvatar.setVisibility(View.VISIBLE);
            }
        }

        super.setIncoming(contact, sameMessageBlock);
    }

    @Override
    public void setOutgoing(Contact contact, int status, boolean sameMessageBlock) {
        setView(sameMessageBlock);

        if (mAvatar != null) {
            if (mMessageBlocks && sameMessageBlock) {
                mAvatar.setVisibility(View.INVISIBLE);
                mAvatar.setImageDrawable(null);
            }
            else {
                Drawable avatar;
                Bitmap profile = SystemUtils.getProfilePhoto(mContext);
                if (profile != null) {
                    avatar = new BitmapDrawable(mContext.getResources(), profile);
                }
                else {
                    avatar = sDefaultContactImage;
                }

                mAvatar.setImageDrawable(avatar);
                mAvatar.assignContactUri(SystemUtils.getProfileUri(mContext));
                mAvatar.setVisibility(View.VISIBLE);
            }
        }

        super.setOutgoing(contact, status, sameMessageBlock);
    }

    @Override
    public void avatarLoaded(final Contact contact, final Drawable avatar) {
        if (avatar != null) {
            if (mHandler.getLooper().getThread() != Thread.currentThread()) {
                mHandler.post(new Runnable() {
                    public void run() {
                        updateAvatar(contact, avatar);
                    }
                });
            }
            else {
                updateAvatar(contact, avatar);
            }
        }
    }

    void updateAvatar(Contact contact, Drawable avatar) {
        try {
            // be sure the contact is still the same
            // this is an insane workaround against race conditions
            Integer contactTag = (Integer) mAvatar.getTag();
            if (contactTag != null && contactTag == contact.hashCode())
                mAvatar.setImageDrawable(avatar);
        }
        catch (Exception e) {
            // we are deliberately ignoring any exception here
            // because an error here could happen only if something
            // weird is happening, e.g. user leaving the activity
        }
    }

}
