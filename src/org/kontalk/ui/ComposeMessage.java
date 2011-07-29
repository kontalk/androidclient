package org.kontalk.ui;

import java.security.GeneralSecurityException;
import java.util.Random;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.AbstractMessage;
import org.kontalk.client.ImageMessage;
import org.kontalk.client.MessageSender;
import org.kontalk.client.PlainTextMessage;
import org.kontalk.crypto.Coder;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Conversations;
import org.kontalk.service.DownloadService;
import org.kontalk.service.MessageCenterService;
import org.kontalk.service.MessageCenterService.MessageCenterInterface;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.util.MessageUtils;

import android.accounts.Account;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
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
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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
    private static final int CONVERSATION_QUERY_TOKEN = 8721;

    private static final int REQUEST_CONTACT_PICKER = 9721;

    /** View conversation intent action. Just provide the threadId with this. */
    public static final String ACTION_VIEW_CONVERSATION = "org.kontalk.conversation.VIEW";
    /** View conversation with userId intent action. Just provide userId with this. */
    public static final String ACTION_VIEW_USERID = "org.kontalk.conversation.VIEW_USERID";

    private MessageListQueryHandler mQueryHandler;
    private MessageListAdapter mListAdapter;
    private EditText mTextEntry;
    private Button mSendButton;

    /** The thread id. */
    private long threadId = -1;
    private Conversation mConversation;

    /** The SEND intent. */
    private Intent sendIntent;

    /** The user we are talking to. */
    private String userId;
    private String userName;
    private String userPhone;

    private final MessageListAdapter.OnContentChangedListener mContentChangedListener =
        new MessageListAdapter.OnContentChangedListener() {
        public void onContentChanged(MessageListAdapter adapter) {
            startQuery(true);
        }
    };

    /** Used by the service binder to receive responses from the request worker. */
    //private MessageRequestListener mMessageSenderListener;

    /** Used for binding to the message center to send messages. */
    private class ComposerServiceConnection implements ServiceConnection {
        public final MessageSender job;
        private MessageCenterService service;

        public ComposerServiceConnection(String userId, byte[] text, String mime, Uri msgUri) {
            job = new MessageSender(userId, text, mime, msgUri);
            //job.setListener(mMessageSenderListener);
        }

        public ComposerServiceConnection(String userId, Uri fileUri, String mime, Uri msgUri) {
            job = new MessageSender(userId, fileUri, mime, msgUri);
            //job.setListener(mMessageSenderListener);
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

        mQueryHandler = new MessageListQueryHandler();
        mListAdapter = new MessageListAdapter(this, null);
        mListAdapter.setOnContentChangedListener(mContentChangedListener);
        setListAdapter(mListAdapter);

        registerForContextMenu(getListView());

        /*
        mMessageSenderListener = new MessageRequestListener(this) {
            @Override
            public void response(RequestJob job, List<StatusResponse> res) {
                super.response(job, res);
            }

            @Override
            public boolean error(RequestJob job, Throwable e) {
                return super.error(job, e);
            }
        };
        */

        mTextEntry = (EditText) findViewById(R.id.text_editor);
        mTextEntry.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mSendButton.setEnabled(s.length() > 0);
            }
        });

        mSendButton = (Button) findViewById(R.id.send_button);
        mSendButton.setEnabled(mTextEntry.length() > 0);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTextMessage();
            }
        });

        processIntent(savedInstanceState);
    }

    /** Sends out an image message. */
    private void sendImageMessage(Uri uri, String mime) {
        Log.i(TAG, "sending image: " + uri);
        Uri newMsg = null;

        try {
            String msgId = "draft" + (new Random().nextInt());

            // save to local storage
            ContentValues values = new ContentValues();
            // must supply a message ID...
            values.put(Messages.MESSAGE_ID, msgId);
            values.put(Messages.PEER, userId);
            values.put(Messages.MIME, mime);
            values.put(Messages.CONTENT, ImageMessage.getSampleTextContent(mime));
            values.put(Messages.UNREAD, false);
            values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
            values.put(Messages.TIMESTAMP, System.currentTimeMillis());
            values.put(Messages.STATUS, Messages.STATUS_SENDING);
            values.put(Messages.LOCAL_URI, uri.toString());
            values.put(Messages.FETCHED, true);
            newMsg = getContentResolver().insert(Messages.CONTENT_URI, values);
        }
        catch (Exception e) {
            Log.e(TAG, "unable to store media", e);
        }

        if (newMsg != null) {

            // update thread id from the inserted message
            if (threadId <= 0) {
                Cursor c = getContentResolver().query(newMsg, new String[] { Messages.THREAD_ID }, null, null, null);
                if (c.moveToFirst()) {
                    threadId = c.getLong(0);
                    Log.i(TAG, "starting query with threadId " + threadId);
                    mConversation = null;
                    startQuery(true);
                }
                else
                    Log.i(TAG, "no data - cannot start query for this composer");
                c.close();
            }

            // send the message!
            ComposerServiceConnection conn = new ComposerServiceConnection(userId, uri, mime, newMsg);
            if (!bindService(
                    new Intent(getApplicationContext(), MessageCenterService.class),
                    conn, Context.BIND_AUTO_CREATE)) {
                // cannot bind :(
                //mMessageSenderListener.error(conn.job, new IllegalArgumentException("unable to bind to message center"));
            }
        }
        else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ComposeMessage.this,
                            "Unable to store message to outbox.",
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /** Sends out the text message in the composing entry. */
    private void sendTextMessage() {
        String text = mTextEntry.getText().toString();
        if (!TextUtils.isEmpty(text)) {
            Log.w(TAG, "sending message...");
            // save to local storage
            ContentValues values = new ContentValues();
            // must supply a message ID...
            values.put(Messages.MESSAGE_ID, "draft" + (new Random().nextInt()));
            values.put(Messages.PEER, userId);
            values.put(Messages.MIME, PlainTextMessage.MIME_TYPE);
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
                        mConversation = null;
                        startQuery(true);
                    }
                    else
                        Log.i(TAG, "no data - cannot start query for this composer");
                    c.close();
                }

                // hide softkeyboard
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mTextEntry.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);

                // send the message!
                ComposerServiceConnection conn = new ComposerServiceConnection(userId, text.getBytes(), PlainTextMessage.MIME_TYPE, newMsg);
                if (!bindService(
                        new Intent(getApplicationContext(), MessageCenterService.class),
                        conn, Context.BIND_AUTO_CREATE)) {
                    // cannot bind :(
                    //mMessageSenderListener.error(conn.job, new IllegalArgumentException("unable to bind to service"));
                }
            }
            else {
                Toast.makeText(ComposeMessage.this,
                        "Unable to store message to outbox.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.compose_message_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean contactEnabled = ((mConversation != null) ? mConversation.getContact() != null : null);
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
                if (mConversation != null) {
                    Contact contact = mConversation.getContact();
                    if (contact != null)
                        startActivity(new Intent(Intent.ACTION_VIEW, contact.getUri()));
                }
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
    private static final int MENU_DECRYPT = 3;
    private static final int MENU_VIEW_IMAGE = 4;
    private static final int MENU_DOWNLOAD = 5;
    private static final int MENU_DETAILS = 6;
    private static final int MENU_DELETE = 7;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        MessageListItem vitem = (MessageListItem) info.targetView;
        AbstractMessage<?> msg = vitem.getMessage();

        menu.setHeaderTitle(R.string.title_message_options);
        menu.add(Menu.NONE, MENU_FORWARD, MENU_FORWARD, R.string.forward);

        if (msg instanceof ImageMessage) {
            // we are able to view image if either we fetched the image or we sent that
            if (msg.isFetched() || msg.getDirection() == Messages.DIRECTION_OUT)
                menu.add(Menu.NONE, MENU_VIEW_IMAGE, MENU_VIEW_IMAGE, R.string.view_image);
            else
                menu.add(Menu.NONE, MENU_DOWNLOAD, MENU_DOWNLOAD, "Download file");
        }
        else {
            if (msg.isEncrypted())
                menu.add(Menu.NONE, MENU_DECRYPT, MENU_DECRYPT, "Decrypt message");
            else
                menu.add(Menu.NONE, MENU_COPY_TEXT, MENU_COPY_TEXT, R.string.copy_message_text);
        }

        menu.add(Menu.NONE, MENU_DETAILS, MENU_DETAILS, R.string.menu_message_details);
        menu.add(Menu.NONE, MENU_DELETE, MENU_DELETE, R.string.delete_message);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        MessageListItem v = (MessageListItem) info.targetView;
        AbstractMessage<?> msg = v.getMessage();

        switch (item.getItemId()) {
            case MENU_FORWARD: {
                // TODO message forwarding
                return true;
            }

            case MENU_COPY_TEXT: {
                Log.i(TAG, "copying message text: " + msg.getId());
                ClipboardManager cpm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cpm.setText(msg.getTextContent());

                Toast.makeText(this, R.string.message_text_copied, Toast.LENGTH_SHORT)
                    .show();
                return true;
            }

            case MENU_DECRYPT: {
                Log.i(TAG, "decrypting message: " + msg.getId());
                Account acc = Authenticator.getDefaultAccount(this);
                Coder coder = MessagingPreferences.getDecryptCoder(this, acc.name);
                try {
                    // decrypt the message
                    msg.decrypt(coder);
                    // update database
                    ContentValues values = new ContentValues();
                    values.put(Messages.CONTENT, msg.getTextContent());
                    values.put(Messages.MIME, msg.getMime());
                    getContentResolver()
                        .update(
                            Messages.getUri(msg.getId()),
                            values, null, null);
                }
                catch (GeneralSecurityException e) {
                    Log.e(TAG, "unable to decrypt message", e);
                    Toast.makeText(this, "Decryption failed!", Toast.LENGTH_LONG)
                    .show();
                }
                return true;
            }

            case MENU_VIEW_IMAGE: {
                Log.i(TAG, "opening image");
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(msg.getLocalUri(), msg.getMime());
                startActivity(i);
                return true;
            }

            case MENU_DOWNLOAD: {
                Log.i(TAG, "downloading attachment");
                Intent i = new Intent(this, DownloadService.class);
                i.setAction(DownloadService.ACTION_DOWNLOAD_URL);
                i.putExtra(AbstractMessage.MSG_ID, msg.getId());
                i.setData(Uri.parse(msg.getFetchUrl()));
                startService(i);
                return true;
            }

            case MENU_DETAILS: {
                Log.i(TAG, "opening message details");
                CharSequence messageDetails = MessageUtils.getMessageDetails(this, msg, userPhone != null ? userPhone : userId);
                new AlertDialog.Builder(this)
                        .setTitle(R.string.title_message_details)
                        .setMessage(messageDetails)
                        .setPositiveButton(android.R.string.ok, null)
                        .setCancelable(true)
                        .show();
                return true;
            }

            case MENU_DELETE: {
                Log.i(TAG, "deleting message: " + msg.getDatabaseId());

                getContentResolver()
                    .delete(ContentUris.withAppendedId(Messages.CONTENT_URI,
                            msg.getDatabaseId()), null, null);
                return true;
            }
        }

        return super.onContextItemSelected(item);
    }

    private void startQuery(boolean reloadConversation) {
        try {
            setProgressBarIndeterminateVisibility(true);

            Log.i(TAG, "starting query for thread " + threadId);
            AbstractMessage.startQuery(mQueryHandler, MESSAGE_LIST_QUERY_TOKEN, threadId);

            if (reloadConversation)
                Conversation.startQuery(mQueryHandler, CONVERSATION_QUERY_TOKEN, threadId);
        } catch (SQLiteException e) {
            Log.e(TAG, "query error", e);
        }
    }

    private void chooseContact() {
        // TODO one day it will be like this
        // Intent i = new Intent(Intent.ACTION_PICK, Users.CONTENT_URI);
        Intent i = new Intent(this, ContactsListActivity.class);
        startActivityForResult(i, REQUEST_CONTACT_PICKER);
    }

    private void processSendIntent() {
        final String mime = sendIntent.getType();
        // send text message - just fill the text entry
        if (PlainTextMessage.supportsMimeType(mime)) {
            mTextEntry.setText(sendIntent.getCharSequenceExtra(Intent.EXTRA_TEXT));
        }

        else if (ImageMessage.supportsMimeType(mime)) {
            // send image immediately
            sendImageMessage((Uri) sendIntent.getParcelableExtra(Intent.EXTRA_STREAM), mime);
        }
        else {
            Log.e(TAG, "mime " + mime + " not supported");
        }

        sendIntent = null;
    }

    private void loadConversationMetadata(Uri uri) {
        threadId = ContentUris.parseId(uri);
        mConversation = Conversation.loadFromId(this, threadId);

        userId = mConversation.getRecipient();
        Contact contact = mConversation.getContact();
        if (contact != null) {
            userName = contact.getName();
            userPhone = contact.getNumber();
        }
        else {
            userName = userId;
        }
    }

    private void processIntent(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            Log.w(TAG, "restoring from saved instance");
            Uri uri = savedInstanceState.getParcelable(Uri.class.getName());
            threadId = ContentUris.parseId(uri);
            // TODO what else here??
            return;
        }

        Intent intent = getIntent();
        if (intent != null) {
            final String action = intent.getAction();

            // view intent
            if (Intent.ACTION_VIEW.equals(action)) {
                Uri uri = intent.getData();
                Log.w(TAG, "intent uri: " + uri);
                ContentResolver cres = getContentResolver();

                Cursor c = cres.query(uri, new String[] {
                        SyncAdapter.DATA_COLUMN_DISPLAY_NAME,
                        SyncAdapter.DATA_COLUMN_PHONE
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
                            new String[] { Messages.THREAD_ID },
                            Messages.PEER + " = ?", new String[] { userId }, null);
                    if (cp.moveToFirst())
                        threadId = cp.getLong(0);
                    cp.close();
                }
                c.close();
            }

            // send external content
            else if (Intent.ACTION_SEND.equals(action)) {
                sendIntent = intent;
                String mime = intent.getType();

                Log.i(TAG, "sending data to someone: " + mime);
                chooseContact();

                // don't do other things - onActivityResult will handle the rest
                return;
            }

            // view conversation - just threadId provided
            else if (ACTION_VIEW_CONVERSATION.equals(action)) {
                Uri uri = intent.getData();
                loadConversationMetadata(uri);
            }

            // view conversation - just userId provided
            else if (ACTION_VIEW_USERID.equals(action)) {
                Uri uri = intent.getData();
                userId = uri.getPathSegments().get(1);
                mConversation = Conversation.loadFromUserId(this, userId);

                if (mConversation == null) {
                    mConversation = Conversation.createNew(this);
                    mConversation.setRecipient(userId);
                }

                threadId = mConversation.getThreadId();
                Contact contact = mConversation.getContact();
                if (contact != null) {
                    userName = contact.getName();
                    userPhone = contact.getNumber();
                }
                else {
                    userName = userId;
                }
            }
        }
    }

    private void processStart() {
        // opening for contact picker - do nothing
        if (threadId < 0 && sendIntent != null) return;

        Log.i(TAG, "starting query with threadId " + threadId);
        if (threadId > 0) {
            startQuery(true);
        }
        else {
            mConversation = Conversation.createNew(this);
            mConversation.setRecipient(userId);

            if (sendIntent != null)
                processSendIntent();
        }

        String title = userName;
        if (userPhone != null)
            title += " <" + userPhone + ">";
        setTitle(title);
    }

    @Override
    protected void onStart() {
        super.onStart();

        processStart();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CONTACT_PICKER) {
            if (resultCode == RESULT_OK) {
                Uri rawContact = data.getData();
                if (rawContact != null) {
                    Log.i(TAG, "composing message for contact: " + rawContact);
                    Intent i = fromContactPicker(this, rawContact);
                    if (i != null) {
                        onNewIntent(i);
                    }
                    else {
                        Toast.makeText(this, "Contact seems not to be registered on Kontalk.", Toast.LENGTH_LONG)
                            .show();
                        finish();
                    }
                }
            }
            // nothing to do - exit
            else {
                Log.w(TAG, "unknown request code " + requestCode);
                finish();
            }
        }
    }

    public static Intent fromContactPicker(Context context, Uri rawContactUri) {
        String userId = Contact.getUserId(context, rawContactUri);
        if (userId != null) {
            Conversation conv = Conversation.loadFromUserId(context, userId);
            // not found - create new
            if (conv == null) {
                Intent ni = new Intent(context, ComposeMessage.class);
                ni.setAction(ComposeMessage.ACTION_VIEW_USERID);
                ni.setData(Threads.getUri(userId));
                return ni;
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
        return new Intent(ComposeMessage.ACTION_VIEW_CONVERSATION,
                ContentUris.withAppendedId(Conversations.CONTENT_URI,
                        conv.getThreadId()),
                context, ComposeMessage.class);
    }

    public static Intent fromConversation(Context context, long threadId) {
        return new Intent(ComposeMessage.ACTION_VIEW_CONVERSATION,
                ContentUris.withAppendedId(Conversations.CONTENT_URI,
                        threadId),
                context, ComposeMessage.class);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    /** Prevents the list adapter from using the cursor (which is being destroyed). */
    @Override
    protected void onStop() {
        super.onStop();
        mListAdapter.changeCursor(null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        processIntent(null);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        processIntent(state);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putParcelable(Uri.class.getName(),
                ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId));
        super.onSaveInstanceState(out);
    }

    /*
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            if (mListAdapter.getCursor() == null) {
                startQuery(true);
            }
        }
        else {
                mListAdapter.changeCursor(null);
                mQueryHandler.cancelOperation(MESSAGE_LIST_QUERY_TOKEN);
                mQueryHandler.cancelOperation(CONVERSATION_QUERY_TOKEN);
        }

        super.onWindowFocusChanged(hasFocus);
    }
    */

    /**
     * The conversation list query handler.
     */
    private final class MessageListQueryHandler extends AsyncQueryHandler {
        public MessageListQueryHandler() {
            super(getApplicationContext().getContentResolver());
        }

        @Override
        protected synchronized void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (cursor == null) {
                Log.e(TAG, "query aborted or error!");
                mListAdapter.changeCursor(null);
                return;
            }

            switch (token) {
                case MESSAGE_LIST_QUERY_TOKEN:
                    mListAdapter.changeCursor(cursor);
                    setProgressBarIndeterminateVisibility(false);

                    // no messages to show - exit
                    if (mListAdapter.getCount() == 0) {
                        Log.w(TAG, "no data to view - exit");
                        finish();
                    }

                    break;

                case CONVERSATION_QUERY_TOKEN:
                    Log.i(TAG, "conversation query completed, marking as read");
                    if (cursor.moveToFirst()) {
                        mConversation = Conversation.createFromCursor(ComposeMessage.this, cursor);
                        // mark all messages as read
                        mConversation.markAsRead();
                    }

                    cursor.close();

                    break;

                default:
                    Log.e(TAG, "onQueryComplete called with unknown token " + token);
            }

            // did we have a SEND action message to be sent?
            if (mListAdapter.getCursor() != null && mConversation != null && sendIntent != null)
                processSendIntent();
        }
    }
}

