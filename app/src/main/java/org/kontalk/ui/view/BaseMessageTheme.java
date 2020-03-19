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

import java.util.List;
import java.util.regex.Pattern;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.crypto.Coder;
import org.kontalk.data.Contact;
import org.kontalk.message.MessageComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.provider.MyMessages;
import org.kontalk.util.XMPPUtils;


/**
 * Base message balloon theme with no avatars.
 * @author Daniele Ricci
 */
public abstract class BaseMessageTheme implements MessageListItemTheme {

    private final int mLayoutId;
    protected Context mContext;
    protected LayoutInflater mInflater;

    protected TextView mContactNameView;
    private MessageContentLayout mContent;
    private ImageView mStatusIcon;
    private ImageView mWarningIcon;
    private TextView mDateView;

    /** If true, we will show the contact name above the message content. */
    protected final boolean mGroupChat;

    protected BaseMessageTheme(int layoutId, boolean groupChat) {
        mLayoutId = layoutId;
        mGroupChat = groupChat;
    }

    @Override
    public View inflate(ViewStub stub) {
        stub.setLayoutResource(mLayoutId);
        View view = stub.inflate();
        // save the inflater for later
        mContext = stub.getContext();
        mInflater = LayoutInflater.from(mContext);

        mContactNameView = view.findViewById(R.id.contact_name);
        mContent = view.findViewById(R.id.content);
        mStatusIcon = view.findViewById(R.id.status_indicator);
        mWarningIcon = view.findViewById(R.id.warning_icon);
        mDateView = view.findViewById(R.id.date_view);

        return view;
    }

    @Override
    public MessageContentLayout getContent() {
        return mContent;
    }

    @Override
    public void setEncryptedContent(long databaseId) {
        // FIXME this is not good
        TextContentView view = TextContentView.obtain(mInflater, mContent, true);

        String text = mContext.getResources().getString(R.string.text_encrypted);
        view.bind(databaseId, new TextComponent(text), null);
        mContent.addContent(view);
    }

    @Override
    public void processComponents(long databaseId, Pattern highlight,
        List<MessageComponent<?>> components, Object... args) {
        for (MessageComponent<?> cmp : components) {
            MessageContentView<?> view = MessageContentViewFactory
                .createContent(mInflater, mContent, cmp, databaseId,
                    highlight, args);

            if (view != null) {
                processComponentView(view);
                mContent.addContent(view);
            }
        }
    }

    /** Override to modify a component view before adding it. */
    public void processComponentView(MessageContentView<?> view) {
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
    public void setIncoming(Contact contact, boolean sameMessageBlock) {
        // no status icon for incoming messages
        mStatusIcon.setImageDrawable(null);
        mStatusIcon.setVisibility(View.GONE);

        if (mGroupChat) {
            if (contact != null) {
                mContactNameView.setText(contact.getDisplayName());
                mContactNameView.setTextColor(XMPPUtils.getJIDColor(contact.getJID()));
                mContactNameView.setVisibility(View.VISIBLE);
            }
            else {
                // FIXME awkard situation
                mContactNameView.setVisibility(View.GONE);
            }
        }
        else {
            mContactNameView.setVisibility(View.GONE);
        }
    }

    @Override
    public void setOutgoing(Contact contact, int status, boolean sameMessageBlock) {
        int resId = 0;
        int statusId = 0;

        // status icon
        switch (status) {
            case MyMessages.Messages.STATUS_SENDING:
                // use pending icon even for errors
            case MyMessages.Messages.STATUS_ERROR:
            case MyMessages.Messages.STATUS_PENDING:
            case MyMessages.Messages.STATUS_QUEUED:
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

    protected boolean isIncoming() {
        return mStatusIcon.getVisibility() == View.GONE;
    }

    @Override
    public void setTimestamp(CharSequence timestamp) {
        mDateView.setText(timestamp);
    }

    @Override
    public TextContentView getTextContentView() {
        int c = mContent.getChildCount();
        for (int i = 0; i < c; i++) {
            MessageContentView<?> view = (MessageContentView<?>) mContent.getChildAt(i);
            if (view instanceof TextContentView) {
                return (TextContentView) view;
            }
        }
        return null;
    }

    @Override
    public void unload() {
        int c = mContent.getChildCount();
        for (int i = 0; i < c; i++) {
            MessageContentView<?> view = (MessageContentView<?>) mContent.getChildAt(0);
            mContent.removeView((View) view);
            view.unbind();
        }
    }
}
