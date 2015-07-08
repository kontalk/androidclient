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

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.kontalk.R;
import org.kontalk.data.Contact;


/**
 * Avatar-based message balloon theme.
 * @author Daniele Ricci
 */
public class AvatarMessageTheme extends BaseMessageTheme {

    private static Drawable sDefaultContactImage;

    private final int mIncomingDrawableId;
    private final int mOutgoingDrawableId;

    private LinearLayout mBalloonView;

    private ImageView mAvatarIncoming;
    private ImageView mAvatarOutgoing;

    public AvatarMessageTheme(int incomingDrawableId, int outgoingDrawableId) {
        super();
        mIncomingDrawableId = incomingDrawableId;
        mOutgoingDrawableId = outgoingDrawableId;
    }

    @Override
    public View inflate(ViewStub stub, int direction) {
        View view = super.inflate(stub);

        mBalloonView = (LinearLayout) view.findViewById(R.id.balloon_view);

        mAvatarIncoming = (ImageView) view.findViewById(R.id.avatar_incoming);
        mAvatarOutgoing = (ImageView) view.findViewById(R.id.avatar_outgoing);

        if (sDefaultContactImage == null) {
            sDefaultContactImage = mContext.getResources()
                .getDrawable(R.drawable.ic_contact_picture);
        }

        return view;
    }

    @Override
    public void processComponentView(MessageContentView<?> view) {
    }

    @Override
    public void setIncoming(Contact contact) {
        if (mBalloonView != null) {
            mBalloonView.setBackgroundResource(mIncomingDrawableId);
        }

        if (mAvatarIncoming != null) {
            mAvatarOutgoing.setVisibility(View.GONE);
            mAvatarIncoming.setVisibility(View.VISIBLE);
            mAvatarIncoming.setImageDrawable(contact != null ?
                contact.getAvatar(mContext, sDefaultContactImage) : sDefaultContactImage);
        }

        super.setIncoming(contact);
    }

    @Override
    public void setOutgoing(Contact contact, int status) {
        if (mBalloonView != null) {
            mBalloonView.setBackgroundResource(mOutgoingDrawableId);
        }

        if (mAvatarOutgoing != null) {
            mAvatarIncoming.setVisibility(View.GONE);
            mAvatarOutgoing.setVisibility(View.VISIBLE);
            // TODO show own profile picture
            mAvatarOutgoing.setImageDrawable(sDefaultContactImage);
        }

        super.setOutgoing(contact, status);
    }

}
