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

import java.util.regex.Pattern;

import android.content.Context;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.core.text.util.LinkifyCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.InReplyToComponent;
import org.kontalk.message.ReferencedMessage;
import org.kontalk.provider.MyMessages;
import org.kontalk.util.SystemUtils;
import org.kontalk.util.ViewUtils;
import org.kontalk.util.XMPPUtils;


/**
 * Component view for {@link InReplyToComponent}.
 * @author Daniele Ricci
 */
public class QuoteContentView extends RelativeLayout
        implements MessageContentView<InReplyToComponent> {

    private InReplyToComponent mComponent;

    private TextView mSender;
    private TextView mContent;

    public QuoteContentView(Context context) {
        super(context);
    }

    public QuoteContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSender = findViewById(R.id.sender);
        mContent = findViewById(R.id.content);
    }

    @Override
    public void bind(long databaseId, InReplyToComponent component, Pattern highlight) {
        mComponent = component;

        CharSequence msgSender;
        CharSequence msgText;

        ReferencedMessage referencedMsg = mComponent.getContent();
        if (referencedMsg != null) {
            SpannableStringBuilder formattedMessage =
                new SpannableStringBuilder(referencedMsg.getTextContent());

            // linkify!
            if (formattedMessage.length() < TextContentView.MAX_AFFORDABLE_SIZE) {
                try {
                    LinkifyCompat.addLinks(formattedMessage, Linkify.ALL);
                }
                catch (Throwable e) {
                    // working around some crappy firmwares
                }
            }

            TextContentView.applyTextWorkarounds(formattedMessage);
            msgText = formattedMessage;

            Contact sender = getReferencedContact(referencedMsg);
            msgSender = sender.getDisplayName();
        }
        else {
            Spannable formattedMessage = new SpannableString(getContext()
                .getString(R.string.reply_message_deleted));
            formattedMessage.setSpan(SystemUtils.getTypefaceSpan(Typeface.ITALIC),
                0, formattedMessage.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            msgText = formattedMessage;

            msgSender = getContext().getString(R.string.peer_unknown);
        }

        mSender.setText(msgSender);
        mContent.setText(msgText);
    }

    @Override
    public void unbind() {
        clear();
    }

    @Override
    public InReplyToComponent getComponent() {
        return mComponent;
    }

    /** Quote is always first. */
    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public void onApplyTheme(MessageListItemTheme theme) {
        ViewUtils.setMessageBodyTextStyle(mSender, false);
        ReferencedMessage referencedMessage = mComponent.getContent();
        if (referencedMessage != null) {
            mSender.setTextColor(XMPPUtils
                .getJIDColor(getReferencedContact(referencedMessage).getJID()));
        }

        ViewUtils.setMessageBodyTextStyle(mContent, false);
    }

    private void clear() {
        mComponent = null;
    }

    private Contact getReferencedContact(@NonNull ReferencedMessage referencedMsg) {
        String senderId;
        if (referencedMsg.getDirection() == MyMessages.Messages.DIRECTION_OUT) {
            senderId = Kontalk.get().getDefaultAccount().getSelfJID();
        }
        else {
            senderId = referencedMsg.getPeer();
        }

        return Contact.findByUserId(getContext(), senderId);
    }

    public static QuoteContentView create(LayoutInflater inflater, ViewGroup parent) {
        return (QuoteContentView) inflater.inflate(R.layout.message_content_quote, parent, false);
    }

}
