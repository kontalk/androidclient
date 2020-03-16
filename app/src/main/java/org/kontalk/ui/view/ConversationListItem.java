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

import android.content.Context;
import android.graphics.Typeface;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import org.kontalk.BuildConfig;
import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.GroupCommandComponent;
import org.kontalk.provider.MessagesProviderClient.GroupThreadContent;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.util.MessageUtils;


public class ConversationListItem extends AvatarListItem {

    private static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);
    private static final StyleSpan STYLE_ITALIC = new StyleSpan(Typeface.ITALIC);

    private Conversation mConversation;
    private TextView mSubjectView;
    private TextView mFromView;
    private TextView mDateView;
    private ImageView mSticky;
    private ImageView mErrorIndicator;
    private TextView mCounterView;

    public ConversationListItem(Context context) {
        super(context);
    }

    public ConversationListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mFromView = findViewById(R.id.from);
        mSubjectView = findViewById(R.id.subject);

        mDateView = findViewById(R.id.date);
        mSticky = findViewById(R.id.sticky);
        mErrorIndicator = findViewById(R.id.error);
        mCounterView = findViewById(R.id.counter);

        if (isInEditMode()) {
            mFromView.setText("Test zio");
            mSubjectView.setText("Bella zio senti per domani facciamo che mi vieni a prendere ok?");
            mDateView.setText("20:14");
            /*
            mErrorIndicator.setVisibility(VISIBLE);
            mErrorIndicator.setImageResource(R.drawable.ic_msg_pending);
            */
            mCounterView.setVisibility(VISIBLE);
            mCounterView.setText("5");
        }
    }

    public final void bind(Context context, final Conversation conv, boolean selected) {
        mConversation = conv;

        setActivated(selected);

        Contact contact;
        // used for the conversation subject: either group subject or contact name
        String recipient = null;

        if (mConversation.isGroupChat()) {
            recipient = mConversation.getGroupSubject();
            if (TextUtils.isEmpty(recipient))
                recipient = context.getString(R.string.group_untitled);

            // enable group chat marker
            TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(mFromView,
                R.drawable.ic_indicator_group, 0, 0, 0);

            loadAvatar(null);
        }
        else {
            contact = mConversation.getContact();

            if (contact != null) {
                recipient = contact.getDisplayName();
            }

            if (recipient == null) {
                if (BuildConfig.DEBUG) {
                    recipient = conv.getRecipient();
                }
                else {
                    recipient = context.getString(R.string.peer_unknown);
                }
            }

            // disable group chat marker
            TextViewCompat.setCompoundDrawablesRelative(mFromView,
                null, null, null, null);

            loadAvatar(contact);
        }


        SpannableStringBuilder from = new SpannableStringBuilder(recipient);
        if (conv.getUnreadCount() > 0)
            from.setSpan(STYLE_BOLD, 0, from.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

        // draft indicator
        int lastpos = from.length();
        String draft = conv.getDraft();
        if (draft != null) {
            from.append(" ");
            from.append(context.getResources().getString(R.string.has_draft));
            from.setSpan(new ForegroundColorSpan(
                ContextCompat.getColor(context, R.color.text_color_draft)),
                lastpos, from.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }

        mFromView.setText(from);
        mDateView.setText(MessageUtils.formatTimeStampString(context, conv.getDate()));
        mSticky.setVisibility(conv.isSticky() ? VISIBLE : GONE);

        // error indicator
        int resId = -1;
        int statusId = -1;
        switch (conv.getStatus()) {
            case Messages.STATUS_SENDING:
                // use pending icon even for errors
            case Messages.STATUS_ERROR:
            case Messages.STATUS_PENDING:
            case Messages.STATUS_QUEUED:
                resId = R.drawable.ic_msg_pending;
                statusId = R.string.msg_status_sending;
                break;
            case Messages.STATUS_SENT:
                resId = R.drawable.ic_msg_sent;
                statusId = R.string.msg_status_sent;
                break;
            case Messages.STATUS_RECEIVED:
                resId = R.drawable.ic_msg_delivered;
                statusId = R.string.msg_status_delivered;
                break;
            // here we use the error icon
            case Messages.STATUS_NOTACCEPTED:
                resId = R.drawable.ic_thread_error;
                statusId = R.string.msg_status_notaccepted;
                break;
            case Messages.STATUS_NOTDELIVERED:
                resId = R.drawable.ic_msg_notdelivered;
                statusId = R.string.msg_status_notdelivered;
                break;
        }

        // no matching resource or draft - hide status icon
        boolean incoming = resId < 0;
        if (incoming || draft != null) {
            mErrorIndicator.setVisibility(GONE);

            int unread = mConversation.getUnreadCount();
            if (unread > 0) {
                mCounterView.setText(String.valueOf(unread));
                mCounterView.setVisibility(VISIBLE);
            }
            else {
                mCounterView.setVisibility(GONE);
            }
        }
        else {
            mCounterView.setVisibility(GONE);
            mErrorIndicator.setVisibility(VISIBLE);
            mErrorIndicator.setImageResource(resId);
            mErrorIndicator.setContentDescription(getResources().getString(statusId));
        }

        CharSequence text;

        // last message or draft??
        if (conv.getRequestStatus() == Threads.REQUEST_WAITING) {
            text = new SpannableString(context.getString(R.string.text_invitation_info));
            ((Spannable) text).setSpan(STYLE_ITALIC, 0, text.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        else {
            String subject = conv.getSubject();
            String source = (draft != null) ? draft : subject;

            if (source != null) {
                if (draft == null) {
                    if (GroupCommandComponent.supportsMimeType(conv.getMime())) {
                        if (incoming) {
                            // content is in a special format
                            GroupThreadContent parsed = GroupThreadContent.parseIncoming(subject);
                            subject = parsed.command;
                        }
                        text = new SpannableString(GroupCommandComponent.getTextContent(getContext(), subject, incoming));
                        ((Spannable) text).setSpan(STYLE_ITALIC, 0, text.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                    }
                    else {
                        if (incoming && conv.isGroupChat()) {
                            // content is in a special format
                            GroupThreadContent parsed = GroupThreadContent.parseIncoming(subject);
                            contact = parsed.sender != null ? Contact.findByUserId(context, parsed.sender) : null;
                            source = parsed.command;

                            String displayName = null;
                            if (contact != null)
                                displayName = contact.getDisplayName();

                            if (displayName == null) {
                                if (BuildConfig.DEBUG) {
                                    displayName = conv.getRecipient();
                                }
                                else {
                                    displayName = context.getString(R.string.peer_unknown);
                                }
                            }

                            if (source == null) {
                                // determine from mime type
                                source = CompositeMessage.getSampleTextContent(conv.getMime());
                            }

                            text = new SpannableString(displayName + ": " + source);
                            ((Spannable) text).setSpan(STYLE_ITALIC, 0, displayName.length() + 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                        }
                        else {
                            text = source;
                        }
                    }
                }
                else {
                    text = draft;
                }
            }

            else if (conv.isEncrypted()) {
                text = context.getString(R.string.text_encrypted);
            }

            else {
                // determine from mime type
                text = CompositeMessage.getSampleTextContent(conv.getMime());
            }
        }

        if (conv.getUnreadCount() > 0) {
            text = new SpannableString(text);
            ((Spannable) text).setSpan(STYLE_BOLD, 0, text.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            mSubjectView.setSingleLine(false);
            mSubjectView.setMaxLines(3);
            mSubjectView.setEllipsize(TextUtils.TruncateAt.END);
        }
        else {
            mSubjectView.setSingleLine(true);
            mSubjectView.setMaxLines(1);
            mSubjectView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        }

        mSubjectView.setText(text);
    }

    public final void unbind() {
        // TODO unbind (contact?)
    }

    public Conversation getConversation() {
        return mConversation;
    }

    @Override
    protected boolean isGroupChat() {
        return mConversation != null && mConversation.isGroupChat();
    }

}
