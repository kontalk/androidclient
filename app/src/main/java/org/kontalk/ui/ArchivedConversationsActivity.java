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

package org.kontalk.ui;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.os.Bundle;

import org.kontalk.R;
import org.kontalk.data.Conversation;
import org.kontalk.provider.MyMessages;
import org.kontalk.service.msgcenter.MessageCenterService;


/**
 * The archived conversations list activity.
 * This is a always dedicated activity (also on tablets) which will reply to the
 * calling activity with the selected conversation.
 * @author Daniele Ricci
 */
public class ArchivedConversationsActivity extends ToolbarActivity
        implements ConversationsCallback {
    public static final String TAG = ArchivedConversationsActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.archived_conversations_screen);

        setupToolbar(true, true);
    }

    @Override
    protected boolean isNormalUpNavigation() {
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();

        // hold message center
        MessageCenterService.hold(this, true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // release message center
        MessageCenterService.release(this);
    }

    @Override
    public void openConversation(Conversation conv) {
        // return result to caller
        setResult(RESULT_OK, new Intent(null, ContentUris
            .withAppendedId(MyMessages.Threads.Conversations.CONTENT_URI, conv.getThreadId())));
        finish();
    }

    @Override
    public void onDatabaseChanged() {
        // nothing to do
    }

    public static void start(Activity activity, int requestCode) {
        activity.startActivityForResult(new Intent(activity, ArchivedConversationsActivity.class),
            requestCode);
    }

}
