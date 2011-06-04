package org.nuntius.ui;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.nuntius.R;
import org.nuntius.client.AbstractMessage;
import org.nuntius.client.MessageSender;
import org.nuntius.client.StatusResponse;
import org.nuntius.provider.MyMessages.Messages;
import org.nuntius.service.MessageCenterService;
import org.nuntius.service.RequestJob;
import org.nuntius.service.ResponseListener;
import org.nuntius.service.MessageCenterService.MessageCenterInterface;

import android.app.ListActivity;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;


/**
 * Conversation writing activity.
 * @author Daniele Ricci
 * @version 1.0
 */
public class ComposeMessage extends ListActivity {
    private static final String TAG = ComposeMessage.class.getSimpleName();

    private static final int MESSAGE_LIST_QUERY_TOKEN = 8720;

    /** Used on the launch intent to pass the thread ID. */
    public static final String MESSAGE_THREAD_ID = "org.nuntius.message.threadId";
    /** Used on the launch intent to pass the peer of the thread. */
    public static final String MESSAGE_THREAD_PEER = "org.nuntius.message.peer";

    private MessageListQueryHandler mQueryHandler;
    private MessageListAdapter mListAdapter;
    private EditText mTextEntry;

    /** The thread id. */
    private long threadId;

    /** The user we are talking to. */
    private String userId;

    private final MessageListAdapter.OnContentChangedListener mContentChangedListener =
        new MessageListAdapter.OnContentChangedListener() {
        public void onContentChanged(MessageListAdapter adapter) {
            startQuery();
        }
    };

    /** Used by the service binder to receive responses from the request worker. */
    private final ResponseListener mMessageSenderListener =
        new ResponseListener() {
        @Override
        public void response(RequestJob job, List<StatusResponse> res) {
            MessageSender job2 = (MessageSender) job;
            if (res != null && res.size() > 0) {
                Uri uri = job2.getUri();
                StatusResponse st = res.get(0);

                // message accepted!
                if (st.code == StatusResponse.STATUS_SUCCESS) {
                    Map<String, String> extra = st.extra;
                    if (extra != null) {
                        String msgId = extra.get("i");
                        if (!TextUtils.isEmpty(msgId)) {
                            ContentValues values = new ContentValues(1);
                            values.put(Messages.MESSAGE_ID, msgId);
                            values.put(Messages.STATUS, Messages.STATUS_SENT);
                            int n = getContentResolver().update(uri, values, null, null);
                            Log.i(TAG, "message sent and updated (" + n + ")");
                        }
                    }
                }

                // message refused!
                else {
                    ContentValues values = new ContentValues(1);
                    values.put(Messages.STATUS, Messages.STATUS_NOTACCEPTED);
                    getContentResolver().update(uri, values, null, null);
                    Log.w(TAG, "message not accepted by server and updated (" + st.code + ")");
                }
            }
            else {
                // empty response!? :O
                error(job, new IllegalArgumentException("empty response"));
            }
        }

        @Override
        public void error(RequestJob job, Throwable e) {
            MessageSender job2 = (MessageSender) job;
            Uri uri = job2.getUri();
            ContentValues values = new ContentValues(1);
            values.put(Messages.STATUS, Messages.STATUS_ERROR);
            getContentResolver().update(uri, values, null, null);
            Log.e(TAG, "error sending message", e);
        }
    };

    /** Used for binding to the message center to send messages. */
    private class ComposerServiceConnection implements ServiceConnection {
        public final MessageSender job;
        private MessageCenterService service;

        public ComposerServiceConnection(String userId, String text, Uri uri) {
            job = new MessageSender(userId, text, uri);
            job.setListener(mMessageSenderListener);
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
            unbindService(this);
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.compose_message);

        mQueryHandler = new MessageListQueryHandler(getContentResolver());
        mListAdapter = new MessageListAdapter(this, null);
        mListAdapter.setOnContentChangedListener(mContentChangedListener);
        setListAdapter(mListAdapter);

        mTextEntry = (EditText) findViewById(R.id.text_editor);
        Button sendButton = (Button) findViewById(R.id.send_button);
        sendButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = mTextEntry.getText().toString();
                if (!TextUtils.isEmpty(text)) {
                    Log.w(TAG, "sending message...");
                    // save to local storage
                    ContentValues values = new ContentValues();
                    // must supply a message ID...
                    values.put(Messages.MESSAGE_ID, "draft" + (new Random().nextInt()));
                    values.put(Messages.PEER, userId);
                    values.put(Messages.MIME, "text/plain");
                    values.put(Messages.CONTENT, text);
                    values.put(Messages.UNREAD, false);
                    values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
                    values.put(Messages.TIMESTAMP, System.currentTimeMillis());
                    values.put(Messages.STATUS, Messages.STATUS_SENDING);
                    Uri newMsg = getContentResolver().insert(Messages.CONTENT_URI, values);
                    if (newMsg != null) {
                        // empty text
                        mTextEntry.setText("");

                        // hide softkeyboard
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(mTextEntry.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);

                        // send the message!
                        ComposerServiceConnection conn = new ComposerServiceConnection(userId, text, newMsg);
                        if (!bindService(
                                new Intent(getApplicationContext(), MessageCenterService.class),
                                conn, Context.BIND_AUTO_CREATE)) {
                            // cannot bind :(
                            mMessageSenderListener.error(conn.job, new IllegalArgumentException("unable to bind to service"));
                        }
                    }
                    else {
                        Toast.makeText(ComposeMessage.this,
                                "Unable to store message to outbox.",
                                Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }

    private void startQuery() {
        try {
            setProgressBarIndeterminateVisibility(true);

            Log.i(TAG, "starting query for thread " + threadId);
            AbstractMessage.startQuery(mQueryHandler, MESSAGE_LIST_QUERY_TOKEN, threadId);
        } catch (SQLiteException e) {
            Log.e(TAG, "query error", e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = getIntent();
        if (intent != null) {
            userId = intent.getStringExtra(MESSAGE_THREAD_PEER);
            setTitle(userId);

            threadId = intent.getLongExtra(MESSAGE_THREAD_ID, -1);
            if (threadId > 0)
                startQuery();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    /**
     * Prevents the list adapter from using the cursor (which is being destroyed).
     */
    @Override
    protected void onStop() {
        super.onStop();
        mListAdapter.changeCursor(null);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // TODO ehm :)
    }

    /**
     * The conversation list query handler.
     */
    private final class MessageListQueryHandler extends AsyncQueryHandler {
        public MessageListQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch (token) {
            case MESSAGE_LIST_QUERY_TOKEN:
                mListAdapter.changeCursor(cursor);
                setProgressBarIndeterminateVisibility(false);
                break;

            default:
                Log.e(TAG, "onQueryComplete called with unknown token " + token);
            }
        }
    }
}

