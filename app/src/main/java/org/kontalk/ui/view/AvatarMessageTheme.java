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

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
public class AvatarMessageTheme extends BaseMessageTheme {

    private static Drawable sDefaultContactImage;

    private final int mDrawableId;

    private LinearLayout mBalloonView;

    private CircleContactBadge mAvatar;

    public AvatarMessageTheme(int layoutId, int drawableId) {
        super(layoutId);
        mDrawableId = drawableId;
    }

    @Override
    public View inflate(ViewStub stub) {
        View view = super.inflate(stub);

        mBalloonView = (LinearLayout) view.findViewById(R.id.balloon_view);

        mAvatar = (CircleContactBadge) view.findViewById(R.id.avatar);

        if (sDefaultContactImage == null) {
            sDefaultContactImage = mContext.getResources()
                .getDrawable(R.drawable.ic_contact_picture);
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

    private void setView() {
        if (mBalloonView != null) {
            mBalloonView.setBackgroundResource(mDrawableId);
        }
    }

    @Override
    public void setIncoming(Contact contact) {
        setView();

        if (mAvatar != null) {
            mAvatar.assignContactUri(contact != null ? contact.getUri() : null);
            mAvatar.setImageDrawable(contact != null ?
                contact.getAvatar(mContext) : sDefaultContactImage);
        }

        super.setIncoming(contact);
    }

    @Override
    public void setOutgoing(Contact contact, int status) {
        setView();

        if (mAvatar != null) {
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
        }

        super.setOutgoing(contact, status);
    }

}
