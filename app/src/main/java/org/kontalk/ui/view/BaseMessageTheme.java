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

import java.util.List;
import java.util.regex.Pattern;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.crypto.Coder;
import org.kontalk.data.Contact;
import org.kontalk.message.MessageComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.provider.MyMessages;
import org.kontalk.util.Preferences;


/**
 * Base message balloon theme with no avatars.
 * @author Daniele Ricci
 */
public abstract class BaseMessageTheme implements MessageListItemTheme {

    private final int mLayoutId;
    private Context mContext;
    private LayoutInflater mInflater;

    private MessageContentLayout mContent;
    private ImageView mStatusIcon;
    private ImageView mWarningIcon;
    private TextView mDateView;
    private LinearLayout mBalloonView;
    private LinearLayout mParentView;

    public BaseMessageTheme(int layoutId) {
        mLayoutId = layoutId;
    }

    @Override
    public void inflate(ViewStub stub) {
        stub.setLayoutResource(mLayoutId);
        View view = stub.inflate();
        // save the inflater for later
        mContext = stub.getContext();
        mInflater = LayoutInflater.from(mContext);

        mContent = (MessageContentLayout) view.findViewById(R.id.content);
        mStatusIcon = (ImageView) view.findViewById(R.id.status_indicator);
        mWarningIcon = (ImageView) view.findViewById(R.id.warning_icon);
        mBalloonView = (LinearLayout) view.findViewById(R.id.balloon_view);
        mDateView = (TextView) view.findViewById(R.id.date_view);
        mParentView = (LinearLayout) view.findViewById(R.id.message_view_parent);
    }

    @Override
    public MessageContentLayout getContent() {
        return mContent;
    }

    @Override
    public void setEncryptedContent(long databaseId, Contact contact) {
        // FIXME this is not good
        TextContentView view = TextContentView.obtain(mInflater, mContent, true);

        String text = mContext.getResources().getString(R.string.text_encrypted);
        view.bind(databaseId, new TextComponent(text), contact, null);
        mContent.addContent(view);
    }

    @Override
    public void processComponents(long databaseId, Contact contact, Pattern highlight,
        List<MessageComponent<?>> components, Object... args) {
        for (MessageComponent<?> cmp : components) {
            MessageContentView<?> view = MessageContentViewFactory
                .createContent(mInflater, mContent, cmp, databaseId,
                    contact, highlight, args);

            mContent.addContent(view);
        }
    }

    @Override
    public void setSecurityFlags(int securityFlags) {
        if (Coder.isError(securityFlags)) {
            mWarningIcon.setImageResource(R.drawable.ic_msg_security);
            mWarningIcon.setVisibility(View.VISIBLE);
        }
        else {
            mWarningIcon.setImageResource(R.drawable.ic_msg_warning);
            mWarningIcon.setVisibility((securityFlags != Coder.SECURITY_CLEARTEXT) ?
                View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void setIncoming(Contact contact) {
        // TODO this is not base
        if (mBalloonView != null) {
            mBalloonView.setBackgroundResource(Preferences
                .getBalloonResource(mContext, MyMessages.Messages.DIRECTION_IN));
        }
        // TODO this is not base
        mParentView.setGravity(Gravity.LEFT);

        /*
        if (mAvatarIncoming != null) {
            mAvatarOutgoing.setVisibility(GONE);
            mAvatarIncoming.setVisibility(VISIBLE);
            mAvatarIncoming.setImageDrawable(contact != null ?
                contact.getAvatar(context, sDefaultContactImage) : sDefaultContactImage);
        }
        */

        // no status icon for incoming messages
        mStatusIcon.setImageDrawable(null);
        mStatusIcon.setVisibility(View.GONE);
    }

    @Override
    public void setOutgoing(Contact contact, int status) {
        // TODO this is not base
        if (mBalloonView != null) {
            mBalloonView.setBackgroundResource(Preferences
                .getBalloonResource(mContext, MyMessages.Messages.DIRECTION_OUT));
        }
        // TODO this is not base
        mParentView.setGravity(Gravity.RIGHT);

        /*
        if (mAvatarOutgoing != null) {
            mAvatarIncoming.setVisibility(GONE);
            mAvatarOutgoing.setVisibility(VISIBLE);
            // TODO show own profile picture
            mAvatarOutgoing.setImageDrawable(sDefaultContactImage);
        }
        */

        int resId = 0;
        int statusId = 0;

        // status icon
        switch (status) {
            case MyMessages.Messages.STATUS_SENDING:
                // use pending icon even for errors
            case MyMessages.Messages.STATUS_ERROR:
            case MyMessages.Messages.STATUS_PENDING:
                resId = R.drawable.ic_msg_pending;
                statusId = R.string.msg_status_sending;
                break;
            case MyMessages.Messages.STATUS_RECEIVED:
                resId = R.drawable.ic_msg_delivered;
                statusId = R.string.msg_status_delivered;
                break;
            // here we use the error icon
            case MyMessages.Messages.STATUS_NOTACCEPTED:
                resId = R.drawable.ic_msg_error;
                statusId = R.string.msg_status_notaccepted;
                break;
            case MyMessages.Messages.STATUS_SENT:
                resId = R.drawable.ic_msg_sent;
                statusId = R.string.msg_status_sent;
                break;
            case MyMessages.Messages.STATUS_NOTDELIVERED:
                resId = R.drawable.ic_msg_notdelivered;
                statusId = R.string.msg_status_notdelivered;
                break;
        }

        if (resId > 0) {
            mStatusIcon.setImageResource(resId);
            mStatusIcon.setVisibility(View.VISIBLE);
            mStatusIcon.setContentDescription(mContext.getResources().getString(statusId));
        }
        else {
            mStatusIcon.setImageDrawable(null);
            mStatusIcon.setVisibility(View.GONE);
        }
    }

    @Override
    public void setTimestamp(CharSequence timestamp) {
        mDateView.setText(timestamp);
    }

}
