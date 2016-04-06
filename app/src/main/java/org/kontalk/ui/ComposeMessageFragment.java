/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

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
import java.util.Set;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.nispok.snackbar.SnackbarManager;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jxmpp.util.XmppStringUtils;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PGP;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.GroupCommandComponent;
import org.kontalk.provider.MyMessages;
import org.kontalk.provider.MyMessages.CommonColumns;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Requests;
import org.kontalk.provider.UsersProvider;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.sync.Syncer;
import org.kontalk.util.MessageUtils;
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
public class ComposeMessageFragment extends AbstractComposeFragment {
    private static final String TAG = ComposeMessage.TAG;

    private ViewGroup mInvitationBar;
    private MenuItem mViewContactMenu;
    private MenuItem mCallMenu;
    private MenuItem mBlockMenu;
    private MenuItem mUnblockMenu;

    /** The user we are talking to. */
    private String mUserJID;
    private String mUserPhone;

    /** Available resources. */
    private Set<String> mAvailableResources = new HashSet<>();
    private String mLastActivityRequestId;
    private String mVersionRequestId;

    private BroadcastReceiver mPresenceReceiver;
    private BroadcastReceiver mPrivacyListener;

    private boolean mIsTyping;

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        // TODO inflater.inflate(R.menu.compose_message_menu, menu);
        mViewContactMenu = menu.findItem(R.id.view_contact);
        mCallMenu = menu.findItem(R.id.call_contact);
        mBlockMenu = menu.findItem(R.id.block_user);
        mUnblockMenu = menu.findItem(R.id.unblock_user);
        updateUI();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (super.onOptionsItemSelected(item))
            return true;

        switch (item.getItemId()) {
            case R.id.call_contact:
                startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:"
                    + mUserPhone)));
                return true;

            case R.id.view_contact:
                viewContact();
                return true;

            case R.id.menu_attachment:
                toggleAttachmentView();
                return true;

            case R.id.block_user:
                blockUser();
                return true;

            case R.id.unblock_user:
                unblockUser();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void viewContact() {
        if (mConversation != null) {
            Contact contact = mConversation.getContact();
            if (contact != null) {
                Uri uri = contact.getUri();
                if (uri != null) {
                    Intent i = new Intent(Intent.ACTION_VIEW, uri);
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
                public void onClick(@NonNull MaterialDialog materialDialog, @NonNull DialogAction dialogAction) {
                    setPrivacy(PRIVACY_BLOCK);
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
                public void onClick(@NonNull MaterialDialog materialDialog, @NonNull DialogAction dialogAction) {
                    setPrivacy(PRIVACY_UNBLOCK);
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
                mUserName = contact.getName();
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
            mUserJID = XMPPUtils.createLocalJID(getActivity(), MessageUtils.sha1(mUserPhone));

            Cursor cp = cres.query(MyMessages.Messages.CONTENT_URI,
                new String[] { MyMessages.Messages.THREAD_ID }, MyMessages.Messages.PEER
                    + " = ?", new String[] { mUserJID }, null);
            if (cp.moveToFirst())
                threadId = cp.getLong(0);
            cp.close();
        }
        c.close();

        if (threadId > 0) {
            mConversation = Conversation.loadFromId(getActivity(),
                threadId);
            setThreadId(threadId);
        }
        else {
            mConversation = Conversation.createNew(getActivity());
            mConversation.setRecipient(mUserJID);
        }
    }

    @Override
    protected void handleActionViewConversation(Uri uri, Bundle args) {
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
            mUserName = contact.getName();
            mUserPhone = contact.getNumber();
        }
        else {
            mUserName = mUserJID;
            mUserPhone = null;
        }
    }

    @Override
    protected void onArgumentsProcessed() {
        // non existant thread - check for not synced contact
        if (getThreadId() <= 0 && mConversation != null) {
            Contact contact = mConversation.getContact();
            if (!(mUserPhone != null && contact != null) || !contact.isRegistered()) {
                // ask user to send invitation
                DialogInterface.OnClickListener noListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // FIXME is this specific to sms app?
                        Intent i = new Intent(Intent.ACTION_SENDTO,
                            Uri.parse("smsto:" + mUserPhone));
                        i.putExtra("sms_body",
                            getString(R.string.text_invite_message));
                        startActivity(i);
                        getActivity().finish();
                    }
                };

                AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
                builder.
                    setTitle(R.string.title_user_not_found)
                    .setMessage(R.string.message_user_not_found)
                    // nothing happens if user chooses to contact the user anyway
                    .setPositiveButton(R.string.yes_user_not_found, null)
                    .setNegativeButton(R.string.no_user_not_found, noListener)
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

    /** Called when the {@link Conversation} object has been created. */
    @Override
    protected void onConversationCreated() {
        // subscribe to presence notifications
        subscribePresence();

        super.onConversationCreated();

        // setup invitation bar
        boolean visible = (mConversation.getRequestStatus() == Threads.REQUEST_WAITING);

        if (visible) {

            if (mInvitationBar == null) {
                mInvitationBar = (ViewGroup) getView().findViewById(R.id.invitation_bar);

                // setup listeners and show button bar
                View.OnClickListener listener = new View.OnClickListener() {
                    public void onClick(View v) {
                        mInvitationBar.setVisibility(View.GONE);

                        int action;
                        if (v.getId() == R.id.button_accept)
                            action = PRIVACY_ACCEPT;
                        else
                            action = PRIVACY_REJECT;

                        setPrivacy(action);
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

    private void setPrivacy(int action) {
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

        Context ctx = getActivity();

        // mark request as pending accepted
        ContentValues values = new ContentValues(1);
        values.put(Threads.REQUEST_STATUS, status);

        // FIXME this won't work on new threads

        ctx.getContentResolver().update(Requests.CONTENT_URI,
            values, CommonColumns.PEER + "=?",
                new String[] { mUserJID });

        // accept invitation
        if (action == PRIVACY_ACCEPT) {
            // trust the key
            UsersProvider.trustUserKey(ctx, mUserJID);
            // reload contact
            invalidateContact();
        }
        // setup broadcast receiver for block/unblock reply
        else if (action == PRIVACY_REJECT || action == PRIVACY_BLOCK || action == PRIVACY_UNBLOCK) {
            if (mPrivacyListener == null) {
                mPrivacyListener = new BroadcastReceiver() {
                    public void onReceive(Context context, Intent intent) {
                        String from = XmppStringUtils.parseBareJid(intent
                            .getStringExtra(MessageCenterService.EXTRA_FROM));

                        if (mUserJID.equals(from)) {
                            // reload contact
                            reloadContact();
                            // this will update block/unblock menu items
                            updateUI();
                            // request presence subscription if unblocking
                            if (MessageCenterService.ACTION_UNBLOCKED.equals(intent.getAction())) {
                                Toast.makeText(getActivity(),
                                        R.string.msg_user_unblocked,
                                        Toast.LENGTH_LONG).show();

                                // hide any block warning
                                // a new warning will be issued for the key if needed
                                hideWarning();
                                presenceSubscribe();
                            }
                            else {
                                Toast.makeText(getActivity(),
                                    R.string.msg_user_blocked,
                                    Toast.LENGTH_LONG).show();
                            }

                            // we don't need this receiver anymore
                            mLocalBroadcastManager.unregisterReceiver(this);
                        }
                    }
                };
            }

            IntentFilter filter = new IntentFilter(MessageCenterService.ACTION_BLOCKED);
            filter.addAction(MessageCenterService.ACTION_UNBLOCKED);
            mLocalBroadcastManager.registerReceiver(mPrivacyListener, filter);
        }

        // send command to message center
        MessageCenterService.replySubscription(ctx, mUserJID, action);
    }

    private void invalidateContact() {
        Contact.invalidate(mUserJID);
        reloadContact();
    }

    private void reloadContact() {
        if (mConversation != null) {
            // this will trigger contact reload
            mConversation.setRecipient(mUserJID);
        }
    }

    private Uri storeCreateGroup(long threadId, String groupJid, String msgId, boolean encrypted) {
        // save to database
        ContentValues values = new ContentValues();
        values.put(MyMessages.Messages.THREAD_ID, threadId);
        values.put(MyMessages.Messages.MESSAGE_ID, msgId);
        values.put(MyMessages.Messages.PEER, groupJid);
        values.put(MyMessages.Messages.BODY_MIME, GroupCommandComponent.MIME_TYPE);
        values.put(MyMessages.Messages.BODY_CONTENT, GroupCommandComponent.COMMAND_CREATE.getBytes());
        values.put(MyMessages.Messages.BODY_LENGTH, 0);
        values.put(MyMessages.Messages.UNREAD, false);
        values.put(MyMessages.Messages.DIRECTION, MyMessages.Messages.DIRECTION_OUT);
        values.put(MyMessages.Messages.TIMESTAMP, System.currentTimeMillis());
        values.put(MyMessages.Messages.STATUS, MyMessages.Messages.STATUS_SENDING);
        // of course outgoing messages are not encrypted in database
        values.put(MyMessages.Messages.ENCRYPTED, false);
        values.put(MyMessages.Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        return getActivity().getContentResolver().insert(MyMessages.Messages.CONTENT_URI, values);
    }

    @Override
    protected void addUsers(String[] members) {
        String groupId = StringUtils.randomString(20);
        String groupJid = XmppStringUtils.completeJidFrom(groupId,
            Authenticator.getSelfJID(getContext()));

        // add this user and the others requested
        // duplicates will be ignored by the provider
        String[] users = new String[members.length + 1];
        users[0] = getUserId();
        System.arraycopy(members, 0, users, 1, members.length);

        long groupThreadId = Conversation.initGroupChat(getActivity(),
            groupJid, mConversation.getGroupSubject(), users,
            mComposer.getText().toString());

        // store create group command to outbox
        boolean encrypted = Preferences.getEncryptionEnabled(getContext());
        String msgId = MessageCenterService.messageId();
        Uri cmdMsg = storeCreateGroup(groupThreadId, groupJid, msgId, encrypted);
        // TODO check for null

        // send create group command now
        MessageCenterService.createGroup(getContext(), groupJid,
            mConversation.getGroupSubject(), users, encrypted,
            ContentUris.parseId(cmdMsg), msgId);

        // load the new conversation
        ((ComposeMessageParent) getActivity()).loadConversation(groupThreadId);
    }

    private void showIdentityDialog(boolean informationOnly, int titleId) {
        String fingerprint;
        String uid;

        PGPPublicKeyRing publicKey = UsersProvider.getPublicKey(getActivity(), mUserJID, false);
        if (publicKey != null) {
            PGPPublicKey pk = PGP.getMasterKey(publicKey);
            fingerprint = PGP.formatFingerprint(PGP.getFingerprint(pk));
            uid = PGP.getUserId(pk, null);    // TODO server!!!
        }
        else {
            // FIXME using another string
            fingerprint = uid = getString(R.string.peer_unknown);
        }

        SpannableStringBuilder text = new SpannableStringBuilder();
        text.append(getString(R.string.text_invitation1))
            .append('\n');

        Contact c = mConversation.getContact();
        if (c != null) {
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

        AlertDialogWrapper.Builder builder = new AlertDialogWrapper
            .Builder(getActivity())
            .setMessage(text);

        if (informationOnly) {
            builder.setTitle(titleId);
        }
        else {
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // hide warning bar
                    hideWarning();

                    switch (which) {
                        case DialogInterface.BUTTON_POSITIVE:
                            // trust new key
                            trustKeyChange();
                            break;
                        case DialogInterface.BUTTON_NEGATIVE:
                            // block user immediately
                            setPrivacy(PRIVACY_BLOCK);
                            break;
                    }
                }
            };
            builder.setTitle(titleId)
                .setPositiveButton(R.string.button_accept, listener)
                .setNegativeButton(R.string.button_block, listener);
        }

        builder.show();
    }

    private void hideWarning() {
        SnackbarManager.dismiss();
    }

    private void trustKeyChange() {
        // mark current key as trusted
        UsersProvider.trustUserKey(getActivity(), mUserJID);
        // reload contact
        invalidateContact();
    }

    private void showKeyWarning(int textId, final int dialogTitleId, final int dialogMessageId) {
        Activity context = getActivity();
        if (context != null) {
            showWarning(context.getText(textId), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which) {
                                case DialogInterface.BUTTON_POSITIVE:
                                    // hide warning bar
                                    hideWarning();
                                    // trust new key
                                    trustKeyChange();
                                    break;
                                case DialogInterface.BUTTON_NEUTRAL:
                                    showIdentityDialog(false, dialogTitleId);
                                    break;
                                case DialogInterface.BUTTON_NEGATIVE:
                                    // hide warning bar
                                    hideWarning();
                                    // block user immediately
                                    setPrivacy(PRIVACY_BLOCK);
                                    break;
                            }
                        }
                    };
                    new AlertDialogWrapper.Builder(getActivity())
                        .setTitle(dialogTitleId)
                        .setMessage(dialogMessageId)
                        .setPositiveButton(R.string.button_accept, listener)
                        .setNeutralButton(R.string.button_identity, listener)
                        .setNegativeButton(R.string.button_block, listener)
                        .show();
                }
            }, WarningType.FATAL);
        }
    }

    private void showKeyUnknownWarning() {
        showKeyWarning(R.string.warning_public_key_unknown,
            R.string.title_public_key_unknown_warning, R.string.msg_public_key_unknown_warning);
    }

    private void showKeyChangedWarning() {
        showKeyWarning(R.string.warning_public_key_changed,
            R.string.title_public_key_changed_warning, R.string.msg_public_key_changed_warning);
    }

    private void subscribePresence() {
        // TODO this needs serious refactoring
        if (mPresenceReceiver == null) {
            mPresenceReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();

                    if (MessageCenterService.ACTION_PRESENCE.equals(action)) {
                        String from = intent.getStringExtra(MessageCenterService.EXTRA_FROM);
                        String bareFrom = from != null ? XmppStringUtils.parseBareJid(from) : null;

                        // we are receiving a presence from our peer
                        if (from != null && bareFrom.equalsIgnoreCase(mUserJID)) {

                            // we handle only (un)available presence stanzas
                            String type = intent.getStringExtra(MessageCenterService.EXTRA_TYPE);

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
                            else if (Presence.Type.available.name().equals(type) || Presence.Type.unavailable.name().equals(type)) {

                                CharSequence statusText = null;

                                // really not much sense in requesting the key for a non-existing contact
                                Contact contact = getContact();
                                if (contact != null) {
                                    String newFingerprint = intent.getStringExtra(MessageCenterService.EXTRA_FINGERPRINT);
                                    // if this is null, we are accepting the key for the first time
                                    PGPPublicKeyRing trustedPublicKey = contact.getTrustedPublicKeyRing();

                                    // request the key if we don't have a trusted one and of course if the user has a key
                                    boolean unknownKey = (trustedPublicKey == null && contact.getFingerprint() != null);
                                    boolean changedKey = false;
                                    // check if fingerprint changed
                                    if (trustedPublicKey != null && newFingerprint != null) {
                                        String oldFingerprint = PGP.getFingerprint(PGP.getMasterKey(trustedPublicKey));
                                        if (!newFingerprint.equalsIgnoreCase(oldFingerprint)) {
                                            // fingerprint has changed since last time
                                            changedKey = true;
                                        }
                                    }

                                    if (changedKey) {
                                        // warn user that public key is changed
                                        showKeyChangedWarning();
                                    }
                                    else if (unknownKey) {
                                        // warn user that public key is unknown
                                        showKeyUnknownWarning();
                                    }
                                }

                                if (Presence.Type.available.toString().equals(type)) {
                                    mAvailableResources.add(from);
                                    mIsTyping = mIsTyping || Contact.isTyping(from);
                                    if (mIsTyping) {
                                        setStatusText(context.getString(R.string.seen_typing_label));
                                    }

                                    /*
                                     * FIXME using mode this way has several flaws.
                                     * 1. it doesn't take multiple resources into account
                                     * 2. it doesn't account for away status duration (we don't have this information at all)
                                     */
                                    String mode = intent.getStringExtra(MessageCenterService.EXTRA_SHOW);
                                    if (mode != null && mode.equals(Presence.Mode.away.toString())) {
                                        statusText = context.getString(R.string.seen_away_label);
                                    }
                                    else {
                                        statusText = context.getString(R.string.seen_online_label);
                                    }

                                    // request version information
                                    if (contact != null && contact.getVersion() != null) {
                                        setVersionInfo(context, contact.getVersion());
                                    }
                                    else if (mVersionRequestId == null) {
                                        requestVersion(from);
                                    }
                                }
                                else if (Presence.Type.unavailable.toString().equals(type)) {
                                    boolean removed = mAvailableResources.remove(from);
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
                                                MessageCenterService.requestLastActivity(context, bareFrom, mLastActivityRequestId);
                                            }
                                        }
                                    }
                                }

                                if (statusText != null) {
                                    setCurrentStatusText(statusText);
                                }
                            }

                            // subscription accepted, probe presence
                            else if (Presence.Type.subscribed.name().equals(type)) {
                                presenceSubscribe();
                            }
                        }
                    }

                    else if (MessageCenterService.ACTION_LAST_ACTIVITY.equals(action)) {
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
                                    Contact contact = getContact();
                                    if (contact != null)
                                        // cache the version
                                        contact.setVersion(version);
                                    setVersionInfo(context, version);
                                }
                            }
                        }
                    }

                    else if (MessageCenterService.ACTION_CONNECTED.equals(action)) {
                        // reset compose sent flag
                        mComposer.resetCompose();
                        // reset available resources list
                        mAvailableResources.clear();
                        // reset any pending request
                        mLastActivityRequestId = null;
                        mVersionRequestId = null;
                    }

                    else if (MessageCenterService.ACTION_ROSTER_LOADED.equals(action)) {
                        // probe presence
                        presenceSubscribe();
                    }

                    else if (MessageCenterService.ACTION_MESSAGE.equals(action)) {
                        String from = intent.getStringExtra(MessageCenterService.EXTRA_FROM);
                        String chatState = intent.getStringExtra("org.kontalk.message.chatState");

                        // we are receiving a composing notification from our peer
                        if (from != null && XMPPUtils.equalsBareJID(from, mUserJID)) {
                            if (chatState != null && ChatState.composing.toString().equals(chatState)) {
                                mIsTyping = true;
                                setStatusText(context.getString(R.string.seen_typing_label));
                            }
                            else {
                                mIsTyping = false;
                                setStatusText(mCurrentStatus != null ? mCurrentStatus : "");
                            }
                        }
                    }

                }
            };

            // listen for user presence, connection and incoming messages
            IntentFilter filter = new IntentFilter();
            filter.addAction(MessageCenterService.ACTION_PRESENCE);
            filter.addAction(MessageCenterService.ACTION_CONNECTED);
            filter.addAction(MessageCenterService.ACTION_ROSTER_LOADED);
            filter.addAction(MessageCenterService.ACTION_LAST_ACTIVITY);
            filter.addAction(MessageCenterService.ACTION_MESSAGE);
            filter.addAction(MessageCenterService.ACTION_VERSION);

            mLocalBroadcastManager.registerReceiver(mPresenceReceiver, filter);

            // request connection and roster load status
            Context ctx = getActivity();
            if (ctx != null) {
                MessageCenterService.requestConnectionStatus(ctx);
                MessageCenterService.requestRosterStatus(ctx);
            }
        }
    }

    private void setVersionInfo(Context context, String version) {
        if (SystemUtils.isOlderVersion(context, version)) {
            showWarning(context.getText(R.string.warning_older_version), null, WarningType.WARNING);
        }
    }

    private void setLastSeenTimestamp(Context context, long stamp) {
        setCurrentStatusText(MessageUtils.formatRelativeTimeSpan(context, stamp));
    }

    private void setLastSeenSeconds(Context context, long seconds) {
        CharSequence statusText = null;
        if (seconds == 0) {
            // it's improbable, but whatever...
            statusText = context.getText(R.string.seen_moment_ago_label);
        }
        else if (seconds > 0) {
            long stamp = System.currentTimeMillis() - (seconds * 1000);

            Contact contact = getContact();
            if (contact != null) {
                contact.setLastSeen(stamp);
            }

            // seconds ago relative to our time
            statusText = MessageUtils.formatRelativeTimeSpan(context, stamp);
        }

        if (statusText != null) {
            setCurrentStatusText(statusText);
        }
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

    /** Sends a subscription request for the current peer. */
    private void presenceSubscribe() {
        Context context = getActivity();
        if (context != null) {
            // all of this shall be done only if there isn't a request from the other contact
            if (mConversation.getRequestStatus() != Threads.REQUEST_WAITING) {
                // request last presence
                Intent i = new Intent(context, MessageCenterService.class);
                i.setAction(MessageCenterService.ACTION_PRESENCE);
                i.putExtra(MessageCenterService.EXTRA_TO, mUserJID);
                i.putExtra(MessageCenterService.EXTRA_TYPE, Presence.Type.probe.name());
                context.startService(i);
            }
        }
    }

    private void unsubcribePresence() {
        if (mPresenceReceiver != null) {
            mLocalBroadcastManager.unregisterReceiver(mPresenceReceiver);
            mPresenceReceiver = null;
        }
    }

    public void onFocus(boolean resuming) {
        super.onFocus(resuming);

        if (mUserJID != null) {
            // clear chat invitation (if any)
            // TODO use jid here
            MessagingNotification.clearChatInvitation(getActivity(), mUserJID);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // unsubcribe presence notifications
        unsubcribePresence();
    }

    protected void updateUI() {
        super.updateUI();

        Contact contact = (mConversation != null) ? mConversation
                .getContact() : null;

        boolean contactEnabled = contact != null && contact.getId() > 0;

        if (mCallMenu != null) {
            Context context = getActivity();
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
            Context context = getActivity();
            if (context != null && Authenticator.isSelfJID(context, mUserJID)) {
                mBlockMenu.setVisible(false).setEnabled(false);
                mUnblockMenu.setVisible(false).setEnabled(false);
            }
            else if (contact != null) {
                // block/unblock
                boolean blocked = contact.isBlocked();
                if (blocked)
                    // show warning if blocked
                    showWarning(getText(R.string.warning_user_blocked), null, WarningType.WARNING);

                mBlockMenu.setVisible(!blocked).setEnabled(!blocked);
                mUnblockMenu.setVisible(blocked).setEnabled(blocked);
            }
            else {
                mBlockMenu.setVisible(true).setEnabled(true);
                mUnblockMenu.setVisible(true).setEnabled(true);
            }
        }
    }

    @Override
    protected void sendTextMessageInternal(String text, boolean encrypt, long msgId, String packetId) {
        MessageCenterService.sendTextMessage(getContext(), mUserJID, text, encrypt, msgId, packetId);
    }

    @Override
    protected void sendBinaryMessageInternal(String mime, Uri localUri, long length, String previewPath, boolean encrypt, int compress, long msgId, String packetId) {
        MessageCenterService.sendBinaryMessage(getContext(),
            mUserJID, mime, localUri, length, previewPath, encrypt, compress,
            msgId, packetId);
    }

    @Override
    public String getUserId() {
        return mUserJID;
    }

    @Override
    protected String getDecodedPeer(CompositeMessage msg) {
        return mUserPhone != null ? mUserPhone : mUserJID;
    }

}
