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

import android.view.View;
import android.view.ViewStub;

import androidx.annotation.ColorInt;

import org.kontalk.data.Contact;
import org.kontalk.message.MessageComponent;


/**
 * Interface implemented by message list item themes.
 * @author Daniele Ricci
 */
public interface MessageListItemTheme {

    View inflate(ViewStub stub);

    MessageContentLayout getContent();

    void setEncryptedContent(long databaseId);

    void processComponents(long databaseId, Pattern highlight,
        List<MessageComponent<?>> components, Object... args);

    void setSecurityFlags(int securityFlags);

    void setIncoming(Contact contact, boolean sameMessageBlock);

    void setOutgoing(Contact contact, int status, boolean sameMessageBlock);

    void setTimestamp(CharSequence timestamp);

    TextContentView getTextContentView();

    void unload();

    // theme information

    boolean isFullWidth();

    @ColorInt
    int getTextColor();

}
