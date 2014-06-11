/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

import java.util.ArrayList;
import java.util.regex.Pattern;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.NumberValidator;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.message.ImageComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.message.VCardComponent;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Conversations;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;

import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


/**
 * Conversation writing activity.
 * @author Daniele Ricci
 * @version 1.0
 */
public class ComposeMessage extends ActionBarActivity {
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
    private TextView mTitleView;
    private TextView mSubtitleView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.compose_message_screen);

        setupActionBar();

        // load the fragment
        mFragment = (ComposeMessageFragment) getSupportFragmentManager()
            .findFragmentById(R.id.fragment_compose_message);

        View customView = getSupportActionBar().getCustomView();
        mTitleView = (TextView) customView.findViewById(R.id.title);
        mSubtitleView = (TextView) customView.findViewById(R.id.summary);

        processIntent(savedInstanceState);
    }

    @TargetApi(android.os.Build.VERSION_CODES.HONEYCOMB)
    private void setupActionBar() {
        ActionBar bar = getSupportActionBar();
        bar.setDisplayShowHomeEnabled(true);
        bar.setCustomView(R.layout.compose_message_action_view);
        bar.setDisplayShowCustomEnabled(true);
        bar.setDisplayHomeAsUpEnabled(true);
        bar.setHomeButtonEnabled(true);

        // hack to remove padding from activity icon

        int homeId;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
            homeId = android.R.id.home;
        else
            homeId = android.support.v7.appcompat.R.id.home;

        ImageView icon = (ImageView) findViewById(homeId);

        FrameLayout.LayoutParams iconLp = (FrameLayout.LayoutParams) icon.getLayoutParams();
        iconLp.topMargin = iconLp.bottomMargin = 0;
        icon.setLayoutParams(iconLp);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        switch (itemId) {
            case android.R.id.home:
                onAvatarClick();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /** Sets custom title. Pass null to any of the arguments to skip setting it. */
    public void setTitle(CharSequence title, CharSequence subtitle, Contact contact) {
        if (title != null)
            mTitleView.setText(title);
        if (subtitle != null)
            mSubtitleView.setText(subtitle);
        if (contact != null) {
            Drawable avatar = contact.getAvatar(this, null);
            if (avatar == null)
                avatar = getResources().getDrawable(R.drawable.ic_contact_picture);

            getSupportActionBar().setIcon(avatar);
        }
    }

    private void onAvatarClick() {
        finish();
        startActivity(new Intent(this, ConversationList.class));
    }

    public void onTitleboxClick(View view) {
        if (mFragment != null)
            mFragment.viewContact();
    }

    private void processIntent(Bundle savedInstanceState) {
        Intent intent = null;
        if (savedInstanceState != null) {
            Uri uri = savedInstanceState.getParcelable(Uri.class.getName());
            intent = new Intent(ACTION_VIEW_USERID, uri);
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
            else if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
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
                            Authenticator.getDefaultAccountName(this), 0);
                    // compute hash and open conversation
                    String userId = MessageUtils.sha1(number);

                    args = new Bundle();
                    args.putString("action", ComposeMessage.ACTION_VIEW_USERID);
                    args.putParcelable("data", Threads.getUri(userId));
                    args.putString("number", number);
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
            else {
                // no contact chosen or other problems - quit
                finish();
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
        i.setType(TextComponent.MIME_TYPE);
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
        i.putExtra("picker", true);
        startActivityForResult(i, REQUEST_CONTACT_PICKER);
    }

    private void sendMedia(Uri uri) {
        Log.d(TAG, "looking up mime type for uri " + uri);
        String mime = MediaStorage.getType(this, uri);
        Log.d(TAG, "using detected mime type " + mime);

        if (ImageComponent.supportsMimeType(mime)) {
            // send image immediately
            mFragment.sendBinaryMessage(uri, mime, true, ImageComponent.class);
        }

        else if (VCardComponent.supportsMimeType(mime)) {
            mFragment.sendBinaryMessage(uri, mime, true, VCardComponent.class);
        }

        else {
            // notify to user
            Log.w(TAG, "mime " + mime + " not supported");
            Toast.makeText(this, R.string.send_mime_not_supported, Toast.LENGTH_LONG)
                .show();
        }
    }

    private void processSendIntent() {
        String mime = sendIntent.getType();
        boolean multi = Intent.ACTION_SEND_MULTIPLE.equals(sendIntent.getAction());

        if (multi) {
            // multiple texts: take only the first one
            // FIXME this will not allow text file attachments
            if (TextComponent.supportsMimeType(mime)) {
                ArrayList<CharSequence> texts = sendIntent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT);
                if (texts != null && texts.size() > 0)
                    mFragment.setTextEntry(texts.get(0));
            }

            else {
                ArrayList<Uri> uris = sendIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (uris != null) {
                    for (Uri uri : uris) {
                        sendMedia(uri);
                    }
                }
            }
        }

        else {
            // FIXME this will not allow text file attachments
            if (TextComponent.supportsMimeType(mime)) {
                CharSequence text = sendIntent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                mFragment.setTextEntry(text);
            }

            else {
                sendMedia((Uri) sendIntent.getParcelableExtra(Intent.EXTRA_STREAM));
            }
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
        out.putParcelable(Uri.class.getName(), Threads.getUri(mFragment.getUserId()));
    }

    public Intent getSendIntent() {
        return sendIntent;
    }
}
