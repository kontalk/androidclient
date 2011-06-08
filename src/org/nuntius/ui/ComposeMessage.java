package org.nuntius.ui;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.nuntius.R;
import org.nuntius.client.AbstractMessage;
import org.nuntius.client.MessageSender;
import org.nuntius.client.StatusResponse;
import org.nuntius.data.Contact;
import org.nuntius.data.Conversation;
import org.nuntius.provider.MessagesProvider;
import org.nuntius.provider.MyMessages.Messages;
import org.nuntius.service.MessageCenterService;
import org.nuntius.service.RequestJob;
import org.nuntius.service.ResponseListener;
import org.nuntius.service.MessageCenterService.MessageCenterInterface;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;


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
    /** Used on the launch intent to pass the name of the peer. */
    public static final String MESSAGE_THREAD_USERNAME = "org.nuntius.message.user.name";
    /** Used on the launch intent to pass the phone of the peer. */
    public static final String MESSAGE_THREAD_USERPHONE = "org.nuntius.message.user.phone";

    private MessageListQueryHandler mQueryHandler;
    private MessageListAdapter mListAdapter;
    private EditText mTextEntry;

    /** The thread id. */
    private long threadId = -1;

    /** The user we are talking to. */
    private String userId;
    private String userName;
    private String userPhone;
    private Contact userContact;

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

        registerForContextMenu(getListView());

        mTextEntry = (EditText) findViewById(R.id.text_editor);
        Button sendButton = (Button) findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
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

                        // update thread id from the inserted message
                        if (threadId <= 0) {
                            Cursor c = getContentResolver().query(newMsg, new String[] { Messages.THREAD_ID }, null, null, null);
                            if (c.moveToFirst()) {
                                threadId = c.getLong(0);
                                Log.i(TAG, "starting query with threadId " + threadId);
                                startQuery();
                            }
                            else
                                Log.i(TAG, "no data - cannot start query for this composer");
                            c.close();
                        }

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.compose_message_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean contactEnabled = (userContact != null);
        boolean threadEnabled = (threadId > 0);
        MenuItem i;

        i = menu.findItem(R.id.call_contact);
        i.setEnabled(contactEnabled);
        i = menu.findItem(R.id.view_contact);
        i.setEnabled(contactEnabled);
        i = menu.findItem(R.id.delete_thread);
        i.setEnabled(threadEnabled);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.call_contact:
                startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + userPhone)));
                return true;

            case R.id.view_contact:
                startActivity(new Intent(Intent.ACTION_VIEW, userContact.getUri()));
                return true;

            case R.id.delete_thread:
                if (threadId > 0)
                    deleteThread();

                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void deleteThread() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_delete_thread);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.confirm_will_delete_thread);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MessagesProvider.deleteThread(ComposeMessage.this, threadId);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    private static final int MENU_FORWARD = 1;
    private static final int MENU_COPY_TEXT = 2;
    private static final int MENU_DELETE = 3;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        Log.i(TAG, "onCreateContextMenu");
        menu.setHeaderTitle("Message options");
        menu.add(Menu.NONE, MENU_FORWARD, MENU_FORWARD, R.string.forward);
        menu.add(Menu.NONE, MENU_COPY_TEXT, MENU_COPY_TEXT, R.string.copy_message_text);
        menu.add(Menu.NONE, MENU_DELETE, MENU_DELETE, "Delete message");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        MessageListItem v = (MessageListItem) info.targetView;
        AbstractMessage<?> msg = v.getMessage();

        switch (item.getItemId()) {
            case MENU_FORWARD:
                // TODO
                return true;

            case MENU_COPY_TEXT:
                Log.i(TAG, "copying message text: " + msg.getId());
                ClipboardManager cpm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cpm.setText(msg.getTextContent());

                Toast.makeText(this, R.string.message_text_copied, Toast.LENGTH_SHORT)
                    .show();
                return true;

            case MENU_DELETE:
                Log.i(TAG, "deleting message: " + msg.getId());

                getContentResolver()
                    .delete(Messages.getUri(msg.getId()), null, null);
                return true;
        }

        return super.onContextItemSelected(item);
    }

    // TODO handle onNewIntent()

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
            final String action = intent.getAction();

            // view intent
            if (Intent.ACTION_VIEW.equals(action)) {
                Uri uri = intent.getData();
                Log.w(TAG, "intent uri: " + uri);
                ContentResolver cres = getContentResolver();

                Cursor c = cres.query(uri, new String[] {
                        ContactsContract.Data.DATA1,
                        ContactsContract.Data.DATA3
                        }, null, null, null);
                if (c.moveToFirst()) {
                    userName = c.getString(0);
                    userPhone = c.getString(1);

                    // FIXME should it be retrieved from RawContacts.SYNC3 ??
                    try {
                        userId = MessageUtils.sha1(userPhone);
                    }
                    catch (Exception e) {
                        Log.e(TAG, "sha1 digest failed", e);
                    }

                    Cursor cp = cres.query(Messages.CONTENT_URI,
                            new String[] { Messages.THREAD_ID }, Messages.PEER + " = ?", new String[] { userId }, null);
                    if (cp.moveToFirst())
                        threadId = cp.getLong(0);
                    cp.close();
                }
                c.close();
            }

            // private launch intent
            else {
                userName = intent.getStringExtra(MESSAGE_THREAD_USERNAME);
                if (userName == null)
                    userName = userId;
                userPhone = intent.getStringExtra(MESSAGE_THREAD_USERPHONE);
                userId = intent.getStringExtra(MESSAGE_THREAD_PEER);
                threadId = intent.getLongExtra(MESSAGE_THREAD_ID, -1);
            }

            Log.i(TAG, "starting query with threadId " + threadId);
            if (threadId > 0)
                startQuery();

            String title = userName;
            if (userPhone != null)
                title += " <" + userPhone + ">";
            setTitle(title);
        }

        userContact = Contact.findbyUserId(this, userId);
    }

    public static Intent fromContactPicker(Context context, Uri contactUri) {
        String userId = Contact.getUserId(context, contactUri);
        if (userId != null) {
            Conversation conv = Conversation.loadFromUserId(context, userId);
            // not found - create new
            if (conv == null) {
                conv = Conversation.createNew(context);
                conv.setRecipient(userId);
            }

            return fromConversation(context, conv);
        }

        return null;
    }

    /**
     * Creates an {@link Intent} for launching the composer for a given {@link Conversation}.
     * @param context
     * @param conv
     * @return
     */
    public static Intent fromConversation(Context context, Conversation conv) {
        Intent i = new Intent(context, ComposeMessage.class);
        i.putExtra(ComposeMessage.MESSAGE_THREAD_ID, conv.getThreadId());
        i.putExtra(ComposeMessage.MESSAGE_THREAD_PEER, conv.getRecipient());
        Contact contact = conv.getContact();
        if (contact != null) {
            i.putExtra(ComposeMessage.MESSAGE_THREAD_USERNAME, contact.getName());
            i.putExtra(ComposeMessage.MESSAGE_THREAD_USERPHONE, contact.getNumber());
        }
        return i;
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
                // no messages to show - exit
                if (mListAdapter.getCount() == 0)
                    finish();
                break;

            default:
                Log.e(TAG, "onQueryComplete called with unknown token " + token);
            }
        }
    }
}

