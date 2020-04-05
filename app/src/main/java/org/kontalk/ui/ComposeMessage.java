/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.data.Conversation;
import org.kontalk.message.TextComponent;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Conversations;
import org.kontalk.service.registration.RegistrationService;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.SystemUtils;
import org.kontalk.util.ViewUtils;
import org.kontalk.util.XMPPUtils;


/**
 * Conversation writing activity.
 * @author Daniele Ricci
 */
public class ComposeMessage extends ToolbarActivity implements ComposeMessageParent {
    public static final String TAG = ComposeMessage.class.getSimpleName();

    private static final int REQUEST_CONTACT_PICKER = 9721;

    private static final StyleSpan sUpdatingTextSpan = new StyleSpan(Typeface.ITALIC);

    /** View conversation intent action. Just provide the threadId with this. */
    public static final String ACTION_VIEW_CONVERSATION = "org.kontalk.conversation.VIEW";
    /** View conversation with userId intent action. Just provide userId with this. */
    public static final String ACTION_VIEW_USERID = "org.kontalk.conversation.VIEW_USERID";

    /** Used with VIEW actions, scrolls to a specific message. */
    public static final String EXTRA_MESSAGE = "org.kontalk.conversation.MESSAGE";
    /** Used with VIEW actions, highlight a {@link Pattern} in messages. */
    public static final String EXTRA_HIGHLIGHT = "org.kontalk.conversation.HIGHLIGHT";
    /** Used with SEND actions sent via direct share. */
    public static final String EXTRA_USERID = "org.kontalk.conversation.USERID";
    /** Used internally when reloading: does not trigger scroll-to-match. */
    static final String EXTRA_RELOADING = "org.kontalk.conversation.RELOADING";
    /** Set to true for showing the group chat on creation disclaimer. */
    static final String EXTRA_CREATING_GROUP = "org.kontalk.CREATING_GROUP";

    /** The SEND intent. */
    private Intent mSendIntent;
    /** The SEND intent from direct share. */
    private Intent mDirectSendIntent;

    AbstractComposeFragment mFragment;

    /**
     * True if the window has lost focus the last time
     * {@link #onWindowFocusChanged} was called. */
    private boolean mLostFocus;
    /**
     * This is set to true in {@link #onResume} and to false in {@link #onPause}.
     * It is checked in {@link #onWindowFocusChanged} to ensure that the activity is indeed
     * visible before granting focus capabilities.
     */
    private boolean mResumed;

    /** Toolbar title TextView. Used for applying our emojis. */
    private TextView mTitleView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.compose_message_screen);

        setupActionBar();

        if (savedInstanceState != null) {
            mFragment = (AbstractComposeFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragment_compose_message);
        }

        if (mFragment == null) {
            // build chat fragment
            AbstractComposeFragment f = getComposeFragment(savedInstanceState);
            if (f != null) {
                // insert it into the activity
                setComposeFragment(f);
            }
        }
    }

    private void setupActionBar() {
        Toolbar toolbar = super.setupToolbar(true, true);
        // TODO find a way to use a colored selector
        toolbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFragment == null || !mFragment.isActionModeActive())
                    onTitleClick();
            }
        });

        try {
            // hack to extract the title text view
            mTitleView = (TextView) toolbar.getChildAt(0);
        }
        catch (Exception e) {
            Log.v(TAG, "unable to retrieve toolbar view. Custom emojis will not be applied to activity title.");
        }
    }

    @Override
    protected boolean isNormalUpNavigation() {
        return false;
    }

    private void setComposeFragment(@NonNull AbstractComposeFragment f) {
        mFragment = f;
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_compose_message, f)
            .setTransition(FragmentTransaction.TRANSIT_NONE)
            .commitNowAllowingStateLoss();
    }

    @Override
    public void loadConversation(long threadId, boolean creatingGroup) {
        // create bootstrap intent
        setIntent(ComposeMessage.fromConversation(this, threadId, creatingGroup));
        loadConversation();
    }

    @Override
    public void loadConversation(Uri threadUri) {
        onNewIntent(fromThreadUri(this, threadUri));
    }

    public void loadConversation() {
        // build chat fragment
        AbstractComposeFragment f = getComposeFragment(null);
        if (f != null) {
            // insert it into the activity
            setComposeFragment(f);
        }
        else {
            // conversation disappeared
            finish();
        }
    }

    @Override
    public void setTitle(CharSequence title, CharSequence subtitle) {
        ActionBar bar = getSupportActionBar();
        if (title != null) {
            final CharSequence titleStr;
            if (mTitleView != null) {
                titleStr = ViewUtils.injectEmojis(mTitleView, title);
            }
            else {
                titleStr = title;
            }
            bar.setTitle(titleStr);
        }
        if (subtitle != null)
            bar.setSubtitle(subtitle);
    }

    @Override
    public void setUpdatingSubtitle() {
        ActionBar bar = getSupportActionBar();
        CharSequence current = bar.getSubtitle();
        // no need to set updating status if no text is displayed
        if (current != null && current.length() > 0) {
            bar.setSubtitle(applyUpdatingStyle(current));
        }
    }

    static CharSequence applyUpdatingStyle(CharSequence text) {
        // we call toString() to strip any existing span
        SpannableString status = new SpannableString(text.toString());
        status.setSpan(sUpdatingTextSpan,
            0, status.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return status;
    }

    @Override
    public void onBackPressed() {
        if (mFragment == null || (!mFragment.tryHideAttachmentView() && !mFragment.tryHideEmojiDrawer()))
            super.onBackPressed();
    }

    public void onTitleClick() {
        if (mFragment instanceof ComposeMessageFragment)
            ((ComposeMessageFragment) mFragment).viewContactInfo();
        else if (mFragment instanceof GroupMessageFragment)
            ((GroupMessageFragment) mFragment).viewGroupInfo();
    }

    private AbstractComposeFragment getComposeFragment(Bundle savedInstanceState) {
        Bundle args = processIntent(savedInstanceState);
        if (args != null) {
            AbstractComposeFragment f = null;
            Uri threadUri = args.getParcelable("data");
            String action = args.getString("action");
            if (ACTION_VIEW_CONVERSATION.equals(action)) {
                long threadId = ContentUris.parseId(threadUri);
                Conversation conv = Conversation.loadFromId(this, threadId);
                if (conv != null) {
                    f = conv.isGroupChat() ?
                        new GroupMessageFragment() :
                        new ComposeMessageFragment();
                }
            }
            else if (ACTION_VIEW_USERID.equals(action)) {
                String userId =  threadUri.getLastPathSegment();
                Conversation conv = Conversation.loadFromUserId(this, userId);
                f = conv != null && conv.isGroupChat() ?
                    new GroupMessageFragment() :
                    new ComposeMessageFragment();
            }
            else {
                // default to a single user chat
                f = new ComposeMessageFragment();
            }

            if (f != null)
                f.setArguments(args);
            return f;
        }

        return null;
    }

    private Bundle processIntent(Bundle savedInstanceState) {
        Intent intent;
        if (savedInstanceState != null) {
            mLostFocus = savedInstanceState.getBoolean("lostFocus");

            Uri uri = savedInstanceState.getParcelable(Uri.class.getName());
            if (uri == null) {
                Log.d(TAG, "restoring non-loaded conversation, aborting");
                finish();
                return null;
            }
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
                Uri uri = intent.getData();

                // two-panes UI: start conversation list
                if (Kontalk.hasTwoPanesUI(this)) {
                    Intent startIntent = new Intent(action, uri,
                        getApplicationContext(), ConversationsActivity.class);
                    startActivity(startIntent);
                    // no need to go further
                    finish();
                    return null;
                }
                // single-pane UI: start normally
                else {
                    args = new Bundle();
                    args.putString("action", action);
                    args.putParcelable("data", uri);
                    args.putLong(EXTRA_MESSAGE, intent.getLongExtra(EXTRA_MESSAGE, -1));
                    args.putString(EXTRA_HIGHLIGHT, intent.getStringExtra(EXTRA_HIGHLIGHT));
                    args.putBoolean(EXTRA_CREATING_GROUP, intent.getBooleanExtra(EXTRA_CREATING_GROUP, false));
                }
            }

            // send external content
            else if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                String mime = intent.getType();

                Log.i(TAG, "sending data to someone: " + mime);

                String userId = intent.getStringExtra(EXTRA_USERID);
                if (userId != null) {
                    handleSendResult(userId, intent);
                    // we'll handle directSendIntent later
                    return null;
                }
                else {
                    mSendIntent = intent;
                    chooseContact();
                    // onActivityResult will handle the rest
                    return null;
                }
            }

            // send to someone
            else if (Intent.ACTION_SENDTO.equals(action)) {
                try {
                    Uri uri = intent.getData();
                    // a phone number should come here...
                    String number = RegistrationService.fixNumber(this,
                            uri.getSchemeSpecificPart(),
                            Kontalk.get().getDefaultAccount().getPhoneNumber(), 0);
                    // compute hash and open conversation
                    String jid = XMPPUtils.createLocalJID(XMPPUtils.createLocalpart(number));

                    // two-panes UI: start conversation list
                    if (Kontalk.hasTwoPanesUI(this)) {
                        Intent startIntent = new Intent(getApplicationContext(), ConversationsActivity.class);
                        startIntent.setAction(ACTION_VIEW_USERID);
                        startIntent.setData(Threads.getUri(jid));
                        startActivity(startIntent);
                        // no need to go further
                        finish();
                        return null;
                    }
                    // single-pane UI: start normally
                    else {
                        args = new Bundle();
                        args.putString("action", ComposeMessage.ACTION_VIEW_USERID);
                        args.putParcelable("data", Threads.getUri(jid));
                        args.putString("number", number);
                    }
                }
                catch (Exception e) {
                    Log.e(TAG, "invalid intent", e);
                    finish();
                }
            }

            return args;
        }

        return null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CONTACT_PICKER) {
            if (resultCode == RESULT_OK) {
                Uri threadUri = data.getData();
                if (threadUri != null) {
                    Log.i(TAG, "composing message for conversation: " + threadUri);
                    String userId = threadUri.getLastPathSegment();
                    handleSendResult(userId, null);
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

    private void handleSendResult(String userId, Intent directShareIntent) {
        Intent i = fromUserId(this, userId);
        if (i != null) {
            if (Kontalk.hasTwoPanesUI(this)) {
                // we need to go back to the main activity
                Intent startIntent = new Intent(getApplicationContext(), ConversationsActivity.class);
                startIntent.setAction(ACTION_VIEW_USERID);
                startIntent.setData(Threads.getUri(userId));
                startIntent.putExtra(ConversationsActivity.EXTRA_SEND_INTENT,
                    directShareIntent != null ? directShareIntent : mSendIntent);
                startActivity(startIntent);
                finish();
            }
            else {
                onNewIntent(i);

                // process SEND intent if necessary
                if (mSendIntent != null)
                    processSendIntent();
                // otherwise save direct share intent for later
                else if (directShareIntent != null)
                    mDirectSendIntent = directShareIntent;
            }
        }
        else {
            Toast.makeText(this, R.string.contact_not_registered, Toast.LENGTH_LONG)
                .show();
            finish();
        }
    }

    public static Intent fromUserId(Context context, String userId) {
        return fromUserId(context, userId, false);
    }

    public static Intent fromUserId(Context context, String userId, boolean creatingGroup) {
        Conversation conv = Conversation.loadFromUserId(context, userId);
        // not found - create new
        if (conv == null) {
            Intent ni = new Intent(context, ComposeMessage.class);
            ni.setAction(ComposeMessage.ACTION_VIEW_USERID);
            ni.setData(Threads.getUri(userId));
            return ni;
        }

        return fromConversation(context, conv, creatingGroup);
    }

    public static Intent fromThreadUri(Context context, Uri threadUri) {
        String userId = threadUri.getLastPathSegment();
        Conversation conv = Conversation.loadFromUserId(context, userId);
        // not found - create new
        if (conv == null) {
            Intent ni = new Intent(context, ComposeMessage.class);
            ni.setAction(ComposeMessage.ACTION_VIEW_USERID);
            ni.setData(threadUri);
            return ni;
        }

        return fromConversation(context, conv);
    }

    /** Creates an {@link Intent} for launching the composer for a given {@link Conversation}. */
    public static Intent fromConversation(Context context, Conversation conv) {
        return fromConversation(context, conv, false);
    }

    /** Creates an {@link Intent} for launching the composer for a given {@link Conversation}. */
    public static Intent fromConversation(Context context, Conversation conv, boolean creatingGroup) {
        return fromConversation(context, conv.getThreadId(), creatingGroup);
    }

    /** Creates an {@link Intent} for launching the composer for a given thread Id. */
    public static Intent fromConversation(Context context, long threadId) {
        return fromConversation(context, threadId, false);
    }

    /** Creates an {@link Intent} for launching the composer for a given thread Id. */
    public static Intent fromConversation(Context context, long threadId, boolean creatingGroup) {
        Intent i = new Intent(ComposeMessage.ACTION_VIEW_CONVERSATION,
                ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId),
                context, ComposeMessage.class);
        i.putExtra(EXTRA_CREATING_GROUP, creatingGroup);
        return i;
    }

    /** Creates an {@link Intent} for sending a text message. */
    public static Intent sendTextMessage(String text) {
        Intent i = SystemUtils.externalIntent(Intent.ACTION_SEND);
        i.setType(TextComponent.MIME_TYPE);
        i.putExtra(Intent.EXTRA_TEXT, text);
        return i;
    }

    public static Intent sendMediaMessage(Context context, Uri uri, String mime) {
        Intent i = SystemUtils.externalIntent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, MediaStorage
            .getWorldReadableUri(context, uri, i));
        return i;
    }

    private void chooseContact() {
        // TODO one day it will be like this
        // Intent i = new Intent(Intent.ACTION_PICK, Users.CONTENT_URI);
        Intent i = new Intent(this, ContactsListActivity.class);
        i.putExtra(ContactsListActivity.MODE_RECENTS, true);
        startActivityForResult(i, REQUEST_CONTACT_PICKER);
    }

    private void processSendIntent() {
        SendIntentReceiver.processSendIntent(this, mSendIntent, mFragment);
        mSendIntent = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadConversation();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (mFragment != null)
            out.putParcelable(Uri.class.getName(), Threads.getUri(mFragment.getUserId()));
        out.putBoolean("lostFocus", mLostFocus);
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (mDirectSendIntent != null) {
            SendIntentReceiver.processSendIntent(this, mDirectSendIntent, mFragment);
            mDirectSendIntent = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mResumed = false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus && mResumed) {
            if (mLostFocus) {
                mFragment.onFocus();
                mLostFocus = false;
            }
        }
    }

    public void fragmentLostFocus() {
        mLostFocus = true;
    }

    public boolean hasLostFocus() {
        return mLostFocus;
    }

    public Intent getSendIntent() {
        return mSendIntent;
    }
}
