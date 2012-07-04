/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

import java.util.regex.Pattern;

import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.NumberValidator;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.message.ImageMessage;
import org.kontalk.message.PlainTextMessage;
import org.kontalk.message.VCardMessage;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Conversations;
import org.kontalk.util.MessageUtils;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
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

    /** Used with VIEW actions, scrolls to a specific message. */
    public static final String EXTRA_MESSAGE = "org.kontalk.conversation.MESSAGE";
    /** Used with VIEW actions, highlight a {@link Pattern} in messages. */
    public static final String EXTRA_HIGHLIGHT = "org.kontalk.conversation.HIGHLIGHT";

    /** The SEND intent. */
    private Intent sendIntent;

    private ComposeMessageFragment mFragment;
    private TextView mTitleText;
    private TextView mStatusText;
    private ImageView mAvatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Kontalk.customUI())
            requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        else
            requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.compose_message_screen);

        if (Kontalk.customUI()) {
            getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.compose_message_title_bar);
            mTitleText = (TextView) findViewById(android.R.id.title);
            mStatusText = (TextView) findViewById(R.id.status);
            mAvatar = (ImageView) findViewById(R.id.avatar);

            OnClickListener onclick = new OnClickListener() {
                public void onClick(View v) {
                    if (mFragment != null) {
                        mFragment.switchBanner();
                    }
                }
            };
            mTitleText.setOnClickListener(onclick);
            mStatusText.setOnClickListener(onclick);
        }

        // load the fragment
        mFragment = (ComposeMessageFragment) getSupportFragmentManager()
            .findFragmentById(R.id.fragment_compose_message);

        processIntent(savedInstanceState);
    }

    /** Sets custom title. Pass null to any of the arguments to skip setting it. */
    public void setTitle(CharSequence title, CharSequence subtitle, Contact contact) {
        if (mTitleText != null) {
            if (title != null)
                mTitleText.setText(title);
            if (subtitle != null)
                mStatusText.setText(subtitle);
            if (contact != null) {
                Drawable avatar = contact.getAvatar(this, null);
                if (avatar != null)
                    mAvatar.setImageDrawable(avatar);
            }
        }
        else if (title != null)
            setTitle(title);
    }

    public void titleAttachment(View view) {
        mFragment.selectAttachment();
    }

    private void processIntent(Bundle savedInstanceState) {
        Intent intent = null;
        if (savedInstanceState != null) {
            Uri uri = savedInstanceState.getParcelable(Uri.class.getName());
            intent = new Intent(ACTION_VIEW_CONVERSATION, uri);
        }
        else {
            intent = getIntent();
        }

        if (intent != null) {
            final String action = intent.getAction();
            Bundle args = null;

            // view intent
            // view conversation - just threadId provided
            // view conversation - just userId provided
            if (Intent.ACTION_VIEW.equals(action) ||
                    ACTION_VIEW_CONVERSATION.equals(action) ||
                    ACTION_VIEW_USERID.equals(action)) {
                args = new Bundle();
                Uri uri = intent.getData();
                args.putString("action", action);
                args.putParcelable("data", uri);
                args.putLong(EXTRA_MESSAGE, intent.getLongExtra(EXTRA_MESSAGE, -1));
                args.putString(EXTRA_HIGHLIGHT, intent.getStringExtra(EXTRA_HIGHLIGHT));
            }

            // send external content
            else if (Intent.ACTION_SEND.equals(action)) {
                sendIntent = intent;
                String mime = intent.getType();

                Log.i(TAG, "sending data to someone: " + mime);
                chooseContact();

                // onActivityResult will handle the rest
                return;
            }

            // send to someone
            else if (Intent.ACTION_SENDTO.equals(action)) {
                try {
                    Uri uri = intent.getData();
                    // a phone number should come here...
                    String number = NumberValidator.fixNumber(this,
                            uri.getSchemeSpecificPart(),
                            Authenticator.getDefaultAccountName(this));
                    // compute hash and open conversation
                    String userId = MessageUtils.sha1(number);

                    args = new Bundle();
                    args.putString("action", ComposeMessage.ACTION_VIEW_USERID);
                    args.putParcelable("data", Threads.getUri(userId));
                }
                catch (Exception e) {
                    Log.e(TAG, "invalid intent", e);
                    finish();
                }
            }

            if (args != null) {
                mFragment.setMyArguments(args);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CONTACT_PICKER) {
            if (resultCode == RESULT_OK) {
                Uri threadUri = data.getData();
                if (threadUri != null) {
                    Log.i(TAG, "composing message for conversation: " + threadUri);
                    String userId = threadUri.getLastPathSegment();
                    Intent i = fromUserId(this, userId);
                    if (i != null) {
                        onNewIntent(i);

                        // process SEND intent if necessary
                        if (sendIntent != null)
                            processSendIntent();
                    }
                    else {
                        Toast.makeText(this, R.string.contact_not_registered, Toast.LENGTH_LONG)
                            .show();
                        finish();
                    }
                }
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public static Intent fromUserId(Context context, String userId) {
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

    /** Creates an {@link Intent} for launching the composer for a given {@link Conversation}. */
    public static Intent fromConversation(Context context, Conversation conv) {
        return fromConversation(context, conv.getThreadId());
    }

    /** Creates an {@link Intent} for launching the composer for a given thread Id. */
    public static Intent fromConversation(Context context, long threadId) {
        return new Intent(ComposeMessage.ACTION_VIEW_CONVERSATION,
                ContentUris.withAppendedId(Conversations.CONTENT_URI,
                        threadId),
                context, ComposeMessage.class);
    }

    /** Creates an {@link Intent} for sending a text message. */
    public static Intent sendTextMessage(String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(PlainTextMessage.MIME_TYPE);
        i.putExtra(Intent.EXTRA_TEXT, text);
        return i;
    }

    public static Intent sendMediaMessage(Uri uri, String mime) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        return i;
    }

    private void chooseContact() {
        // TODO one day it will be like this
        // Intent i = new Intent(Intent.ACTION_PICK, Users.CONTENT_URI);
        Intent i = new Intent(this, ContactsListActivity.class);
        startActivityForResult(i, REQUEST_CONTACT_PICKER);
    }

    private void processSendIntent() {
        String mime = sendIntent.getType();

        if (mime.startsWith("*/") || mime.endsWith("/*")) {
            Uri uri = (Uri) sendIntent.getParcelableExtra(Intent.EXTRA_STREAM);
            Log.d(TAG, "looking up mime type for uri " + uri + " (invalid type: " + mime + ")");
            mime = getContentResolver().getType(uri);
            if (mime == null)
                mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri.toString()));
            Log.d(TAG, "using detected mime type " + mime);
        }

        // send text message - just fill the text entry
        if (PlainTextMessage.supportsMimeType(mime)) {
            mFragment.setTextEntry(sendIntent.getCharSequenceExtra(Intent.EXTRA_TEXT));
        }

        else if (ImageMessage.supportsMimeType(mime)) {
            // send image immediately
            mFragment.sendBinaryMessage((Uri) sendIntent.getParcelableExtra(Intent.EXTRA_STREAM), mime, true, ImageMessage.class);
        }

        else if (VCardMessage.supportsMimeType(mime)) {
        	// send vcard immediately
        	mFragment.sendBinaryMessage((Uri) sendIntent.getParcelableExtra(Intent.EXTRA_STREAM), mime, false, VCardMessage.class);
        }

        else {
            // notify to user
            Log.w(TAG, "mime " + mime + " not supported");
            Toast.makeText(this, R.string.send_mime_not_supported, Toast.LENGTH_LONG)
                .show();
        }

        sendIntent = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        processIntent(null);
        mFragment.reload();
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        processIntent(state);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putParcelable(Uri.class.getName(),
                ContentUris.withAppendedId(Conversations.CONTENT_URI,
                        mFragment.getThreadId()));
    }

    public Intent getSendIntent() {
        return sendIntent;
    }

}

