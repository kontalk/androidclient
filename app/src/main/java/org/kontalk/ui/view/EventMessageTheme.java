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

import androidx.annotation.ColorInt;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.MessageComponent;
import org.kontalk.message.TextComponent;


/**
 * Event message theme.
 * @author Daniele Ricci
 */
public class EventMessageTheme implements MessageListItemTheme {

    private final int mLayoutId;
    protected Context mContext;
    protected LayoutInflater mInflater;

    private MessageContentLayout mContent;

    protected EventMessageTheme(int layoutId) {
        mLayoutId = layoutId;
    }

    @Override
    public View inflate(ViewStub stub) {
        stub.setLayoutResource(mLayoutId);
        View view = stub.inflate();
        // save the inflater for later
        mContext = stub.getContext();
        mInflater = LayoutInflater.from(mContext);

        mContent = view.findViewById(R.id.content);
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
                view.onApplyTheme(this);
                processComponentView(view);
                mContent.addContent(view);
            }
        }
    }

    public void processComponentView(MessageContentView<?> view) {
        if (view instanceof TextContentView) {
            ((TextContentView) view).enableMeasureHack(true);
        }
    }

    @Override
    public void setSecurityFlags(int securityFlags) {
    }

    @Override
    public void setIncoming(Contact contact, boolean sameMessageBlock) {
    }

    @Override
    public void setOutgoing(Contact contact, int status, boolean sameMessageBlock) {
    }

    @Override
    public void setTimestamp(CharSequence timestamp) {
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
    public boolean isFullWidth() {
        return false;
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

    @ColorInt
    @Override
    public int getTextColor() {
        return -1;
    }
}
