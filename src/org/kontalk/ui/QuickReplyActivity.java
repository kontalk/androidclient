package org.kontalk.ui;

import java.io.IOException;
import java.util.Random;

import org.kontalk.xmpp.R;
import org.kontalk.client.MessageSender;
import org.kontalk.data.Contact;
import org.kontalk.message.PlainTextMessage;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.service.MessageCenterServiceLegacy;
import org.kontalk.service.MessageCenterServiceLegacy.MessageCenterInterface;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class QuickReplyActivity extends Activity {
    private static final String TAG = QuickReplyActivity.class.getSimpleName();

    private TextView mFrom;
    private TextView mContent;
    private EditText mContentEdit;
    private Button mReply;
    private PendingIntent mOpenConv;
    private ComposerServiceConnection mConn;

    private String userId;
    private String userString;
    private Contact mContact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.quick_reply);

        mFrom = (TextView) findViewById(R.id.from);
        mContent = (TextView) findViewById(R.id.content);
        mContentEdit = (EditText) findViewById(R.id.content_editor);
        mReply = (Button) findViewById(R.id.reply);

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

    /** Used for binding to the message center to send messages. */
    private final class ComposerServiceConnection implements ServiceConnection {
        public final MessageSender job;
        private MessageCenterServiceLegacy service;

        public ComposerServiceConnection(String userId, byte[] text,
                String mime, Uri msgUri, String encryptKey) {
            job = new MessageSender(userId, text, mime, msgUri, encryptKey, false);
            // listener will be set by message center
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder ibinder) {
            MessageCenterInterface binder = (MessageCenterInterface) ibinder;
            service = binder.getService();
            service.sendMessage(job);
            try {
                unbindService(this);
            }
            catch (Exception e) {
                // ignore exception on exit
            }
            finish();
            service = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            // unbind anyway
            unbindService(mConn);
        }
        catch (Exception e) {
            // ignore
        }
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
                if (MessagingPreferences.getEncryptionEnabled(ctx)) {
                    key = MessagingPreferences.getDefaultPassphrase(ctx);
                    // no global passphrase defined -- use recipient phone number
                    if (key == null || key.length() == 0)
                        key = Contact.numberByUserId(ctx, userId);
                }

                byte[] bytes = mText.getBytes();

                // save to local storage
                ContentValues values = new ContentValues();
                // must supply a message ID...
                values.put(Messages.MESSAGE_ID, "draft" + (new Random().nextInt()));
                values.put(Messages.PEER, userId);
                values.put(Messages.MIME, PlainTextMessage.MIME_TYPE);
                values.put(Messages.CONTENT, bytes);
                values.put(Messages.UNREAD, false);
                values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
                values.put(Messages.TIMESTAMP, System.currentTimeMillis());
                values.put(Messages.STATUS, Messages.STATUS_SENDING);
                values.put(Messages.ENCRYPT_KEY, key);
                values.put(Messages.LENGTH, bytes.length);
                Uri newMsg = ctx.getContentResolver().insert(
                        Messages.CONTENT_URI, values);
                if (newMsg != null) {
                    // send the message!
                    mConn = new ComposerServiceConnection(
                            userId, mText.getBytes(), PlainTextMessage.MIME_TYPE,
                            newMsg, key);
                    if (!bindService(
                            new Intent(getApplicationContext(), MessageCenterServiceLegacy.class),
                            mConn, Context.BIND_AUTO_CREATE)) {
                        // cannot bind :(
                        throw new IllegalArgumentException("unable to bind to service");
                    }
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
