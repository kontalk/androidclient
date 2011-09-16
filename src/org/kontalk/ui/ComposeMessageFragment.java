package org.kontalk.ui;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Random;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.AbstractMessage;
import org.kontalk.client.ImageMessage;
import org.kontalk.client.MessageSender;
import org.kontalk.client.PlainTextMessage;
import org.kontalk.client.RequestClient;
import org.kontalk.client.StatusResponse;
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
import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ListFragment;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;


/**
 * The composer fragment.
 * @author Daniele Ricci
 */
public class ComposeMessageFragment extends ListFragment {
    private static final String TAG = ComposeMessageFragment.class.getSimpleName();

    private static final int MESSAGE_LIST_QUERY_TOKEN = 8720;
    private static final int CONVERSATION_QUERY_TOKEN = 8721;

    private MessageListQueryHandler mQueryHandler;
    private MessageListAdapter mListAdapter;
    private EditText mTextEntry;
    private Button mSendButton;

    private TextView mLastSeenBanner;

    /** The thread id. */
    private long threadId = -1;
    private Conversation mConversation;
    private Bundle mArguments;

    /** The user we are talking to. */
    private String userId;
    private String userName;
    private String userPhone;


    /** Returns a new fragment instance from a picked contact. */
    public static ComposeMessageFragment fromContactPicker(Context context, Uri rawContactUri) {
        String userId = Contact.getUserId(context, rawContactUri);
        if (userId != null) {
            ComposeMessageFragment f = new ComposeMessageFragment();
            Conversation conv = Conversation.loadFromUserId(context, userId);
            // not found - create new
            if (conv == null) {
                Bundle args = new Bundle();
                args.putString("action", ComposeMessage.ACTION_VIEW_USERID);
                args.putParcelable("data", Threads.getUri(userId));
                f.setArguments(args);
                return f;
            }

            return fromConversation(context, conv);
        }

        return null;
    }

    /** Returns a new fragment instance from a {@link Conversation} instance. */
    public static ComposeMessageFragment fromConversation(Context context, Conversation conv) {
        return fromConversation(context, conv.getThreadId());
    }

    /** Returns a new fragment instance from a thread ID. */
    public static ComposeMessageFragment fromConversation(Context context, long threadId) {
        ComposeMessageFragment f = new ComposeMessageFragment();
        Bundle args = new Bundle();
        args.putString("action", ComposeMessage.ACTION_VIEW_CONVERSATION);
        args.putParcelable("data", ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId));
        f.setArguments(args);
        return f;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
    	super.onActivityCreated(savedInstanceState);
        setListAdapter(mListAdapter);

        registerForContextMenu(getListView());

        mTextEntry = (EditText) getView().findViewById(R.id.text_editor);
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

        mSendButton = (Button) getView().findViewById(R.id.send_button);
        mSendButton.setEnabled(mTextEntry.length() > 0);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTextMessage();
            }
        });

        mLastSeenBanner = (TextView) getView().findViewById(R.id.last_seen_text);

        processArguments(savedInstanceState);
    }

    public void reload() {
        processArguments(null);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
    		Bundle savedInstanceState) {
    	return inflater.inflate(R.layout.compose_message, container, false);
    }

    private final MessageListAdapter.OnContentChangedListener mContentChangedListener =
        new MessageListAdapter.OnContentChangedListener() {
        public void onContentChanged(MessageListAdapter adapter) {
            if (isVisible())
                startQuery(true);
        }
    };

    /** Used by the service binder to receive responses from the request worker. */
    //private MessageRequestListener mMessageSenderListener;

    /** Used for binding to the message center to send messages. */
    private class ComposerServiceConnection implements ServiceConnection {
        public final MessageSender job;
        private MessageCenterService service;

        public ComposerServiceConnection(String userId, byte[] text, String mime, Uri msgUri, String encryptKey) {
            job = new MessageSender(userId, text, mime, msgUri, encryptKey);
            //job.setListener(mMessageSenderListener);
        }

        public ComposerServiceConnection(String userId, Uri fileUri, String mime, Uri msgUri, String encryptKey) {
            job = new MessageSender(userId, fileUri, mime, msgUri, encryptKey);
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
            getActivity().unbindService(this);
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
        mQueryHandler = new MessageListQueryHandler();
        mListAdapter = new MessageListAdapter(getActivity(), null);
        mListAdapter.setOnContentChangedListener(mContentChangedListener);
    }

    /** Sends out an image message. */
    public void sendImageMessage(Uri uri, String mime) {
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
            newMsg = getActivity().getContentResolver().insert(Messages.CONTENT_URI, values);
        }
        catch (Exception e) {
            Log.e(TAG, "unable to store media", e);
        }

        if (newMsg != null) {

            // update thread id from the inserted message
            if (threadId <= 0) {
                Cursor c = getActivity().getContentResolver().query(newMsg, new String[] { Messages.THREAD_ID }, null, null, null);
                if (c.moveToFirst()) {
                    threadId = c.getLong(0);
                    mConversation = null;
                    startQuery(true);
                }
                else
                    Log.i(TAG, "no data - cannot start query for this composer");
                c.close();
            }

            // send the message!
            // FIXME do not encrypt images for now
            ComposerServiceConnection conn = new ComposerServiceConnection(userId, uri, mime, newMsg, null);
            if (!getActivity().bindService(
                    new Intent(getActivity().getApplicationContext(), MessageCenterService.class),
                    conn, Context.BIND_AUTO_CREATE)) {
                // cannot bind :(
                //mMessageSenderListener.error(conn.job, new IllegalArgumentException("unable to bind to message center"));
            }
        }
        else {
        	getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity(),
                            "Unable to store message to outbox.",
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /** Sends out the text message in the composing entry. */
    public void sendTextMessage() {
        String text = mTextEntry.getText().toString();
        if (!TextUtils.isEmpty(text)) {
            Log.w(TAG, "sending message...");
            // get encryption key if needed
            String key = null;
            if (MessagingPreferences.getEncryptionEnabled(getActivity())) {
                key = MessagingPreferences.getDefaultPassphrase(getActivity());
                // no global passphrase defined -- use recipient phone number
                if (key == null || key.length() == 0)
                    key = Contact.numberByUserId(getActivity(), userId);
            }
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
            values.put(Messages.ENCRYPT_KEY, key);
            Uri newMsg = getActivity().getContentResolver().insert(Messages.CONTENT_URI, values);
            if (newMsg != null) {
                // empty text
                mTextEntry.setText("");

                // update thread id from the inserted message
                if (threadId <= 0) {
                    Cursor c = getActivity().getContentResolver().query(newMsg, new String[] { Messages.THREAD_ID }, null, null, null);
                    if (c.moveToFirst()) {
                        threadId = c.getLong(0);
                        mConversation = null;
                        startQuery(true);
                    }
                    else
                        Log.i(TAG, "no data - cannot start query for this composer");
                    c.close();
                }

                // hide softkeyboard
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mTextEntry.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);

                // send the message!
                ComposerServiceConnection conn = new ComposerServiceConnection(userId, text.getBytes(), PlainTextMessage.MIME_TYPE, newMsg, key);
                if (!getActivity().bindService(
                        new Intent(getActivity().getApplicationContext(), MessageCenterService.class),
                        conn, Context.BIND_AUTO_CREATE)) {
                    // cannot bind :(
                    //mMessageSenderListener.error(conn.job, new IllegalArgumentException("unable to bind to service"));
                }
            }
            else {
                Toast.makeText(getActivity(),
                        R.string.error_store_outbox,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.compose_message_menu, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        boolean contactEnabled = (mConversation != null) ? mConversation.getContact() != null : false;
        boolean threadEnabled = (threadId > 0);
        MenuItem i;

        i = menu.findItem(R.id.call_contact);
        if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
        	i.setVisible(false);
    	}
    	else {
        	i.setVisible(true);
        	i.setEnabled(contactEnabled);
        }
        i = menu.findItem(R.id.view_contact);
        i.setEnabled(contactEnabled);
        i = menu.findItem(R.id.delete_thread);
        i.setEnabled(threadEnabled);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.confirm_delete_thread);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.confirm_will_delete_thread);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mTextEntry.setText("");
                MessagesProvider.deleteThread(getActivity(), threadId);
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
                menu.add(Menu.NONE, MENU_DOWNLOAD, MENU_DOWNLOAD, R.string.download_file);
        }
        else {
            if (msg.isEncrypted())
                menu.add(Menu.NONE, MENU_DECRYPT, MENU_DECRYPT, R.string.decrypt_message);
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
                ClipboardManager cpm = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                cpm.setText(msg.getTextContent());

                Toast.makeText(getActivity(), R.string.message_text_copied, Toast.LENGTH_SHORT)
                    .show();
                return true;
            }

            case MENU_DECRYPT: {
                Log.i(TAG, "decrypting message: " + msg.getId());
                Account acc = Authenticator.getDefaultAccount(getActivity());
                Coder coder = MessagingPreferences.getDecryptCoder(getActivity(), acc.name);
                try {
                    // decrypt the message
                    msg.decrypt(coder);
                    // update database
                    ContentValues values = new ContentValues();
                    values.put(Messages.CONTENT, msg.getTextContent());
                    values.put(Messages.MIME, msg.getMime());
                    getActivity().getContentResolver()
                        .update(
                            Messages.getUri(msg.getId()),
                            values, null, null);
                }
                catch (GeneralSecurityException e) {
                    Log.e(TAG, "unable to decrypt message", e);
                    Toast.makeText(getActivity(), "Decryption failed!", Toast.LENGTH_LONG)
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
                Intent i = new Intent(getActivity(), DownloadService.class);
                i.setAction(DownloadService.ACTION_DOWNLOAD_URL);
                i.putExtra(AbstractMessage.MSG_ID, msg.getId());
                i.setData(Uri.parse(msg.getFetchUrl()));
                getActivity().startService(i);
                return true;
            }

            case MENU_DETAILS: {
                Log.i(TAG, "opening message details");
                CharSequence messageDetails = MessageUtils.getMessageDetails(getActivity(), msg, userPhone != null ? userPhone : userId);
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.title_message_details)
                        .setMessage(messageDetails)
                        .setPositiveButton(android.R.string.ok, null)
                        .setCancelable(true)
                        .show();
                return true;
            }

            case MENU_DELETE: {
                Log.i(TAG, "deleting message: " + msg.getDatabaseId());

                getActivity().getContentResolver()
                    .delete(ContentUris.withAppendedId(Messages.CONTENT_URI,
                            msg.getDatabaseId()), null, null);
                return true;
            }
        }

        return super.onContextItemSelected(item);
    }

    private void startQuery(boolean reloadConversation) {
        try {
        	getActivity().setProgressBarIndeterminateVisibility(true);

            Log.i(TAG, "starting query for thread " + threadId);
            AbstractMessage.startQuery(mQueryHandler, MESSAGE_LIST_QUERY_TOKEN, threadId);

            if (reloadConversation)
                Conversation.startQuery(mQueryHandler, CONVERSATION_QUERY_TOKEN, threadId);

        } catch (SQLiteException e) {
            Log.e(TAG, "query error", e);
        }
    }

    private void loadConversationMetadata(Uri uri) {
        threadId = ContentUris.parseId(uri);
        mConversation = Conversation.loadFromId(getActivity(), threadId);

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

    private Bundle myArguments() {
        return (mArguments != null) ? mArguments : getArguments();
    }

    public void setMyArguments(Bundle args) {
        mArguments = args;
    }

    private void processArguments(Bundle savedInstanceState) {
        Bundle args = null;
        if (savedInstanceState != null) {
            Log.w(TAG, "restoring from saved instance");
            Uri uri = savedInstanceState.getParcelable(Uri.class.getName());
            //threadId = ContentUris.parseId(uri);
            args = new Bundle();
            args.putString("action", ComposeMessage.ACTION_VIEW_CONVERSATION);
            args.putParcelable("data", uri);
        }
        else {
        	args = myArguments();
        }

        if (args != null && args.size() > 0) {
            final String action = args.getString("action");

            // view intent
            if (Intent.ACTION_VIEW.equals(action)) {
                Uri uri = args.getParcelable("data");
                Log.w(TAG, "intent uri: " + uri);
                ContentResolver cres = getActivity().getContentResolver();

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
                        // TODO handle error
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

                if (threadId > 0) {
                    mConversation = Conversation.loadFromId(getActivity(), threadId);
                }
                else {
                    mConversation = Conversation.createNew(getActivity());
                    mConversation.setRecipient(userId);
                }
            }

            // view conversation - just threadId provided
            else if (ComposeMessage.ACTION_VIEW_CONVERSATION.equals(action)) {
                Uri uri = args.getParcelable("data");
                loadConversationMetadata(uri);
            }

            // view conversation - just userId provided
            else if (ComposeMessage.ACTION_VIEW_USERID.equals(action)) {
                Uri uri = args.getParcelable("data");
                userId = uri.getPathSegments().get(1);
                mConversation = Conversation.loadFromUserId(getActivity(), userId);

                if (mConversation == null) {
                    mConversation = Conversation.createNew(getActivity());
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

        // set title if we are autonomous
        if (mArguments != null) {
            String title = userName;
            if (userPhone != null)
                title += " <" + userPhone + ">";
            getActivity().setTitle(title);
        }

        // update conversation stuff
        if (mConversation != null)
            onConversationCreated();

        if (userId != null && MessagingPreferences.getLastSeenEnabled(getActivity())) {
            // FIXME this should be handled better and of course honour activity
            // pause/resume/saveState/restoreState/display rotation.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String text = null;
                    try {
                        Context context = getActivity();
                        RequestClient client = new RequestClient(context,
                                MessagingPreferences.getEndpointServer(context),
                                Authenticator.getDefaultAccountToken(context));
                        final List<StatusResponse> data = client.lookup(new String[] { userId });
                        if (data != null && data.size() > 0) {
                            StatusResponse res = data.get(0);
                            if (res.code == StatusResponse.STATUS_SUCCESS) {
                                String timestamp = (String) res.extra.get("t");
                                long time = Long.parseLong(timestamp);
                                if (time > 0)
                                    text = getResources().getString(R.string.last_seen_label) +
                                        MessageUtils.formatTimeStampString(context, time * 1000, true);
                            }
                        }
                    }
                    catch (Exception e) {
                        Log.e(TAG, "unable to lookup user " + userId, e);
                        // TODO better text :D
                        text = "(error)";
                    }

                    if (text != null) {
                        final String bannerText = text;
                        // show last seen banner
                        Activity context = getActivity();
                        if (context != null)
                            context.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mLastSeenBanner.setText(bannerText);
                                    mLastSeenBanner.setVisibility(View.VISIBLE);
                                    mLastSeenBanner.startAnimation(
                                            AnimationUtils.loadAnimation(
                                                    getActivity(), R.anim.header_appear));
                                }
                            });
                    }
                }
            }).start();
        }
    }

    public ComposeMessage getParentActivity() {
        Activity _activity = getActivity();
        return (_activity instanceof ComposeMessage) ?
                (ComposeMessage) _activity : null;
    }

    private void processStart() {
        ComposeMessage activity = getParentActivity();
        // opening for contact picker - do nothing
        if (threadId < 0 && activity != null && activity.getSendIntent() != null) return;

        Log.i(TAG, "starting query with threadId " + threadId);
        if (threadId > 0) {
            startQuery((mConversation == null));
        }
        else {
            // HACK this is for crappy honeycomb :)
            getActivity().setProgressBarIndeterminateVisibility(false);

            mConversation = Conversation.createNew(getActivity());
            mConversation.setRecipient(userId);
            onConversationCreated();
        }
    }

    private void onConversationCreated() {
        // restore draft (if any and only if user hasn't inserted text)
        if (mTextEntry.getText().length() == 0) {
            String draft = mConversation.getDraft();
            if (draft != null)
                mTextEntry.setText(draft);
        }

        // mark all messages as read
        mConversation.markAsRead();
    }

    @Override
    public void onResume() {
        super.onResume();

        // cursor was previously destroyed -- reload everything
        //mConversation = null;
        processStart();
    }

    @Override
    public void onPause() {
        super.onPause();
        CharSequence text = mTextEntry.getText();
        int len = text.length();

        // save last message as draft
        if (threadId > 0) {
            ContentValues values = new ContentValues(1);
            values.put(Threads.DRAFT, (len > 0) ? text.toString() : null);
            getActivity().getContentResolver().update(
                    ContentUris.withAppendedId(Threads.CONTENT_URI, threadId),
                    values, null, null);
        }

        // new thread, create empty conversation
        else {
            // TODO handle aborted draft

            if (len > 0) {
                // save to local storage
                ContentValues values = new ContentValues();
                // must supply a message ID...
                values.put(Messages.MESSAGE_ID, "draft" + (new Random().nextInt()));
                values.put(Messages.PEER, userId);
                values.put(Messages.MIME, PlainTextMessage.MIME_TYPE);
                values.put(Messages.CONTENT, "");
                values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
                values.put(Messages.TIMESTAMP, System.currentTimeMillis());
                values.put(Threads.DRAFT, text.toString());
                getActivity().getContentResolver().insert(Messages.CONTENT_URI, values);
            }
        }

        if (len > 0) {
            // TODO i18n
            Toast.makeText(getActivity(), "Message saved as draft.", Toast.LENGTH_LONG)
                .show();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mListAdapter.changeCursor(null);
    }

    public final boolean isFinishing() {
        return (getActivity() == null ||
                (getActivity() != null && getActivity().isFinishing())) ||
                isRemoving();
    }

    /**
     * The conversation list query handler.
     */
    private final class MessageListQueryHandler extends AsyncQueryHandler {
        public MessageListQueryHandler() {
            super(getActivity().getApplicationContext().getContentResolver());
        }

        @Override
        protected synchronized void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (cursor == null || isFinishing()) {
                // close cursor - if any
                if (cursor != null) cursor.close();

                Log.e(TAG, "query aborted or error!");
                mListAdapter.changeCursor(null);
                return;
            }

            switch (token) {
                case MESSAGE_LIST_QUERY_TOKEN:
                    mListAdapter.changeCursor(cursor);
                    getActivity().setProgressBarIndeterminateVisibility(false);

                    // no messages to show - exit
                    if (mListAdapter.getCount() == 0 &&
                            (mConversation == null || mConversation.getDraft() == null ||
                                    mTextEntry.getText().length() == 0)) {
                        Log.w(TAG, "no data to view - exit");
                        getActivity().finish();
                    }

                    break;

                case CONVERSATION_QUERY_TOKEN:
                    Log.i(TAG, "conversation query completed, marking as read");
                    if (cursor.moveToFirst()) {
                        mConversation = Conversation.createFromCursor(getActivity(), cursor);
                        onConversationCreated();
                    }

                    cursor.close();

                    break;

                default:
                    Log.e(TAG, "onQueryComplete called with unknown token " + token);
            }
        }
    }

    public Conversation getConversation() {
        return mConversation;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setTextEntry(CharSequence text) {
        mTextEntry.setText(text);
    }
}
