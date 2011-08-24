package org.kontalk.ui;

import java.util.List;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.RequestClient;
import org.kontalk.client.StatusResponse;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Conversations;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.util.MessageUtils;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


/**
 * Conversation writing activity.
 * @author Daniele Ricci
 * @version 1.0
 */
public class ComposeMessage extends FragmentActivity {
    private static final String TAG = ComposeMessage.class.getSimpleName();

    private static final int REQUEST_CONTACT_PICKER = 9721;

    /** View conversation intent action. Just provide the threadId with this. */
    public static final String ACTION_VIEW_CONVERSATION = "org.kontalk.conversation.VIEW";
    /** View conversation with userId intent action. Just provide userId with this. */
    public static final String ACTION_VIEW_USERID = "org.kontalk.conversation.VIEW_USERID";

    private TextView mLastSeenBanner;
    private EditText mTextEntry;

    /** The thread id. */
    private long threadId = -1;
    private Conversation mConversation;

    /** The SEND intent. */
    private Intent sendIntent;

    /** The user we are talking to. */
    private String userId;
    private String userName;
    private String userPhone;

    private void processIntent(Bundle savedInstanceState) {
        Intent intent = null;
        if (savedInstanceState != null) {
            Log.w(TAG, "restoring from saved instance");
            Uri uri = savedInstanceState.getParcelable(Uri.class.getName());
            //threadId = ContentUris.parseId(uri);
            intent = new Intent(ACTION_VIEW_CONVERSATION, uri);
        }
        else {
            intent = getIntent();
        }

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

        if (userId != null && MessagingPreferences.getLastSeenEnabled(this)) {
            // FIXME this should be handled better and of course honour activity
            // pause/resume/saveState/restoreState/display rotation.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String text = null;
                    try {
                        Context context = ComposeMessage.this;
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
                                    text = "Last seen: " +
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
                        // TODO animation??
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mLastSeenBanner.setText(bannerText);
                                mLastSeenBanner.setVisibility(View.VISIBLE);
                                mLastSeenBanner.startAnimation(
                                        AnimationUtils.loadAnimation(
                                                ComposeMessage.this, R.anim.header_appear));
                            }
                        });
                    }
                }
            }).start();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
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

    private void chooseContact() {
        // TODO one day it will be like this
        // Intent i = new Intent(Intent.ACTION_PICK, Users.CONTENT_URI);
        Intent i = new Intent(this, ContactsListActivity.class);
        startActivityForResult(i, REQUEST_CONTACT_PICKER);
    }

    /*
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
    */

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

    /*
    private void processStart() {
        // opening for contact picker - do nothing
        if (threadId < 0 && sendIntent != null) return;

        Log.i(TAG, "starting query with threadId " + threadId);
        if (threadId > 0) {
            startQuery(true);
        }
        else {
            // HACK this is for crappy honeycomb :)
            setProgressBarIndeterminateVisibility(false);

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
    */

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

}

