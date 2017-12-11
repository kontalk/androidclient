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

package org.kontalk.ui;

import java.io.IOException;
import java.util.Random;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.util.Preferences;


public class QuickReplyActivity extends Activity {
    private static final String TAG = QuickReplyActivity.class.getSimpleName();

    private TextView mFrom;
    private TextView mContent;
    private EditText mContentEdit;
    private Button mReply;
    private PendingIntent mOpenConv;

    private String userId;
    private String userString;
    private Contact mContact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.quick_reply);

        mFrom = findViewById(R.id.from);
        mContent = findViewById(R.id.content);
        mContentEdit = findViewById(R.id.content_editor);
        mReply = findViewById(R.id.reply);

        processIntent();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        processIntent();
    }

    private void processIntent() {
        Intent intent = getIntent();
        Log.v(TAG, "processing intent: " + intent);

        userId = intent.getStringExtra("org.kontalk.quickreply.FROM");
        mContact = Contact.findByUserId(this, userId);

        mOpenConv = intent.getParcelableExtra("org.kontalk.quickreply.OPEN_INTENT");
        userString = (mContact != null) ? mContact.getName() + "<" + mContact.getNumber() + ">" : getString(R.string.peer_unknown);
        // TODO i18n
        mFrom.setText("From: " + userString);

        String content = intent.getStringExtra("org.kontalk.quickreply.MESSAGE");
        mContent.setText(content);
    }

    public void reply(View view) {
        if (mContentEdit.getVisibility() == View.VISIBLE) {
            // send reply
            sendTextMessage(mContentEdit.getText().toString());
        }
        else {
            // TODO i18n
            mFrom.setText("To: " + userString);
            mContent.setVisibility(View.GONE);
            mContentEdit.setVisibility(View.VISIBLE);
            mContentEdit.requestFocus();
        }
    }

    public void close(View view) {
        finish();
    }

    public void delete(View view) {
        // TODO
    }

    public void openConversation(View view) {
        try {
            mOpenConv.send();
        }
        catch (CanceledException e) {
            Log.e(TAG, "intent canceled!", e);
        }
        finish();
    }

    private void enableEditing(boolean enabled) {
        mContentEdit.setEnabled(enabled);
        mReply.setEnabled(enabled);
    }


    private final class TextMessageThread extends Thread {
        private final String mText;

        TextMessageThread(String text) {
            mText = text;
        }

        @Override
        public void run() {
            final Context ctx = QuickReplyActivity.this;
            try {
                // get encryption key if needed
                String key = null;
                if (Preferences.getEncryptionEnabled(ctx)) {
                    // use recipient phone number
                    key = Contact.numberByUserId(ctx, userId);
                }

                byte[] bytes = mText.getBytes();

                // save to local storage
                ContentValues values = new ContentValues();
                // must supply a message ID...
                values.put(Messages.MESSAGE_ID, "draft" + (new Random().nextInt()));
                values.put(Messages.PEER, userId);
                //values.put(Messages.MIME, PlainTextMessage.MIME_TYPE);
                //values.put(Messages.CONTENT, bytes);
                values.put(Messages.UNREAD, false);
                values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
                values.put(Messages.TIMESTAMP, System.currentTimeMillis());
                values.put(Messages.STATUS, Messages.STATUS_SENDING);
                //values.put(Messages.ENCRYPT_KEY, key);
                //values.put(Messages.LENGTH, bytes.length);
                Uri newMsg = ctx.getContentResolver().insert(
                        Messages.CONTENT_URI, values);
                if (newMsg != null) {
                    // TODO send the message!
                }
                else {
                    throw new IOException();
                }
            }
            catch (Exception e) {
                // whatever
                Log.d(TAG, "broken message thread", e);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ctx,
                            R.string.error_store_outbox, Toast.LENGTH_LONG).show();
                        enableEditing(true);
                    }
                });
            }
        }
    }

    /** Sends out the text message in the composing entry. */
    public void sendTextMessage(String text) {
        if (!TextUtils.isEmpty(text)) {
            enableEditing(false);

            // start thread
            new TextMessageThread(text).start();

            // hide softkeyboard
            InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mContentEdit.getWindowToken(),
                    InputMethodManager.HIDE_IMPLICIT_ONLY);
        }
    }

}
