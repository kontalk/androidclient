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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jxmpp.util.XmppStringUtils;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDiskIOException;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import pub.devrel.easypermissions.EasyPermissions;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.crypto.PGP;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.message.CompositeMessage;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.KontalkGroupCommands;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.provider.MyMessages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyUsers;
import org.kontalk.provider.UsersProvider;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.sync.Syncer;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Permissions;
import org.kontalk.util.Preferences;
import org.kontalk.util.SystemUtils;
import org.kontalk.util.XMPPUtils;

import static org.kontalk.service.msgcenter.MessageCenterService.PRIVACY_ACCEPT;
import static org.kontalk.service.msgcenter.MessageCenterService.PRIVACY_BLOCK;
import static org.kontalk.service.msgcenter.MessageCenterService.PRIVACY_REJECT;
import static org.kontalk.service.msgcenter.MessageCenterService.PRIVACY_UNBLOCK;


/**
 * The composer fragment.
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public class ComposeMessageFragment extends AbstractComposeFragment
        implements EasyPermissions.PermissionCallbacks {
    private static final String TAG = ComposeMessage.TAG;

    ViewGroup mInvitationBar;
    private MenuItem mViewContactMenu;
    private MenuItem mCallMenu;
    private MenuItem mBlockMenu;
    private MenuItem mUnblockMenu;

    /** The user we are talking to. */
    String mUserJID;
    private String mUserPhone;

    String mLastActivityRequestId;
    String mVersionRequestId;
    String mKeyRequestId;

    private boolean mIsTyping;

    private BroadcastReceiver mBroadcastReceiver;

    @Override
    protected void onInflateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.compose_message_menu, menu);
        mViewContactMenu = menu.findItem(R.id.view_contact);
        mCallMenu = menu.findItem(R.id.call_contact);
        mBlockMenu = menu.findItem(R.id.block_user);
        mUnblockMenu = menu.findItem(R.id.unblock_user);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (super.onOptionsItemSelected(item))
            return true;

        switch (item.getItemId()) {
            case R.id.call_contact:
                callContact();
                return true;

            case R.id.view_contact:
                viewContact();
                return true;

            case R.id.block_user:
                blockUser();
                return true;

            case R.id.unblock_user:
                unblockUser();
                return true;
        }

        return false;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mLocalBroadcastManager != null && mBroadcastReceiver != null) {
            mLocalBroadcastManager.unregisterReceiver(mBroadcastReceiver);
        }
    }

    private void callContact() {
        final Context context = getContext();

        if (Permissions.canCallPhone(context)) {
            doCallContact();
        }
        else if (EasyPermissions.permissionPermanentlyDenied(this, Manifest.permission.CALL_PHONE)) {
            doDialContact();
        }
        else {
            Permissions.requestCallPhone(this);
        }
    }

    private void doCallContact() {
        SystemUtils.call(getContext(), mUserPhone);
    }

    private void doDialContact() {
        SystemUtils.dial(getContext(), mUserPhone);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        if (perms.contains(Manifest.permission.CALL_PHONE)) {
            doCallContact();
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        if (perms.contains(Manifest.permission.CALL_PHONE)) {
            doDialContact();
        }
    }

    public void viewContactInfo() {
        final Activity ctx = getActivity();
        if (ctx == null)
            return;

        if (mConversation != null) {
            Contact contact = mConversation.getContact();
            if (contact != null) {
                if (Kontalk.hasTwoPanesUI(ctx)) {
                    ContactInfoDialog.start(ctx, this, contact.getJID(), 0);
                }
                else {
                    ContactInfoActivity.start(ctx, this, contact.getJID(), 0);
                }
            }
        }
    }

    public void viewContact() {
        if (mConversation != null) {
            Contact contact = mConversation.getContact();
            if (contact != null) {
                Uri uri = contact.getUri();
                if (uri != null) {
                    Intent i = SystemUtils.externalIntent(Intent.ACTION_VIEW, uri);
                    if (i.resolveActivity(getActivity().getPackageManager()) != null) {
                        startActivity(i);
                    }
                    else {
                        // no contacts app found (crap device eh?)
                        Toast.makeText(getActivity(),
                            R.string.err_no_contacts_app,
                            Toast.LENGTH_LONG).show();
                    }
                }
                else {
                    // no contact found
                    Toast.makeText(getActivity(),
                        R.string.err_no_contact,
                        Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void blockUser() {
        new MaterialDialog.Builder(getActivity())
            .title(R.string.title_block_user_warning)
            .content(R.string.msg_block_user_warning)
            .positiveText(R.string.menu_block_user)
            .positiveColorRes(R.color.button_danger)
            .negativeText(android.R.string.cancel)
            .onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    setPrivacy(dialog.getContext(), PRIVACY_BLOCK);
                }
            })
            .show();
    }

    private void unblockUser() {
        new MaterialDialog.Builder(getActivity())
            .title(R.string.title_unblock_user_warning)
            .content(R.string.msg_unblock_user_warning)
            .positiveText(R.string.menu_unblock_user)
            .positiveColorRes(R.color.button_danger)
            .negativeText(android.R.string.cancel)
            .onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    setPrivacy(dialog.getContext(), PRIVACY_UNBLOCK);
                }
            })
            .show();
    }

    @Override
    protected void loadConversationMetadata(Uri uri) {
        super.loadConversationMetadata(uri);
        if (mConversation != null) {
            mUserJID = mConversation.getRecipient();
            Contact contact = mConversation.getContact();
            if (contact != null) {
                mUserName = contact.getDisplayName();
                mUserPhone = contact.getNumber();
            }
            else {
                mUserName = mUserJID;
            }
        }
    }

    @Override
    protected void handleActionView(Uri uri) {
        long threadId = 0;
        ContentResolver cres = getContext().getContentResolver();

        /*
         * FIXME this will retrieve name directly from contacts,
         * resulting in a possible discrepancy with users database
         */
        Cursor c = cres.query(uri, new String[] {
            Syncer.DATA_COLUMN_DISPLAY_NAME,
            Syncer.DATA_COLUMN_PHONE }, null, null, null);
        if (c.moveToFirst()) {
            mUserName = c.getString(0);
            mUserPhone = c.getString(1);

            // FIXME should it be retrieved from RawContacts.SYNC3 ??
            mUserJID = XMPPUtils.createLocalJID(getContext(),
                XMPPUtils.createLocalpart(mUserPhone));

            threadId = MessagesProviderClient.findThread(getContext(), mUserJID);
        }
        c.close();

        if (threadId > 0) {
            mConversation = Conversation.loadFromId(getActivity(),
                threadId);
            setThreadId(threadId);
        }
        else if (mUserJID == null) {
            Toast.makeText(getActivity(), R.string.err_no_contact,
                Toast.LENGTH_LONG).show();
            closeConversation();
        }
        else {
            mConversation = Conversation.createNew(getActivity());
            mConversation.setRecipient(mUserJID);
        }
    }

    @Override
    protected boolean handleActionViewConversation(Uri uri, Bundle args) {
        mUserJID = uri.getPathSegments().get(1);
        mConversation = Conversation.loadFromUserId(getActivity(),
            mUserJID);

        if (mConversation == null) {
            mConversation = Conversation.createNew(getActivity());
            mConversation.setNumberHint(args.getString("number"));
            mConversation.setRecipient(mUserJID);
        }
        // this way avoid doing the users database query twice
        else {
            if (mConversation.getContact() == null) {
                mConversation.setNumberHint(args.getString("number"));
                mConversation.setRecipient(mUserJID);
            }
        }

        setThreadId(mConversation.getThreadId());
        Contact contact = mConversation.getContact();
        if (contact != null) {
            mUserName = contact.getDisplayName();
            mUserPhone = contact.getNumber();
        }
        else {
            mUserName = mUserJID;
            mUserPhone = null;
        }

        return true;
    }

    @Override
    protected void onArgumentsProcessed() {
        // non existant thread - check for not synced contact
        if (getThreadId() <= 0 && mConversation != null && mUserJID != null) {
            Contact contact = mConversation.getContact();
            if ((contact == null || !contact.isRegistered()) && mUserPhone != null) {
                // ask user to send invitation
                new MaterialDialog.Builder(getActivity())
                    .title(R.string.title_user_not_found)
                    .content(R.string.message_user_not_found)
                    // nothing happens if user chooses to contact the user anyway
                    .positiveText(R.string.yes_user_not_found)
                    .negativeText(R.string.no_user_not_found)
                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            sendInvitation();
                        }
                    })
                    .show();
            }
        }
    }

    @Override
    public boolean sendTyping() {
        if (mAvailableResources.size() > 0) {
            MessageCenterService.sendChatState(getContext(), mUserJID, ChatState.composing);
            return true;
        }
        return false;
    }

    @Override
    public boolean sendInactive() {
        if (mAvailableResources.size() > 0) {
            MessageCenterService.sendChatState(getActivity(), mUserJID, ChatState.inactive);
            return true;
        }
        return false;
    }

    @Override
    protected void deleteConversation() {
        try {
            // delete all
            mConversation.delete(true);
        }
        catch (SQLiteDiskIOException e) {
            Log.w(TAG, "error deleting thread");
            Toast.makeText(getActivity(), R.string.error_delete_thread,
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected boolean isUserId(String jid) {
        return XMPPUtils.equalsBareJID(jid, mUserJID);
    }

    @Override
    protected void onConnected() {
        // reset any pending request
        mLastActivityRequestId = null;
        mVersionRequestId = null;
        mKeyRequestId = null;
    }

    @Override
    protected void onDisconnected() {
        onConnected();
    }

    @Override
    protected void onRosterLoaded() {
        // probe presence
        requestPresence();
    }

    @Override
    protected void onStartTyping(String jid, String groupJid) {
        mIsTyping = true;
        setStatusText(getString(R.string.seen_typing_label));
    }

    @Override
    protected void onStopTyping(String jid, String groupJid) {
        mIsTyping = false;
        setStatusText(mCurrentStatus != null ? mCurrentStatus : "");
    }

    @Override
    protected void onPresence(String jid, Presence.Type type, boolean removed, Presence.Mode mode, String fingerprint) {
        final Context context = getContext();
        if (context == null)
            return;

        if (type == null) {
            // no roster entry found, request subscription

            // pre-approve our presence if we don't have contact's key
            Intent i = new Intent(context, MessageCenterService.class);
            i.setAction(MessageCenterService.ACTION_PRESENCE);
            i.putExtra(MessageCenterService.EXTRA_TO, mUserJID);
            i.putExtra(MessageCenterService.EXTRA_TYPE, Presence.Type.subscribed.name());
            context.startService(i);

            // request subscription
            i = new Intent(context, MessageCenterService.class);
            i.setAction(MessageCenterService.ACTION_PRESENCE);
            i.putExtra(MessageCenterService.EXTRA_TO, mUserJID);
            i.putExtra(MessageCenterService.EXTRA_TYPE, Presence.Type.subscribe.name());
            context.startService(i);

            setStatusText(context.getString(R.string.invitation_sent_label));
        }

        // (un)available presence
        else if (type == Presence.Type.available || type == Presence.Type.unavailable) {

            CharSequence statusText = null;
            // hide any present warning
            hideWarning();

            // really not much sense in requesting the key for a non-existing contact
            Contact contact = getContact();
            if (contact != null) {
                // if this is null, we are accepting the key for the first time
                PGPPublicKeyRing trustedPublicKey = contact.getTrustedPublicKeyRing();

                // request the key if we don't have a trusted one and of course if the user has a key
                boolean unknownKey = (trustedPublicKey == null && contact.getFingerprint() != null);
                boolean changedKey = false;
                // check if fingerprint changed
                if (trustedPublicKey != null && fingerprint != null) {
                    String oldFingerprint = PGP.getFingerprint(PGP.getMasterKey(trustedPublicKey));
                    if (!fingerprint.equalsIgnoreCase(oldFingerprint)) {
                        // fingerprint has changed since last time
                        changedKey = true;
                    }
                }
                // user has no key or it couldn't be found
                // request it
                else if (trustedPublicKey == null && fingerprint == null) {
                    requestPublicKey(jid);
                }

                if (changedKey) {
                    // warn user that public key is changed
                    showKeyChangedWarning(fingerprint);
                }
                else if (unknownKey) {
                    // warn user that public key is unknown
                    showKeyUnknownWarning(fingerprint);
                }
            }

            if (type == Presence.Type.available) {
                mIsTyping = mIsTyping || Contact.isTyping(jid);
                if (mIsTyping) {
                    setStatusText(context.getString(R.string.seen_typing_label));
                }

                /*
                 * FIXME using mode this way has several flaws.
                 * 1. it doesn't take multiple resources into account
                 * 2. it doesn't account for away status duration (we don't have this information at all)
                 */
                boolean isAway = (mode == Presence.Mode.away);
                if (isAway) {
                    statusText = context.getString(R.string.seen_away_label);
                }
                else {
                    statusText = context.getString(R.string.seen_online_label);
                }

                String version = Contact.getVersion(jid);
                // do not request version info if already requested before
                if (!isAway && version == null && mVersionRequestId == null) {
                    requestVersion(jid);
                }

                // a new resource just connected, send typing information again
                // (only if we already sent it in this session)
                // FIXME this will always broadcast the message to all resources
                if (mComposer.isComposeSent() && mComposer.isSendEnabled() &&
                        Preferences.getSendTyping(context)) {
                    sendTyping();
                }
            }
            else if (type == Presence.Type.unavailable) {
                /*
                 * All available resources have gone. Mark
                 * the user as offline immediately and use the
                 * timestamp provided with the stanza (if any).
                 */
                if (mAvailableResources.size() == 0) {
                    // an offline user can't be typing
                    mIsTyping = false;

                    if (removed) {
                        // resource was removed now, mark as just offline
                        statusText = context.getText(R.string.seen_moment_ago_label);
                    }
                    else {
                        // resource is offline, request last activity
                        if (contact != null && contact.getLastSeen() > 0) {
                            setLastSeenTimestamp(context, contact.getLastSeen());
                        }
                        else if (mLastActivityRequestId == null) {
                            mLastActivityRequestId = StringUtils.randomString(6);
                            MessageCenterService.requestLastActivity(context,
                                XmppStringUtils.parseBareJid(jid), mLastActivityRequestId);
                        }
                    }
                }
            }

            if (statusText != null) {
                setCurrentStatusText(statusText);
            }
        }
    }

    /** Sends a subscription request for the current peer. */
    void requestPresence() {
        // do not request presence for domain JIDs
        if (!XMPPUtils.isDomainJID(mUserJID)) {
            Context context = getContext();
            if (context != null) {
                // all of this shall be done only if there isn't a request from the other contact
                // FIXME when accepting an invitation, the if below could be
                // false because of delay or no reloading of mConversation at all
                // thus skipping the presence request.
                // An automatic solution could be to emit a ROSTER_LOADED whenever the roster changes
                // still this _if_ should go away in favour of the message center checking for subscription status
                // and the UI reacting to a pending request status (i.e. "pending_in")
                if (mConversation.getRequestStatus() != Threads.REQUEST_WAITING) {
                    // request last presence
                    MessageCenterService.requestPresence(context, mUserJID);
                }
            }
        }
    }

    void sendInvitation() {
        // FIXME is this specific to sms app?
        Intent i = SystemUtils.externalIntent(Intent.ACTION_SENDTO,
            Uri.parse("smsto:" + mUserPhone));
        i.putExtra("sms_body",
            getString(R.string.text_invite_message));
        startActivity(i);
        getActivity().finish();
    }

    /** Called when the {@link Conversation} object has been created. */
    @Override
    protected void onConversationCreated() {
        super.onConversationCreated();

        // setup broadcast receiver
        if (mBroadcastReceiver == null) {
            mBroadcastReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String from = XmppStringUtils.parseBareJid(intent
                        .getStringExtra(MessageCenterService.EXTRA_FROM));
                    if (!mUserJID.equals(from)) {
                        // not for us
                        return;
                    }

                    String action = intent.getAction();

                    if (MessageCenterService.ACTION_LAST_ACTIVITY.equals(action)) {
                        String id = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
                        if (id != null && id.equals(mLastActivityRequestId)) {
                            mLastActivityRequestId = null;
                            // ignore last activity if we had an available presence in the meantime
                            if (mAvailableResources.size() == 0) {
                                String type = intent.getStringExtra(MessageCenterService.EXTRA_TYPE);
                                if (type == null || !type.equalsIgnoreCase(IQ.Type.error.toString())) {
                                    long seconds = intent.getLongExtra(MessageCenterService.EXTRA_SECONDS, -1);
                                    setLastSeenSeconds(context, seconds);
                                }
                                else {
                                    setCurrentStatusText(context.getString(R.string.seen_offline_label));
                                }
                            }
                        }
                    }

                    else if (MessageCenterService.ACTION_VERSION.equals(action)) {
                        // compare version and show warning if needed
                        String id = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
                        if (id != null && id.equals(mVersionRequestId)) {
                            mVersionRequestId = null;
                            String name = intent.getStringExtra(MessageCenterService.EXTRA_VERSION_NAME);
                            if (name != null && name.equalsIgnoreCase(context.getString(R.string.app_name))) {
                                String version = intent.getStringExtra(MessageCenterService.EXTRA_VERSION_NUMBER);
                                if (version != null) {
                                    // cache the version
                                    String fullFrom = intent.getStringExtra(MessageCenterService.EXTRA_FROM);
                                    Contact.setVersion(fullFrom, version);
                                    setVersionInfo(context, version);
                                }
                            }
                        }
                    }

                    else if (MessageCenterService.ACTION_PUBLICKEY.equals(action)) {
                        String id = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
                        if (id != null && id.equals(mKeyRequestId)) {
                            mKeyRequestId = null;
                            // reload contact
                            invalidateContact();
                            // request presence again
                            requestPresence();
                        }
                    }

                    else if (MessageCenterService.ACTION_BLOCKED.equals(intent.getAction())) {
                        // reload contact
                        reloadContact();
                        // this will update block/unblock menu items
                        updateUI();
                        Toast.makeText(context,
                            R.string.msg_user_blocked,
                            Toast.LENGTH_LONG).show();
                    }

                    else if (MessageCenterService.ACTION_UNBLOCKED.equals(intent.getAction())) {
                        // reload contact
                        reloadContact();
                        // this will update block/unblock menu items
                        updateUI();
                        // hide any block warning
                        // a new warning will be issued for the key if needed
                        hideWarning();
                        // request presence subscription when unblocking
                        requestPresence();
                        Toast.makeText(context,
                            R.string.msg_user_unblocked,
                            Toast.LENGTH_LONG).show();
                    }

                    else if (MessageCenterService.ACTION_SUBSCRIBED.equals(intent.getAction())) {
                        // reload contact
                        invalidateContact();
                        // request presence
                        requestPresence();
                    }
                }
            };

            // listen for some stuff we need
            IntentFilter filter = new IntentFilter();
            filter.addAction(MessageCenterService.ACTION_LAST_ACTIVITY);
            filter.addAction(MessageCenterService.ACTION_VERSION);
            filter.addAction(MessageCenterService.ACTION_PUBLICKEY);
            filter.addAction(MessageCenterService.ACTION_BLOCKED);
            filter.addAction(MessageCenterService.ACTION_UNBLOCKED);
            filter.addAction(MessageCenterService.ACTION_SUBSCRIBED);
            mLocalBroadcastManager.registerReceiver(mBroadcastReceiver, filter);
        }

        // setup invitation bar
        boolean visible = (mConversation.getRequestStatus() == Threads.REQUEST_WAITING);

        if (visible) {

            if (mInvitationBar == null) {
                mInvitationBar = getView().findViewById(R.id.invitation_bar);

                // setup listeners and show button bar
                View.OnClickListener listener = new View.OnClickListener() {
                    public void onClick(View v) {
                        mInvitationBar.setVisibility(View.GONE);

                        int action;
                        if (v.getId() == R.id.button_accept)
                            action = PRIVACY_ACCEPT;
                        else
                            action = PRIVACY_REJECT;

                        setPrivacy(v.getContext(), action);
                    }
                };

                mInvitationBar.findViewById(R.id.button_accept)
                    .setOnClickListener(listener);
                mInvitationBar.findViewById(R.id.button_block)
                    .setOnClickListener(listener);

                // identity button has its own listener
                mInvitationBar.findViewById(R.id.button_identity)
                    .setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            showIdentityDialog(true, R.string.title_invitation);
                        }
                    }
                );

            }
        }

        if (mInvitationBar != null)
            mInvitationBar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    void setPrivacy(@NonNull Context ctx, int action) {
        int status;

        switch (action) {
            case PRIVACY_ACCEPT:
                status = Threads.REQUEST_REPLY_PENDING_ACCEPT;
                break;

            case PRIVACY_BLOCK:
            case PRIVACY_REJECT:
                status = Threads.REQUEST_REPLY_PENDING_BLOCK;
                break;

            case PRIVACY_UNBLOCK:
                status = Threads.REQUEST_REPLY_PENDING_UNBLOCK;
                break;

            default:
                return;
        }

        // temporarly disable peer observer because the next call will write to the threads table
        unregisterPeerObserver();

        // mark request as pending accepted
        UsersProvider.setRequestStatus(ctx, mUserJID, status);

        // accept invitation
        if (action == PRIVACY_ACCEPT) {
            // trust the key
            Kontalk.get().getMessagesController()
                .setTrustLevelAndRetryMessages(ctx, mUserJID,
                    getContact().getFingerprint(), MyUsers.Keys.TRUST_VERIFIED);
        }

        // reload contact
        invalidateContact();
        // send command to message center
        MessageCenterService.replySubscription(ctx, mUserJID, action);
        // reload manually
        mConversation = Conversation.loadFromUserId(ctx, mUserJID);
        if (mConversation == null) {
            // threads was deleted (it was a request thread)
            threadId = 0;
        }
        processStart();
        if (threadId == 0) {
            // no thread means no peer observer will be invoked
            // we need to manually trigger this
            MessageCenterService.requestConnectionStatus(ctx);
            MessageCenterService.requestRosterStatus(ctx);
        }
    }

    void invalidateContact() {
        Contact.invalidate(mUserJID);
        reloadContact();
    }

    void reloadContact() {
        if (mConversation != null) {
            // this will trigger contact reload
            mConversation.setRecipient(mUserJID);
        }
    }

    @Override
    protected void addUsers(String[] members) {
        String selfJid = Authenticator.getSelfJID(getContext());
        String groupId = StringUtils.randomString(20);
        String groupJid = KontalkGroupCommands.createGroupJid(groupId, selfJid);

        // ensure no duplicates
        Set<String> usersList = new HashSet<>();
        String userId = getUserId();
        if (!userId.equalsIgnoreCase(selfJid))
            usersList.add(userId);
        for (String member : members) {
            // exclude ourselves
            if (!member.equalsIgnoreCase(selfJid))
                usersList.add(member);
        }

        if (usersList.size() > 0) {
            askGroupSubject(usersList, groupJid);
        }
    }

    private void askGroupSubject(final Set<String> usersList, final String groupJid) {
        new MaterialDialog.Builder(getContext())
            .title(R.string.title_group_subject)
            .positiveText(android.R.string.ok)
            .negativeText(android.R.string.cancel)
            .input(null, null, true, new MaterialDialog.InputCallback() {
                @Override
                public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                    String title = !TextUtils.isEmpty(input) ? input.toString() : null;

                    String[] users = usersList.toArray(new String[usersList.size()]);
                    long groupThreadId = Conversation.initGroupChat(getContext(),
                        groupJid, title, users,
                        mComposer.getText().toString());

                    // store create group command to outbox
                    // NOTE: group chats can currently only be created with chat encryption enabled
                    boolean encrypted = Preferences.getEncryptionEnabled(getContext());
                    String msgId = MessageCenterService.messageId();
                    Uri cmdMsg = KontalkGroupCommands.createGroup(getContext(),
                        groupThreadId, groupJid, users, msgId, encrypted);
                    // TODO check for null

                    // send create group command now
                    MessageCenterService.createGroup(getContext(), groupJid,
                        title, users, encrypted,
                        ContentUris.parseId(cmdMsg), msgId);

                    // open the new conversation
                    ((ComposeMessageParent) getActivity()).loadConversation(groupThreadId, true);
                }
            })
            .inputRange(0, MyMessages.Groups.GROUP_SUBJECT_MAX_LENGTH)
            .show();
    }

    void showIdentityDialog(boolean informationOnly, int titleId) {
        String fingerprint;
        String uid;

        PGPPublicKeyRing publicKey = Keyring.getPublicKey(getActivity(), mUserJID, MyUsers.Keys.TRUST_UNKNOWN);
        if (publicKey != null) {
            PGPPublicKey pk = PGP.getMasterKey(publicKey);
            fingerprint = PGP.formatFingerprint(PGP.getFingerprint(pk));
            uid = PGP.getUserId(pk, XmppStringUtils.parseDomain(mUserJID));
        }
        else {
            // FIXME using another string
            fingerprint = uid = getString(R.string.peer_unknown);
        }

        SpannableStringBuilder text = new SpannableStringBuilder();
        text.append(getString(R.string.text_invitation1))
            .append('\n');

        Contact c = mConversation.getContact();
        if (c != null && c.getName() != null && c.getNumber() != null) {
            text.append(c.getName())
                .append(" <")
                .append(c.getNumber())
                .append('>');
        }
        else {
            int start = text.length() - 1;
            text.append(uid);
            text.setSpan(MessageUtils.STYLE_BOLD, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        text.append('\n')
            .append(getString(R.string.text_invitation2))
            .append('\n');

        int start = text.length() - 1;
        text.append(fingerprint);
        text.setSpan(MessageUtils.STYLE_BOLD, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        MaterialDialog.Builder builder = new MaterialDialog
            .Builder(getActivity())
            .content(text);

        if (informationOnly) {
            builder.title(titleId);
        }
        else {
            builder.title(titleId)
                .positiveText(R.string.button_accept)
                .positiveColorRes(R.color.button_success)
                .negativeText(R.string.button_block)
                .negativeColorRes(R.color.button_danger)
                .onAny(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        // hide warning bar
                        hideWarning();

                        switch (which) {
                            case POSITIVE:
                                // trust new key
                                trustKeyChange(dialog.getContext(), null);
                                break;
                            case NEGATIVE:
                                // block user immediately
                                setPrivacy(dialog.getContext(), PRIVACY_BLOCK);
                                break;
                        }
                    }
                });
        }

        builder.show();
    }

    void trustKeyChange(@NonNull Context context, String fingerprint) {
        // mark current key as trusted
        if (fingerprint == null)
            fingerprint = getContact().getFingerprint();
        Kontalk.get().getMessagesController()
            .setTrustLevelAndRetryMessages(context, mUserJID, fingerprint, MyUsers.Keys.TRUST_VERIFIED);
        // reload contact
        invalidateContact();
    }

    private void showKeyWarning(int textId, final int dialogTitleId, final int dialogMessageId, final Object... data) {
        final Activity context = getActivity();
        if (context != null) {
            showWarning(context.getText(textId), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new MaterialDialog.Builder(context)
                        .title(dialogTitleId)
                        .content(dialogMessageId)
                        .positiveText(R.string.button_accept)
                        .positiveColorRes(R.color.button_success)
                        .neutralText(R.string.button_identity)
                        .negativeText(R.string.button_block)
                        .negativeColorRes(R.color.button_danger)
                        .onAny(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                switch (which) {
                                    case POSITIVE:
                                        // hide warning bar
                                        hideWarning();
                                        // trust new key
                                        trustKeyChange(dialog.getContext(), (String) data[0]);
                                        break;
                                    case NEUTRAL:
                                        showIdentityDialog(false, dialogTitleId);
                                        break;
                                    case NEGATIVE:
                                        // hide warning bar
                                        hideWarning();
                                        // block user immediately
                                        setPrivacy(dialog.getContext(), PRIVACY_BLOCK);
                                        break;
                                }
                            }
                        })
                        .show();
                }
            }, WarningType.FATAL);
        }
    }

    private void showKeyUnknownWarning(String fingerprint) {
        showKeyWarning(R.string.warning_public_key_unknown,
            R.string.title_public_key_unknown_warning, R.string.msg_public_key_unknown_warning,
            fingerprint);
    }

    private void showKeyChangedWarning(String newFingerprint) {
        showKeyWarning(R.string.warning_public_key_changed,
            R.string.title_public_key_changed_warning, R.string.msg_public_key_changed_warning,
            newFingerprint);
    }

    void setVersionInfo(Context context, String version) {
        if (SystemUtils.isOlderVersion(context, version)) {
            showWarning(context.getText(R.string.warning_older_version), null, WarningType.WARNING);
        }
    }

    private void setLastSeenTimestamp(Context context, long stamp) {
        setCurrentStatusText(MessageUtils.formatRelativeTimeSpan(context, stamp));
    }

    void setLastSeenSeconds(Context context, long seconds) {
        setCurrentStatusText(MessageUtils
            .formatLastSeen(context, getContact(), seconds));
    }

    private void setCurrentStatusText(CharSequence statusText) {
        mCurrentStatus = statusText;
        if (!mIsTyping)
            setStatusText(statusText);
    }

    private void requestVersion(String jid) {
        Context context = getActivity();
        if (context != null) {
            mVersionRequestId = StringUtils.randomString(6);
            MessageCenterService.requestVersionInfo(context, jid, mVersionRequestId);
        }
    }

    private void requestPublicKey(String jid) {
        Context context = getActivity();
        if (context != null) {
            mKeyRequestId = StringUtils.randomString(6);
            MessageCenterService.requestPublicKey(context, jid, mKeyRequestId);
        }
    }

    public void onFocus() {
        super.onFocus();

        if (mUserJID != null) {
            // clear chat invitation (if any)
            MessagingNotification.clearChatInvitation(getActivity(), mUserJID);
        }
    }

    protected void updateUI() {
        super.updateUI();

        Contact contact = (mConversation != null) ? mConversation
                .getContact() : null;

        boolean contactEnabled = contact != null && contact.getId() > 0;

        if (mCallMenu != null) {
            Context context = getContext();
            // FIXME what about VoIP?
            if (context != null && !context.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY)) {
                mCallMenu.setVisible(false).setEnabled(false);
            }
            else {
                mCallMenu.setVisible(true).setEnabled(true);
                mCallMenu.setEnabled(contactEnabled);
            }
            mViewContactMenu.setEnabled(contactEnabled);
        }

        if (mBlockMenu != null) {
            Context context = getContext();
            if (context != null) {
                if (Authenticator.isSelfJID(context, mUserJID)) {
                    mBlockMenu.setVisible(false).setEnabled(false);
                    mUnblockMenu.setVisible(false).setEnabled(false);
                }
                else if (contact != null) {
                    // block/unblock
                    boolean blocked = contact.isBlocked();
                    if (blocked)
                        // show warning if blocked
                        showWarning(context.getText(R.string.warning_user_blocked),
                            null, WarningType.WARNING);

                    mBlockMenu.setVisible(!blocked).setEnabled(!blocked);
                    mUnblockMenu.setVisible(blocked).setEnabled(blocked);
                }
                else {
                    mBlockMenu.setVisible(true).setEnabled(true);
                    mUnblockMenu.setVisible(true).setEnabled(true);
                }
            }
        }
    }

    @Override
    public String getUserId() {
        return mUserJID;
    }

    @Override
    protected String getDecodedPeer(CompositeMessage msg) {
        return mUserPhone != null ? mUserPhone : mUserJID;
    }

    @Override
    protected String getDecodedName(CompositeMessage msg) {
        Contact c = getContact();
        return (c != null) ? c.getName() : null;
    }

}
