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

import java.util.regex.Pattern;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.InReplyToComponent;
import org.kontalk.message.ReferencedMessage;


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

        SpannableStringBuilder formattedMessage = formatMessage();

        // TODO TextContentView.setTextStyle(mSender);
        // TODO TextContentView.setTextStyle(mContent);

        // linkify!
        if (formattedMessage.length() < TextContentView.MAX_AFFORDABLE_SIZE)
            Linkify.addLinks(formattedMessage, Linkify.ALL);

        TextContentView.applyTextWorkarounds(formattedMessage);

        Contact sender = Contact.findByUserId(getContext(), mComponent.getContent().getSender());
        mSender.setText(sender.getDisplayName());
        mContent.setText(formattedMessage);
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

    private SpannableStringBuilder formatMessage() {
        ReferencedMessage referencedMsg = mComponent.getContent();
        String textContent = referencedMsg.getTextContent();

        return new SpannableStringBuilder(textContent);
    }

    private void clear() {
        mComponent = null;
    }

    public static QuoteContentView create(LayoutInflater inflater, ViewGroup parent) {
        return (QuoteContentView) inflater.inflate(R.layout.message_content_quote, parent, false);
    }

}
