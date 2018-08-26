/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

import java.util.concurrent.ExecutionException;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDiskIOException;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.data.Conversation;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.reporting.ReportingManager;


public class QuickReplyActivity extends ToolbarActivity {

    private static final String EXTRA_THREAD_ID = "org.kontalk.quickreply.THREAD_ID";
    private static final String EXTRA_MESSAGE = "org.kontalk.quickreply.MESSAGE";

    private TextView mContent;
    private EditText mContentEdit;
    private ImageButton mReply;

    Conversation mConversation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.quick_reply);

        setupToolbar(false, false);

        mContent = findViewById(R.id.content);
        mContentEdit = findViewById(R.id.content_editor);
        mReply = findViewById(R.id.reply);

        processIntent();
    }

    @Override
    protected boolean isNormalUpNavigation() {
        return false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        processIntent();
    }

    private void processIntent() {
        Intent intent = getIntent();

        long threadId = intent.getLongExtra(EXTRA_THREAD_ID, 0);
        mConversation = Conversation.loadFromId(this, threadId);
        if (mConversation == null) {
            // deleted or else
            finish();
        }
        else {
            CharSequence content = intent.getCharSequenceExtra(EXTRA_MESSAGE);

            String title = mConversation.isGroupChat() ?
                mConversation.getGroupSubject() : mConversation.getContact().getDisplayName();
            setTitle(title);

            mContent.setText(content);
        }
    }

    public void reply(View view) {
        if (mContentEdit.getVisibility() == View.VISIBLE) {
            // send reply
            sendTextMessage(mContentEdit.getText().toString());
        }
        else {
            mReply.setImageResource(R.drawable.ic_menu_send);
            mContent.setVisibility(View.GONE);
            mContentEdit.setVisibility(View.VISIBLE);
            mContentEdit.requestFocus();
        }
    }

    public void markRead(View view) {
        MessagesProviderClient.markThreadAsRead(this, mConversation.getRecipient());
        MessagingNotification.delayedUpdateMessagesNotification(this, false);
        finish();
    }

    public void openConversation(View view) {
        // update draft
        MessagesProviderClient.updateDraft(this, mConversation.getThreadId(),
            mContentEdit.getText().toString());
        // open conversation (draft will appear)
        startActivity(ComposeMessage.fromConversation(this, mConversation.getThreadId()));
        finish();
    }

    private void disableEditing() {
        mContentEdit.setEnabled(false);
        mReply.setEnabled(false);
    }

    private final class TextMessageThread extends Thread {
        private final String mText;

        TextMessageThread(String text) {
            mText = text;
        }

        @Override
        public void run() {
            final Context context = QuickReplyActivity.this;
            try {
                Uri newMsg;
                try {
                    newMsg = Kontalk.get().getMessagesController()
                        .sendTextMessage(mConversation, mText, 0).get();
                }
                catch (ExecutionException e) {
                    // unwrap exception
                    throw e.getCause();
                }

                if (newMsg != null) {
                    // mark as read and finish
                    markRead(null);
                }
            }
            catch (SQLiteDiskIOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, R.string.error_store_outbox,
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
            catch (Throwable e) {
                ReportingManager.logException(e);
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(context,
                            R.string.err_store_message_failed,
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    }

    /** Sends out the text message in the composing entry. */
    public void sendTextMessage(String text) {
        if (!TextUtils.isEmpty(text)) {
            disableEditing();

            // start thread
            new TextMessageThread(text).start();

            // hide softkeyboard
            InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mContentEdit.getWindowToken(),
                    InputMethodManager.HIDE_IMPLICIT_ONLY);
        }
    }

    public static Intent getStartIntent(Context context, long threadId, CharSequence content) {
        Intent i = new Intent(context, QuickReplyActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(EXTRA_THREAD_ID, threadId);
        i.putExtra(EXTRA_MESSAGE, content);
        return i;
    }

}
