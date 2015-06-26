/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jxmpp.util.XmppStringUtils;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.Contacts;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.text.ClipboardManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PGP;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.message.AttachmentComponent;
import org.kontalk.message.AudioComponent;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.ImageComponent;
import org.kontalk.message.MessageComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.message.VCardComponent;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.provider.MyMessages.CommonColumns;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Conversations;
import org.kontalk.provider.MyMessages.Threads.Requests;
import org.kontalk.provider.UsersProvider;
import org.kontalk.service.DownloadService;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.sync.Syncer;
import org.kontalk.ui.adapter.MessageListAdapter;
import org.kontalk.ui.view.AudioContentView;
import org.kontalk.ui.view.AudioContentViewControl;
import org.kontalk.ui.view.AudioPlayerControl;
import org.kontalk.ui.view.ComposerBar;
import org.kontalk.ui.view.ComposerListener;
import org.kontalk.ui.view.IconContextMenu;
import org.kontalk.ui.view.IconContextMenu.IconContextMenuOnClickListener;
import org.kontalk.ui.view.MessageListItem;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;
import org.kontalk.util.SystemUtils;
import org.kontalk.util.XMPPUtils;

import static android.content.res.Configuration.KEYBOARDHIDDEN_NO;
import static org.kontalk.service.msgcenter.MessageCenterService.PRIVACY_ACCEPT;
import static org.kontalk.service.msgcenter.MessageCenterService.PRIVACY_BLOCK;
import static org.kontalk.service.msgcenter.MessageCenterService.PRIVACY_REJECT;
import static org.kontalk.service.msgcenter.MessageCenterService.PRIVACY_UNBLOCK;


/**
 * The composer fragment.
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public class ComposeMessageFragment extends ListFragment implements
        ComposerListener, View.OnLongClickListener, IconContextMenuOnClickListener,
        // TODO these two interfaces should be handled by an inner class
        AudioDialog.AudioDialogListener, AudioPlayerControl {
    private static final String TAG = ComposeMessage.TAG;

    private static final int MESSAGE_LIST_QUERY_TOKEN = 8720;
    private static final int CONVERSATION_QUERY_TOKEN = 8721;

    private static final int SELECT_ATTACHMENT_OPENABLE = Activity.RESULT_FIRST_USER + 1;
    private static final int SELECT_ATTACHMENT_CONTACT = Activity.RESULT_FIRST_USER + 2;

    private enum WarningType {
        SUCCESS(0),    // not implemented
        INFO(1),       // not implemented
        WARNING(2),
        FATAL(3);

        private final int value;

        WarningType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /** Context menu group ID for this fragment. */
    private static final int CONTEXT_MENU_GROUP_ID = 2;

    /* Attachment chooser stuff. */
    private static final int CONTEXT_MENU_ATTACHMENT = 1;
    private static final int ATTACHMENT_ACTION_PICTURE = 1;
    private static final int ATTACHMENT_ACTION_CONTACT = 2;
    private static final int ATTACHMENT_ACTION_AUDIO = 3;
    private IconContextMenu attachmentMenu;

    private ComposerBar mComposer;

    private MessageListQueryHandler mQueryHandler;
    private MessageListAdapter mListAdapter;
    private TextView mStatusText;
    private ViewGroup mInvitationBar;
    private MenuItem mDeleteThreadMenu;
    private MenuItem mViewContactMenu;
    private MenuItem mCallMenu;
    private MenuItem mBlockMenu;
    private MenuItem mUnblockMenu;

    /** The thread id. */
    private long threadId = -1;
    private Conversation mConversation;
    private Bundle mArguments;

    /** The user we are talking to. */
    private String mUserJID;
    private String mUserName;
    private String mUserPhone;

    /** Available resources. */
    private Set<String> mAvailableResources = new HashSet<String>();
    private String mLastActivityRequestId;
    private String mVersionRequestId;

    /** MediaPlayer */
    private MediaPlayer mPlayer;
    private int mStatus = AudioContentView.STATUS_IDLE;
    private long mMediaPlayerMessageId;
    private Handler mHandler;
    private Runnable mMediaPlayerUpdater;
    private AudioContentViewControl mAudioControl;
    private AudioDialog mAudioDialog;

    private PeerObserver mPeerObserver;
    private File mCurrentPhoto;

    private LocalBroadcastManager mLocalBroadcastManager;
    private BroadcastReceiver mPresenceReceiver;
    private BroadcastReceiver mPrivacyListener;

    private boolean mOfflineModeWarned;
    private CharSequence mCurrentStatus;
    private boolean mIsTyping;

    /** Returns a new fragment instance from a picked contact. */
    public static ComposeMessageFragment fromUserId(Context context, String userId) {
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

    /** Returns a new fragment instance from a {@link Conversation} instance. */
    public static ComposeMessageFragment fromConversation(Context context,
            Conversation conv) {
        return fromConversation(context, conv.getThreadId());
    }

    /** Returns a new fragment instance from a thread ID. */
    public static ComposeMessageFragment fromConversation(Context context,
            long threadId) {
        ComposeMessageFragment f = new ComposeMessageFragment();
        Bundle args = new Bundle();
        args.putString("action", ComposeMessage.ACTION_VIEW_CONVERSATION);
        args.putParcelable("data",
                ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId));
        f.setArguments(args);
        return f;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // setListAdapter() is post-poned

        ListView list = getListView();
        list.setFastScrollEnabled(true);
        registerForContextMenu(list);

        mComposer = (ComposerBar) getView().findViewById(R.id.composer_bar);
        mComposer.setComposerListener(this);

        // footer (for tablet presence status)
        mStatusText = (TextView) getView().findViewById(R.id.status_text);

        // set custom background (if any)
        Drawable bg = Preferences.getConversationBackground(getActivity());
        if (bg != null) {
            ImageView background = (ImageView) getView().findViewById(R.id.background);
            list.setCacheColorHint(Color.TRANSPARENT);
            list.setBackgroundColor(Color.TRANSPARENT);
            background.setImageDrawable(bg);
        }

        mComposer.setRootView(getView().findViewById(R.id.root_view));

        Configuration config = getResources().getConfiguration();
        mComposer.onKeyboardStateChanged(config.keyboardHidden == KEYBOARDHIDDEN_NO);

        processArguments(savedInstanceState);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mLocalBroadcastManager = null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mComposer.onKeyboardStateChanged(newConfig.keyboardHidden == KEYBOARDHIDDEN_NO);
    }

    public void reload() {
        processArguments(null);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.compose_message, container, false);
    }

    private final MessageListAdapter.OnContentChangedListener mContentChangedListener = new MessageListAdapter.OnContentChangedListener() {
        public void onContentChanged(MessageListAdapter adapter) {
            if (isVisible())
                startQuery(true, false);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
        mQueryHandler = new MessageListQueryHandler();
        mHandler = new Handler();

        // list adapter creation is post-poned
    }

    /** Sends out a binary message. */
    @Override
    public void sendBinaryMessage(Uri uri, String mime, boolean media,
            Class<? extends MessageComponent<?>> klass) {
        Log.v(TAG, "sending binary content: " + uri);
        Uri newMsg = null;
        String msgId = null;
        File previewFile = null;
        long length = -1;

        boolean encrypted = Preferences.getEncryptionEnabled(getActivity());
        int compress = 0;
        if (klass == ImageComponent.class) {
            compress = Preferences.getImageCompression(getActivity());
        }

        try {
            // TODO convert to thread (?)

            offlineModeWarning();

            msgId = MessageCenterService.messageId();

            // generate thumbnail
            // FIXME this is blocking!!!!
            if (media && klass == ImageComponent.class) {
                // FIXME hard-coded to ImageComponent
                String filename = ImageComponent.buildMediaFilename(msgId, MediaStorage.THUMBNAIL_MIME_NETWORK);
                previewFile = MediaStorage.cacheThumbnail(getActivity(), uri,
                        filename, true);
            }

            length = MediaStorage.getLength(getActivity(), uri);

            // save to database
            ContentValues values = new ContentValues();
            values.put(Messages.MESSAGE_ID, msgId);
            values.put(Messages.PEER, mUserJID);

            /* TODO ask for a text to send with the image
            values.put(Messages.BODY_MIME, TextComponent.MIME_TYPE);
            values.put(Messages.BODY_CONTENT, content.getBytes());
            values.put(Messages.BODY_LENGTH, content.length());
             */

            values.put(Messages.UNREAD, false);
            // of course outgoing messages are not encrypted in database
            values.put(Messages.ENCRYPTED, false);
            values.put(Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
            values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
            values.put(Messages.TIMESTAMP, System.currentTimeMillis());
            values.put(Messages.STATUS, Messages.STATUS_SENDING);

            if (previewFile != null)
                values.put(Messages.ATTACHMENT_PREVIEW_PATH, previewFile.getAbsolutePath());

            values.put(Messages.ATTACHMENT_MIME, mime);
            values.put(Messages.ATTACHMENT_LOCAL_URI, uri.toString());
            values.put(Messages.ATTACHMENT_LENGTH, length);
            values.put(Messages.ATTACHMENT_COMPRESS, compress);

            newMsg = getActivity().getContentResolver().insert(
                    Messages.CONTENT_URI, values);
        }
        catch (Exception e) {
            Log.e(TAG, "unable to store media", e);
        }

        if (newMsg != null) {

            // update thread id from the inserted message
            if (threadId <= 0) {
                Cursor c = getActivity().getContentResolver().query(newMsg,
                        new String[] { Messages.THREAD_ID }, null, null, null);
                if (c.moveToFirst()) {
                    threadId = c.getLong(0);
                    mConversation = null;
                    startQuery(true, false);
                }
                else {
                    Log.v(TAG, "no data - cannot start query for this composer");
                }
                c.close();
            }

            // send message!
            String previewPath = (previewFile != null) ? previewFile.getAbsolutePath() : null;
            MessageCenterService.sendBinaryMessage(getActivity(),
                mUserJID, mime, uri, length, previewPath, encrypted, compress,
                ContentUris.parseId(newMsg), msgId);
        }
        else {
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(getActivity(),
                        R.string.err_store_message_failed,
                        Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private final class TextMessageThread extends Thread {
        private final String mText;

        TextMessageThread(String text) {
            mText = text;
        }

        @Override
        public void run() {
            try {
                boolean encrypted = Preferences.getEncryptionEnabled(getActivity());

                /* TODO maybe this hack could work...?
                MessageListItem v = (MessageListItem) LayoutInflater.from(getActivity())
                        .inflate(R.layout.message_list_item, getListView(), false);
                v.bind(getActivity(), msg, contact, null);
                getListView().addFooterView(v);
                */
                byte[] bytes = mText.getBytes();

                String msgId = MessageUtils.messageId();

                // save to local storage
                ContentValues values = new ContentValues();
                // must supply a message ID...
                values.put(Messages.MESSAGE_ID, msgId);
                values.put(Messages.PEER, mUserJID);
                values.put(Messages.BODY_MIME, TextComponent.MIME_TYPE);
                values.put(Messages.BODY_CONTENT, bytes);
                values.put(Messages.BODY_LENGTH, bytes.length);
                values.put(Messages.UNREAD, false);
                values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
                values.put(Messages.TIMESTAMP, System.currentTimeMillis());
                values.put(Messages.STATUS, Messages.STATUS_SENDING);
                // of course outgoing messages are not encrypted in database
                values.put(Messages.ENCRYPTED, false);
                values.put(Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
                Uri newMsg = getActivity().getContentResolver().insert(
                        Messages.CONTENT_URI, values);
                if (newMsg != null) {
                    // update thread id from the inserted message
                    if (threadId <= 0) {
                        Cursor c = getActivity().getContentResolver().query(newMsg,
                                new String[] { Messages.THREAD_ID }, null, null,
                                null);
                        if (c.moveToFirst()) {
                            threadId = c.getLong(0);
                            mConversation = null;
                            // we can run it here because progress=false
                            startQuery(true, false);
                        }
                        else {
                            Log.v(TAG, "no data - cannot start query for this composer");
                        }
                        c.close();
                    }

                    // send message!
                    MessageCenterService.sendTextMessage(getActivity(),
                        mUserJID, mText, encrypted,
                        ContentUris.parseId(newMsg), msgId);
                }
                else {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getActivity(), R.string.error_store_outbox,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
            catch (Exception e) {
                // whatever
                Log.d(TAG, "broken message thread", e);
            }
        }
    }

    /** Sends out the text message in the composing entry. */
    @Override
    public void sendTextMessage(String message) {
        if (!TextUtils.isEmpty(message)) {
            /*
             * TODO show an animation to warn the user that the message
             * is being sent (actually stored).
             */

            offlineModeWarning();

            // start thread
            new TextMessageThread(message).start();
        }
    }

    @Override
    public boolean sendTyping() {
        if (mAvailableResources.size() > 0) {
            MessageCenterService.sendChatState(getActivity(), mUserJID, ChatState.composing);
            return true;
        }
        return false;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.compose_message_menu, menu);

        mDeleteThreadMenu = menu.findItem(R.id.delete_thread);
        mViewContactMenu = menu.findItem(R.id.view_contact);
        mCallMenu = menu.findItem(R.id.call_contact);
        mBlockMenu = menu.findItem(R.id.block_user);
        mUnblockMenu = menu.findItem(R.id.unblock_user);
        updateUI();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.call_contact:
                startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:"
                        + mUserPhone)));
                return true;

            case R.id.view_contact:
                viewContact();
                return true;

            case R.id.menu_attachment:
                selectAttachment();
                return true;

            case R.id.delete_thread:
                if (threadId > 0)
                    deleteThread();

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

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        MessageListItem item = (MessageListItem) view;
        final CompositeMessage msg = item.getMessage();

        AttachmentComponent attachment = (AttachmentComponent) msg
                .getComponent(AttachmentComponent.class);

        if (attachment != null && (attachment.getFetchUrl() != null || attachment.getLocalUri() != null)) {

            // outgoing message or already fetched
            if (attachment.getLocalUri() != null) {
                // open file
                openFile(msg);
            }
            else {
                // info & download dialog
                CharSequence message = MessageUtils
                    .getFileInfoMessage(getActivity(), msg,
                        mUserPhone != null ? mUserPhone : mUserJID);

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.title_file_info)
                    .setMessage(message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setCancelable(true);

                if (!DownloadService.isQueued(attachment.getFetchUrl())) {
                    DialogInterface.OnClickListener startDL = new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // start file download
                            startDownload(msg);
                        }
                    };
                    builder.setPositiveButton(R.string.download, startDL);
                }
                else {
                    DialogInterface.OnClickListener stopDL = new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // cancel file download
                            stopDownload(msg);
                        }
                    };
                    builder.setPositiveButton(R.string.download_cancel, stopDL);
                }

                builder.show();
            }
        }

        else {
            item.onClick();
        }
    }

    private void startDownload(CompositeMessage msg) {
        AttachmentComponent attachment = (AttachmentComponent) msg
                .getComponent(AttachmentComponent.class);

        if (attachment != null && attachment.getFetchUrl() != null) {
            Intent i = new Intent(getActivity(), DownloadService.class);
            i.setAction(DownloadService.ACTION_DOWNLOAD_URL);
            i.putExtra(CompositeMessage.MSG_ID, msg.getDatabaseId());
            i.putExtra(CompositeMessage.MSG_SENDER, msg.getSender());
            i.putExtra(CompositeMessage.MSG_TIMESTAMP, msg.getTimestamp());
            i.putExtra(CompositeMessage.MSG_ENCRYPTED, attachment.getSecurityFlags() != Coder.SECURITY_CLEARTEXT);
            i.setData(Uri.parse(attachment.getFetchUrl()));
            getActivity().startService(i);
        }
        else {
            // corrupted message :(
            Toast.makeText(getActivity(), R.string.err_attachment_corrupted,
                Toast.LENGTH_LONG).show();
        }
    }

    private void stopDownload(CompositeMessage msg) {
        AttachmentComponent attachment = (AttachmentComponent) msg
                .getComponent(AttachmentComponent.class);

        if (attachment != null && attachment.getFetchUrl() != null) {
            Intent i = new Intent(getActivity(), DownloadService.class);
            i.setAction(DownloadService.ACTION_DOWNLOAD_ABORT);
            i.setData(Uri.parse(attachment.getFetchUrl()));
            getActivity().startService(i);
        }
    }

    private void openFile(CompositeMessage msg) {
        AttachmentComponent attachment = (AttachmentComponent) msg
                .getComponent(AttachmentComponent.class);

        if (attachment != null) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(attachment.getLocalUri(), attachment.getMime());
            startActivity(i);
        }
    }

    /** Listener for attachment type chooser. */
    @Override
    public void onClick(int id) {
        switch (id) {
            case ATTACHMENT_ACTION_PICTURE:
                selectImageAttachment();
                break;
            case ATTACHMENT_ACTION_CONTACT:
                selectContactAttachment();
                break;
            case ATTACHMENT_ACTION_AUDIO:
                selectAudioAttachment();
                break;
        }
    }

    public void viewContact() {
        if (mConversation != null) {
            Contact contact = mConversation.getContact();
            if (contact != null) {
                Intent i = new Intent(Intent.ACTION_VIEW, contact.getUri());
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
        }
    }

    /** Starts dialog for attachment selection. */
    public void selectAttachment() {
        if (attachmentMenu == null) {
            attachmentMenu = new IconContextMenu(getActivity(), CONTEXT_MENU_ATTACHMENT);
            attachmentMenu.addItem(getResources(), R.string.attachment_picture, R.drawable.ic_launcher_gallery, ATTACHMENT_ACTION_PICTURE);
            attachmentMenu.addItem(getResources(), R.string.attachment_contact, R.drawable.ic_launcher_contacts, ATTACHMENT_ACTION_CONTACT);
            if (AudioDialog.isSupported(getActivity()))
                attachmentMenu.addItem(getResources(), R.string.attachment_audio, R.drawable.ic_launcher_audio, ATTACHMENT_ACTION_AUDIO);
            attachmentMenu.setOnClickListener(this);
        }
        attachmentMenu.createMenu(getString(R.string.menu_attachment)).show();
    }

    /** Starts activity for an image attachment. */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void selectImageAttachment() {
        Intent pictureIntent;

        if (!MediaStorage.isStorageAccessFrameworkAvailable()) {
            pictureIntent = new Intent(Intent.ACTION_GET_CONTENT);
        }
        else {
            pictureIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        }

        pictureIntent
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*");

        Intent chooser = null;
        try {
            // check if camera is available
            final PackageManager packageManager = getActivity().getPackageManager();
            final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            List<ResolveInfo> list =
                    packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (list.size() <= 0) throw new UnsupportedOperationException();

            mCurrentPhoto = MediaStorage.getOutgoingImageFile();
            Intent take = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            take.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mCurrentPhoto));
            chooser = Intent.createChooser(pictureIntent, getString(R.string.chooser_send_picture));
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{take});
        }
        catch (UnsupportedOperationException ue) {
            Log.d(TAG, "no camera app or no camera present", ue);
        }
        catch (IOException e) {
            Log.e(TAG, "error creating temp file", e);
            Toast.makeText(getActivity(), R.string.chooser_error_no_camera,
                Toast.LENGTH_LONG).show();
        }

        if (chooser == null) chooser = pictureIntent;
        startActivityForResult(chooser, SELECT_ATTACHMENT_OPENABLE);
    }

    /** Starts activity for a vCard attachment from a contact. */
    private void selectContactAttachment() {
        Intent i = new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI);
        startActivityForResult(i, SELECT_ATTACHMENT_CONTACT);
    }

    private void selectAudioAttachment() {
        // create audio fragment if needed
        AudioFragment audio = getAudioFragment();
        mAudioDialog = new AudioDialog(getActivity(), audio, this);
        mAudioDialog.show();
    }

    private AudioFragment getAudioFragment() {
        FragmentManager fm = getFragmentManager();
        AudioFragment fragment = (AudioFragment) fm
            .findFragmentByTag("audio");
        if (fragment == null) {
            fragment = new AudioFragment();
            fm.beginTransaction()
                .add(fragment, "audio")
                .commit();
        }

        return fragment;
    }

    private void deleteThread() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.confirm_delete_thread);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.confirm_will_delete_thread);
        builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mComposer.setText("");
                        MessagesProvider.deleteThread(getActivity(), threadId);
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    private void deleteMessage(final long id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.confirm_delete_message);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.confirm_will_delete_message);
        builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getActivity().getContentResolver().delete(
                                ContentUris.withAppendedId(
                                        Messages.CONTENT_URI, id), null, null);
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    private void blockUser() {
        new AlertDialog.Builder(getActivity())
            .setTitle(R.string.menu_block_user)
            .setMessage(R.string.msg_block_user_warning)
            .setPositiveButton(R.string.menu_block_user, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    setPrivacy(PRIVACY_BLOCK);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void unblockUser() {
        new AlertDialog.Builder(getActivity())
            .setTitle(R.string.menu_unblock_user)
            .setMessage(R.string.msg_unblock_user_warning)
            .setPositiveButton(R.string.menu_unblock_user, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    setPrivacy(PRIVACY_UNBLOCK);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void decryptMessage(CompositeMessage msg) {
        try {
            Context ctx = getActivity();

            MessageUtils.decryptMessage(ctx, null, msg);

            // write updated data to the database
            ContentValues values = new ContentValues();
            MessageUtils.fillContentValues(values, msg);

            ctx.getContentResolver().update(Messages.getUri(msg.getId()),
                    values, null, null);
        }
        catch (Exception e) {
            Log.e(TAG, "decryption failed", e);

            // TODO i18n
            Toast.makeText(getActivity(), "Decryption failed!",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void retryMessage(CompositeMessage msg) {
        Intent i = new Intent(getActivity(), MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_RETRY);
        i.putExtra(MessageCenterService.EXTRA_MESSAGE, ContentUris.withAppendedId
                (Messages.CONTENT_URI, msg.getDatabaseId()));
        getActivity().startService(i);
    }

    private static final int MENU_RETRY = 1;
    private static final int MENU_SHARE = 2;
    private static final int MENU_COPY_TEXT = 3;
    private static final int MENU_DECRYPT = 4;
    private static final int MENU_OPEN = 5;
    private static final int MENU_DOWNLOAD = 6;
    private static final int MENU_CANCEL_DOWNLOAD = 7;
    private static final int MENU_DETAILS = 8;
    private static final int MENU_DELETE = 9;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        MessageListItem vitem = (MessageListItem) info.targetView;
        CompositeMessage msg = vitem.getMessage();

        menu.setHeaderTitle(R.string.title_message_options);

        // message waiting for user review
        if (msg.getStatus() == Messages.STATUS_PENDING) {
            menu.add(CONTEXT_MENU_GROUP_ID, MENU_RETRY, MENU_RETRY, R.string.resend);
        }

        // some commands can be used only on unencrypted messages
        if (!msg.isEncrypted()) {

            AttachmentComponent attachment = (AttachmentComponent) msg
                    .getComponent(AttachmentComponent.class);
            TextComponent text = (TextComponent) msg
                    .getComponent(TextComponent.class);

            // sharing media messages has no purpose if media file hasn't been
            // retrieved yet
            if (text != null || (attachment != null ? attachment.getLocalUri() != null : true)) {
                menu.add(CONTEXT_MENU_GROUP_ID, MENU_SHARE, MENU_SHARE, R.string.share);
            }

            // non-empty text: copy text to clipboard
            if (text != null && !TextUtils.isEmpty(text.getContent())) {
                menu.add(CONTEXT_MENU_GROUP_ID, MENU_COPY_TEXT, MENU_COPY_TEXT,
                        R.string.copy_message_text);
            }

            if (attachment != null) {

                // message has a local uri - add open file entry
                if (attachment.getLocalUri() != null) {
                    int resId;
                    if (attachment instanceof ImageComponent)
                        resId = R.string.view_image;
                    else if (attachment instanceof  AudioComponent)
                        resId = R.string.open_audio;
                    else
                        resId = R.string.open_file;

                    menu.add(CONTEXT_MENU_GROUP_ID, MENU_OPEN, MENU_OPEN, resId);
                }

                // message has a fetch url - add download control entry
                if (msg.getDirection() == Messages.DIRECTION_IN && attachment.getFetchUrl() != null) {
                    int id, string;
                    if (!DownloadService.isQueued(attachment.getFetchUrl())) {
                        // already fetched
                        if (attachment.getLocalUri() != null)
                            string = R.string.download_again;
                        else
                            string = R.string.download_file;
                        id = MENU_DOWNLOAD;
                    }
                    else {
                        string = R.string.download_cancel;
                        id = MENU_CANCEL_DOWNLOAD;
                    }
                    menu.add(CONTEXT_MENU_GROUP_ID, id, id, string);
                }


            }

        }

        else {

            menu.add(CONTEXT_MENU_GROUP_ID, MENU_DECRYPT, MENU_DECRYPT,
                    R.string.decrypt_message);

        }

        menu.add(CONTEXT_MENU_GROUP_ID, MENU_DETAILS, MENU_DETAILS, R.string.menu_message_details);
        menu.add(CONTEXT_MENU_GROUP_ID, MENU_DELETE, MENU_DELETE, R.string.delete_message);
    }

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        // not our context
        if (item.getGroupId() != CONTEXT_MENU_GROUP_ID)
            return false;

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
                .getMenuInfo();
        MessageListItem v = (MessageListItem) info.targetView;
        CompositeMessage msg = v.getMessage();

        switch (item.getItemId()) {
            case MENU_SHARE: {
                Intent i = null;
                AttachmentComponent attachment = (AttachmentComponent) msg
                        .getComponent(AttachmentComponent.class);

                if (attachment != null) {
                    i = ComposeMessage.sendMediaMessage(attachment.getLocalUri(),
                            attachment.getMime());
                }

                else {
                    TextComponent txt = (TextComponent) msg
                            .getComponent(TextComponent.class);

                    if (txt != null)
                        i = ComposeMessage.sendTextMessage(txt.getContent());
                }

                if (i != null)
                    startActivity(i);
                else
                    // TODO ehm...
                    Log.w(TAG, "error sharing message");

                return true;
            }

            case MENU_COPY_TEXT: {
                TextComponent txt = (TextComponent) msg
                        .getComponent(TextComponent.class);

                String text = (txt != null) ? txt.getContent() : "";

                ClipboardManager cpm = (ClipboardManager) getActivity()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                cpm.setText(text);

                Toast.makeText(getActivity(), R.string.message_text_copied,
                        Toast.LENGTH_SHORT).show();
                return true;
            }

            case MENU_DECRYPT: {
                decryptMessage(msg);
                return true;
            }

            case MENU_RETRY: {
                retryMessage(msg);
                return true;
            }

            case MENU_DOWNLOAD: {
                startDownload(msg);
                return true;
            }

            case MENU_CANCEL_DOWNLOAD: {
                stopDownload(msg);
                return true;
            }

            case MENU_DETAILS: {
                CharSequence messageDetails = MessageUtils.getMessageDetails(
                        getActivity(), msg, mUserPhone != null ? mUserPhone : mUserJID);
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.title_message_details)
                        .setMessage(messageDetails)
                        .setPositiveButton(android.R.string.ok, null)
                        .setCancelable(true).show();
                return true;
            }

            case MENU_DELETE: {
                deleteMessage(msg.getDatabaseId());
                return true;
            }

            case MENU_OPEN: {
                openFile(msg);
                return true;
            }
        }

        return super.onContextItemSelected(item);
    }

    private void startQuery(boolean reloadConversation, boolean progress) {
        try {
            if (progress)
                getActivity().setProgressBarIndeterminateVisibility(true);

            CompositeMessage.startQuery(mQueryHandler, MESSAGE_LIST_QUERY_TOKEN,
                    threadId);

            if (reloadConversation)
                Conversation.startQuery(mQueryHandler,
                        CONVERSATION_QUERY_TOKEN, threadId);

        }
        catch (SQLiteException e) {
            Log.e(TAG, "query error", e);
        }
    }

    private void loadConversationMetadata(Uri uri) {
        threadId = ContentUris.parseId(uri);
        mConversation = Conversation.loadFromId(getActivity(), threadId);
        if (mConversation == null) {
            Log.w(TAG, "conversation for thread " + threadId + " not found!");
            startActivity(new Intent(getActivity(), ConversationList.class));
            getActivity().finish();
            return;
        }

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

    private Bundle myArguments() {
        return (mArguments != null) ? mArguments : getArguments();
    }

    public void setMyArguments(Bundle args) {
        mArguments = args;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SELECT_ATTACHMENT_OPENABLE) {
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = null;
                String mime = null;

                // returning from camera
                if (data == null) {
                    /*
                     * FIXME picture taking should be done differently.
                     * Use a MediaStore-based uri and use a requestCode just
                     * for taking pictures.
                     */
                    if (mCurrentPhoto != null) {
                        uri = Uri.fromFile(mCurrentPhoto);
                        // notify media scanner
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        mediaScanIntent.setData(uri);
                        getActivity().sendBroadcast(mediaScanIntent);
                        mCurrentPhoto = null;
                    }
                }
                else {
                    if (mCurrentPhoto != null) {
                        mCurrentPhoto.delete();
                        mCurrentPhoto = null;
                    }
                    uri = data.getData();
                    mime = data.getType();

                    // SAF available, request persistable permissions
                    if (MediaStorage.isStorageAccessFrameworkAvailable()) {
                        MediaStorage.requestPersistablePermissions(getActivity(), data);
                    }
                }

                if (uri != null) {
                    if (mime == null || mime.startsWith("*/")
                            || mime.endsWith("/*")) {
                        mime = MediaStorage.getType(getActivity(), uri);
                        Log.v(TAG, "using detected mime type " + mime);
                    }

                    if (ImageComponent.supportsMimeType(mime))
                        sendBinaryMessage(uri, mime, true, ImageComponent.class);
                    else if (VCardComponent.supportsMimeType(mime))
                        sendBinaryMessage(uri, VCardComponent.MIME_TYPE, false, VCardComponent.class);
                    else
                        Toast.makeText(getActivity(), R.string.send_mime_not_supported, Toast.LENGTH_LONG)
                            .show();
                }
            }
            // operation aborted
            else {
                // delete photo :)
                if (mCurrentPhoto != null) {
                    mCurrentPhoto.delete();
                    mCurrentPhoto = null;
                }
            }
        }
        else if (requestCode == SELECT_ATTACHMENT_CONTACT) {
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = data.getData();
                if (uri != null) {
                    // get lookup key
                    final Cursor c = getActivity().getContentResolver()
                        .query(uri, new String[] { Contacts.LOOKUP_KEY }, null, null, null);
                    if (c != null) {
                        try {
                            c.moveToFirst();
                            String lookupKey = c.getString(0);
                            Uri vcardUri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey);
                            sendBinaryMessage(vcardUri, VCardComponent.MIME_TYPE, false, VCardComponent.class);
                        }
                        finally {
                            c.close();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putParcelable(Uri.class.getName(), Threads.getUri(mUserJID));
        // save composer status
        if (mComposer != null)
            mComposer.onSaveInstanceState(out);
        // current photo being shot
        if (mCurrentPhoto != null) {
            out.putString("currentPhoto", mCurrentPhoto.toString());
        }
        // audio dialog open
        if (mAudioDialog != null) {
            mAudioDialog.onSaveInstanceState(out);
        }
    }

    private void processArguments(Bundle savedInstanceState) {
        Bundle args;
        if (savedInstanceState != null) {
            Uri uri = savedInstanceState.getParcelable(Uri.class.getName());
            // threadId = ContentUris.parseId(uri);
            args = new Bundle();
            args.putString("action", ComposeMessage.ACTION_VIEW_USERID);
            args.putParcelable("data", uri);

            String currentPhoto = savedInstanceState.getString("currentPhoto");
            if (currentPhoto != null) {
                mCurrentPhoto = new File(currentPhoto);
            }

            mAudioDialog = AudioDialog.onRestoreInstanceState(getActivity(),
                savedInstanceState, getAudioFragment(), this);
            if (mAudioDialog != null) {
                Log.d(TAG, "recreating audio dialog");
                mAudioDialog.show();
            }
        }
        else {
            args = myArguments();
        }

        if (args != null && args.size() > 0) {
            final String action = args.getString("action");

            // view intent
            if (Intent.ACTION_VIEW.equals(action)) {
                Uri uri = args.getParcelable("data");
                ContentResolver cres = getActivity().getContentResolver();

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

                    Cursor cp = cres.query(Messages.CONTENT_URI,
                            new String[] { Messages.THREAD_ID }, Messages.PEER
                                    + " = ?", new String[] { mUserJID }, null);
                    if (cp.moveToFirst())
                        threadId = cp.getLong(0);
                    cp.close();
                }
                c.close();

                if (threadId > 0) {
                    mConversation = Conversation.loadFromId(getActivity(),
                            threadId);
                }
                else {
                    mConversation = Conversation.createNew(getActivity());
                    mConversation.setRecipient(mUserJID);
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

                threadId = mConversation.getThreadId();
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

        // set title if we are autonomous
        if (mArguments != null) {
            String title = mUserName;
            //if (mUserPhone != null) title += " <" + mUserPhone + ">";
            setActivityTitle(title, "", null);
        }

        // update conversation stuff
        if (mConversation != null)
            onConversationCreated();

        // non existant thread - check for not synced contact
        if (threadId <= 0 && mConversation != null) {
            Contact contact = mConversation.getContact();
            if (mUserPhone != null && contact != null ? !contact.isRegistered() : true) {
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

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
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

    public void setActivityTitle(CharSequence title, CharSequence status, Contact contact) {
        Activity parent = getActivity();
        if (parent instanceof ComposeMessage)
            ((ComposeMessage) parent).setTitle(title, status, contact);
        else if (title != null)
            parent.setTitle(title);
    }

    public void setActivityStatusUpdating() {
        Activity parent = getActivity();
        if (parent instanceof ComposeMessage)
            ((ComposeMessage) parent).setUpdatingSubtitle();
        else if (mStatusText != null) {
            ComposeMessage.setUpdatingSubtitle(mStatusText);
        }
    }

    public ComposeMessage getParentActivity() {
        Activity _activity = getActivity();
        return (_activity instanceof ComposeMessage) ? (ComposeMessage) _activity
                : null;
    }

    private void processStart(boolean resuming) {
        ComposeMessage activity = getParentActivity();
        // opening for contact picker - do nothing
        if (threadId < 0 && activity != null
                && activity.getSendIntent() != null)
            return;

        if (mListAdapter == null) {
            Pattern highlight = null;
            Bundle args = myArguments();
            if (args != null) {
                String highlightString = args
                        .getString(ComposeMessage.EXTRA_HIGHLIGHT);
                highlight = (highlightString == null) ? null : Pattern.compile(
                        "\\b" + Pattern.quote(highlightString),
                        Pattern.CASE_INSENSITIVE);
            }

            mListAdapter = new MessageListAdapter(getActivity(), null,
                    highlight, getListView(), this);
            mListAdapter.setOnContentChangedListener(mContentChangedListener);
            setListAdapter(mListAdapter);
        }

        if (threadId > 0) {
            // always reload conversation
            startQuery(true, resuming);
        }
        else {
            // HACK this is for crappy honeycomb :)
            getActivity().setProgressBarIndeterminateVisibility(false);

            mConversation = Conversation.createNew(getActivity());
            mConversation.setRecipient(mUserJID);
            onConversationCreated();
        }
    }

    /** Called when the {@link Conversation} object has been created. */
    private void onConversationCreated() {
        // subscribe to presence notifications
        subscribePresence();

        // restore any draft
        mComposer.restoreText(mConversation.getDraft());

        if (mConversation.getThreadId() > 0 && mConversation.getUnreadCount() > 0) {
            /*
             * FIXME this has the usual issue about resuming while screen is
             * still locked, having focus and so on...
             * See issue #28.
             */
            mConversation.markAsRead();
        }
        else {
            // new conversation -- observe peer Uri
            registerPeerObserver();
        }

        // update contact icon
        setActivityTitle(null, null, mConversation.getContact());

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
                            showIdentityDialog(true);
                        }
                    }
                );

            }
        }

        if (mInvitationBar != null)
            mInvitationBar.setVisibility(visible ? View.VISIBLE : View.GONE);

        updateUI();
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
                            // this will trigger a Contact reload
                            mConversation.setRecipient(mUserJID);
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
        // this will trigger contact reload
        mConversation.setRecipient(mUserJID);
    }

    private void showIdentityDialog(boolean informationOnly) {
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

        AlertDialog.Builder builder = new AlertDialog
            .Builder(getActivity())
            .setIcon(android.R.drawable.ic_dialog_info)
            .setMessage(text);

        if (informationOnly) {
            builder.setPositiveButton(android.R.string.ok, null)
                .setTitle(R.string.title_invitation);
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
            builder.setTitle(R.string.title_public_key_warning)
                .setPositiveButton(R.string.button_accept, listener)
                .setNegativeButton(R.string.button_block, listener);
        }

        builder.show();
    }

    private void hideWarning() {
        LinearLayout root = (LinearLayout) getView().findViewById(R.id.container);
        TextView warning = (TextView) root.findViewById(R.id.warning_bar);
        if (warning != null) {
            root.removeView(warning);
        }
    }

    private void trustKeyChange() {
        // mark current key as trusted
        UsersProvider.trustUserKey(getActivity(), mUserJID);
        // reload contact
        invalidateContact();
    }

    private void showKeyChangedWarning() {
        showWarning(getText(R.string.warning_public_key), new View.OnClickListener() {
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
                                showIdentityDialog(false);
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
                new AlertDialog.Builder(getActivity())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.title_public_key_warning)
                    .setMessage(R.string.msg_public_key_warning)
                    .setPositiveButton(R.string.button_accept, listener)
                    .setNeutralButton(R.string.button_identity, listener)
                    .setNegativeButton(R.string.button_block, listener)
                    .show();
            }
        }, WarningType.FATAL);
    }

    private void showWarning(CharSequence text, View.OnClickListener listener, WarningType type) {
        LinearLayout root = (LinearLayout) getView().findViewById(R.id.container);
        TextView warning = (TextView) root.findViewById(R.id.warning_bar);
        if (warning == null) {
            warning = (TextView) LayoutInflater.from(getActivity())
                .inflate(R.layout.warning_bar, root, false);
            root.addView(warning, 0);
        }
        else {
            // check type priority
            WarningType oldType = (WarningType) warning.getTag();
            if (oldType != null && oldType.getValue() > type.getValue())
                return;
        }
        int textId = 0;
        int colorId = 0;
        switch (type) {
            case FATAL:
                textId = android.R.attr.textAppearanceSmallInverse;
                colorId = R.color.warning_bar_background_fatal;
                break;
            case WARNING:
                textId = android.R.attr.textAppearanceSmall;
                colorId = R.color.warning_bar_background_warning;
                break;
        }
        final TypedValue typedValue = new TypedValue();
        getActivity().getTheme().resolveAttribute(textId, typedValue, true);
        warning.setTextAppearance(getActivity(), typedValue.resourceId);
        warning.setBackgroundColor(getResources().getColor(colorId));
        warning.setTag(type);
        warning.setOnClickListener(listener);
        warning.setText(text);
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
                                    boolean subscribedFrom = intent.getBooleanExtra(MessageCenterService.EXTRA_SUBSCRIBED_FROM, false);
                                    String newFingerprint = intent.getStringExtra(MessageCenterService.EXTRA_FINGERPRINT);
                                    // if this is null, we are accepting the key for the first time
                                    PGPPublicKeyRing trustedPublicKey = contact.getTrustedPublicKeyRing();

                                    // request the key if we don't have a trusted one or we are subscribed from the contact
                                    boolean requestKey = (trustedPublicKey == null || subscribedFrom);
                                    // check if fingerprint changed
                                    if (trustedPublicKey != null) {
                                        String oldFingerprint = PGP.getFingerprint(PGP.getMasterKey(trustedPublicKey));
                                        if (newFingerprint == null || newFingerprint.equalsIgnoreCase(oldFingerprint)) {
                                            // no fingerprint available or fingerprint has not changed since last time
                                            requestKey = false;
                                        }
                                    }
                                    else {
                                        // request key if we got one in the first place
                                        requestKey = (contact.getFingerprint() != null);
                                    }

                                    if (requestKey) {
                                        // warn user that public key is changed
                                        showKeyChangedWarning();
                                    }
                                }

                                if (Presence.Type.available.toString().equals(type)) {
                                    mAvailableResources.add(from);
                                    statusText = context.getString(R.string.seen_online_label);

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
                                    mCurrentStatus = statusText;
                                    if (!mIsTyping)
                                        setStatusText(statusText);
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
                            if (name != null && name.equalsIgnoreCase(getString(R.string.app_name))) {
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
            showWarning(getText(R.string.warning_older_version), null, WarningType.WARNING);
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

    private void setStatusText(CharSequence text) {
        Activity parent = getActivity();
        if (parent instanceof ComposeMessage)
            setActivityTitle(null, text, null);
        else {
            if (mStatusText != null)
                mStatusText.setText(text);
        }
    }

    private synchronized void registerPeerObserver() {
        if (mPeerObserver == null) {
            Uri uri = Threads.getUri(mConversation.getRecipient());
            mPeerObserver = new PeerObserver(getActivity(), mQueryHandler);
            getActivity().getContentResolver().registerContentObserver(uri,
                    false, mPeerObserver);
        }
    }

    private synchronized void unregisterPeerObserver() {
        if (mPeerObserver != null) {
            getActivity().getContentResolver().unregisterContentObserver(
                    mPeerObserver);
            mPeerObserver = null;
        }
    }

    private final class PeerObserver extends ContentObserver {
        private final Context mContext;

        public PeerObserver(Context context, Handler handler) {
            super(handler);
            mContext = context;
        }

        @Override
        public void onChange(boolean selfChange) {
            Conversation conv = Conversation.loadFromUserId(mContext, mUserJID);

            if (conv != null) {
                mConversation = conv;
                threadId = mConversation.getThreadId();

                // auto-unregister
                unregisterPeerObserver();
            }

            // fire cursor update
            processStart(false);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (Authenticator.getDefaultAccount(getActivity()) == null) {
            NumberValidation.start(getActivity());
            getActivity().finish();
            return;
        }

        // hold message center
        MessageCenterService.hold(getActivity());

        ComposeMessage activity = getParentActivity();
        if (activity == null || !activity.hasLostFocus() || activity.hasWindowFocus()) {
            onFocus();
        }
    }

    public void onFocus() {
        // resume content watcher
        resumeContentListener();

        // we are updating the status now
        setActivityStatusUpdating();

        // cursor was previously destroyed -- reload everything
        // mConversation = null;
        processStart(true);
        if (mUserJID != null) {
            // set notifications on pause
            MessagingNotification.setPaused(mUserJID);

            // clear chat invitation (if any)
            // TODO use jid here
            MessagingNotification.clearChatInvitation(getActivity(), mUserJID);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // notify composer bar
        mComposer.onPause();

        // hide emoji drawer
        tryHideEmojiDrawer();

        // pause content watcher
        pauseContentListener();

        // notify parent of pausing
        ComposeMessage parent = getParentActivity();
        if (parent != null)
            parent.fragmentLostFocus();

        CharSequence text = mComposer.getText();
        int len = text.length();

        // resume notifications
        MessagingNotification.setPaused(null);

        // save last message as draft
        if (threadId > 0) {

            // no draft and no messages - delete conversation
            if (len == 0 && mConversation.getMessageCount() == 0 &&
                    mConversation.getRequestStatus() != Threads.REQUEST_WAITING) {

                // FIXME shouldn't be faster to just delete the thread?
                MessagesProvider.deleteThread(getActivity(), threadId);
            }

            // update draft
            else {
                ContentValues values = new ContentValues(1);
                values.put(Threads.DRAFT, (len > 0) ? text.toString() : null);
                getActivity().getContentResolver().update(
                        ContentUris.withAppendedId(Threads.CONTENT_URI, threadId),
                        values, null, null);
            }
        }

        // new thread, create empty conversation
        else {
            if (len > 0) {
                // save to local storage
                ContentValues values = new ContentValues();
                // must supply a message ID...
                values.put(Messages.MESSAGE_ID,
                        "draft" + (new Random().nextInt()));
                values.put(Messages.PEER, mUserJID);
                values.put(Messages.BODY_CONTENT, new byte[0]);
                values.put(Messages.BODY_LENGTH, 0);
                values.put(Messages.BODY_MIME, TextComponent.MIME_TYPE);
                values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
                values.put(Messages.TIMESTAMP, System.currentTimeMillis());
                values.put(Messages.ENCRYPTED, false);
                values.put(Threads.DRAFT, text.toString());
                getActivity().getContentResolver().insert(Messages.CONTENT_URI,
                        values);
            }
        }

        if (len > 0) {
            Toast.makeText(getActivity(), R.string.msg_draft_saved,
                    Toast.LENGTH_LONG).show();
        }

        if (Preferences.getSendTyping(getActivity())) {
            // send inactive state notification
            if (mAvailableResources.size() > 0)
                MessageCenterService.sendChatState(getActivity(), mUserJID, ChatState.inactive);
            mComposer.resetCompose();
        }

        // unsubcribe presence notifications
        unsubcribePresence();

        // release message center
        MessageCenterService.release(getActivity());

        // release audio player
        if (mPlayer != null) {
            stopMediaPlayerUpdater();
            mMediaPlayerMessageId = -1;
            mPlayer.release();
            mPlayer = null;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        unregisterPeerObserver();
        if (mListAdapter != null)
            mListAdapter.changeCursor(null);

        // be sure to cancel all queries
        mQueryHandler.cancelOperation(MESSAGE_LIST_QUERY_TOKEN);
        mQueryHandler.cancelOperation(CONVERSATION_QUERY_TOKEN);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mComposer != null) {
            mComposer.onDestroy();
        }
        if (mAudioDialog != null) {
            mAudioDialog.dismiss();
            mAudioDialog = null;
        }
    }

    private void pauseContentListener() {
        if (mListAdapter != null)
            mListAdapter.setOnContentChangedListener(null);
    }

    private void resumeContentListener() {
        if (mListAdapter != null)
            mListAdapter.setOnContentChangedListener(mContentChangedListener);
    }

    public final boolean isFinishing() {
        Activity activity = getActivity();
        return (activity == null || activity.isFinishing()) || isRemoving();
    }

    private void updateUI() {
        Contact contact = (mConversation != null) ? mConversation
                .getContact() : null;

        boolean contactEnabled = contact != null && contact.getId() > 0;
        boolean threadEnabled = (threadId > 0);

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
            mDeleteThreadMenu.setEnabled(threadEnabled);
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

    boolean tryHideEmojiDrawer() {
        if (mComposer.isEmojiVisible()) {
            mComposer.hideEmojiDrawer(false);
            return true;
        }
        return false;
    }

    /** The conversation list query handler. */
    // TODO convert to static class and use a weak reference to the context
    private final class MessageListQueryHandler extends AsyncQueryHandler {
        public MessageListQueryHandler() {
            super(getActivity().getApplicationContext().getContentResolver());
        }

        @Override
        protected synchronized void onQueryComplete(int token, Object cookie,
                Cursor cursor) {
            if (cursor == null || isFinishing()) {
                // close cursor - if any
                if (cursor != null)
                    cursor.close();

                Log.e(TAG, "query aborted or error!");
                unregisterPeerObserver();
                mListAdapter.changeCursor(null);
                return;
            }

            switch (token) {
                case MESSAGE_LIST_QUERY_TOKEN:

                    // no messages to show - exit
                    if (cursor.getCount() == 0
                            && (mConversation == null ||
                                // no draft
                                (mConversation.getDraft() == null &&
                                // no subscription request
                                mConversation.getRequestStatus() != Threads.REQUEST_WAITING &&
                                // no text in compose entry
                                mComposer.getText().length() == 0))) {

                        Log.i(TAG, "no data to view - exit");

                        // close conversation
                        closeConversation();

                    }
                    else {
                        // see if we have to scroll to a specific message
                        int newSelectionPos = -1;

                        Bundle args = myArguments();
                        if (args != null) {
                            long msgId = args.getLong(ComposeMessage.EXTRA_MESSAGE,
                                    -1);
                            if (msgId > 0) {

                                cursor.moveToPosition(-1);
                                while (cursor.moveToNext()) {
                                    long curId = cursor.getLong(CompositeMessage.COLUMN_ID);
                                    if (curId == msgId) {
                                        newSelectionPos = cursor.getPosition();
                                        break;
                                    }
                                }
                            }
                        }

                        mListAdapter.changeCursor(cursor);
                        if (newSelectionPos > 0)
                            getListView().setSelection(newSelectionPos);

                        getActivity().setProgressBarIndeterminateVisibility(false);
                        updateUI();
                    }

                    break;

                case CONVERSATION_QUERY_TOKEN:
                    if (cursor.moveToFirst()) {
                        mConversation = Conversation.createFromCursor(
                                getActivity(), cursor);
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

    public Contact getContact() {
        return (mConversation != null) ? mConversation.getContact() : null;
    }

    public long getThreadId() {
        return threadId;
    }

    public String getUserId() {
        return mUserJID;
    }

    public void setTextEntry(CharSequence text) {
        mComposer.setText(text);
    }

    @Override
    public boolean onLongClick(View v) {
        // this seems to be necessary...
        return false;
    }

    public void closeConversation() {
        // main activity
        if (getParentActivity() != null) {
            getActivity().finish();
        }
        // using fragments...
        else {
            ConversationList activity = (ConversationList) getActivity();
            activity.getListFragment().endConversation(this);
        }
    }

    private void offlineModeWarning() {
        if (Preferences.getOfflineMode(getActivity()) && !mOfflineModeWarned) {
            mOfflineModeWarned = true;
            Toast.makeText(getActivity(), R.string.warning_offline_mode,
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRecordingSuccessful(File file) {
        if (file != null)
            sendBinaryMessage(Uri.fromFile(file), AudioDialog.DEFAULT_MIME, true, AudioComponent.class);
    }

    @Override
    public void onRecordingCancel() {
        mAudioDialog = null;
    }

    @Override
    public void buttonClick(File audioFile, AudioContentViewControl view, long messageId) {
        if (mMediaPlayerMessageId == messageId) {
            switch (mStatus) {
                case AudioContentView.STATUS_PLAYING:
                    pauseAudio(view);
                    break;
                case AudioContentView.STATUS_PAUSED:
                case AudioContentView.STATUS_ENDED:
                    playAudio(view, messageId);
                    break;

            }
        }
        else {
            switch (mStatus) {
                case AudioContentView.STATUS_IDLE:
                    if (prepareAudio(audioFile, view, messageId))
                        playAudio(view, messageId);
                    break;
                case AudioContentView.STATUS_ENDED:
                case AudioContentView.STATUS_PLAYING:
                case AudioContentView.STATUS_PAUSED:
                    resetAudio(mAudioControl);
                    if (prepareAudio(audioFile, view, messageId))
                        playAudio(view, messageId);
                    break;
            }
        }
    }

    private boolean prepareAudio(File audioFile, final AudioContentViewControl view, final long messageId) {
        if (mPlayer == null)
            mPlayer = new MediaPlayer();

        stopMediaPlayerUpdater();
        try {
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setDataSource(audioFile.getAbsolutePath());
            mPlayer.prepare();

            // prepare was successful
            mMediaPlayerMessageId = messageId;
            mAudioControl = view;

            view.prepare(mPlayer.getDuration());
            mPlayer.seekTo(view.getPosition());
            view.setProgressChangeListener(true);
            mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopMediaPlayerUpdater();
                    view.end();
                    mPlayer.seekTo(0);
                    setAudioStatus(AudioContentView.STATUS_ENDED);
                }
            });
            return true;
        }
        catch (IOException e) {
            Toast.makeText(getActivity(), R.string.err_file_not_found, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @Override
    public void playAudio(AudioContentViewControl view, long messageId) {
        view.play();
        mPlayer.start();
        setAudioStatus(AudioContentView.STATUS_PLAYING);
        startMediaPlayerUpdater(view);
    }

    private void updatePosition(AudioContentViewControl view) {
        view.updatePosition(mPlayer.getCurrentPosition());
    }

    @Override
    public void pauseAudio(AudioContentViewControl view) {
        view.pause();
        mPlayer.pause();
        stopMediaPlayerUpdater();
        setAudioStatus(AudioContentView.STATUS_PAUSED);
    }

    private void resetAudio(AudioContentViewControl view) {
        if (view != null){
            stopMediaPlayerUpdater();
            view.end();
        }
        if (mPlayer != null)
            mPlayer.reset();
        mMediaPlayerMessageId = -1;
    }

    private void setAudioStatus(int audioStatus) {
        mStatus = audioStatus;
    }

    @Override
    public void stopAllSounds() {
        resetAudio(mAudioControl);
    }

    @Override
    public void onBind(long messageId, final AudioContentViewControl view) {
        if (mMediaPlayerMessageId == messageId) {
            mAudioControl = view;
            if (mPlayer != null) {
                mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        stopMediaPlayerUpdater();
                        view.end();
                        mPlayer.seekTo(0);
                        setAudioStatus(AudioContentView.STATUS_ENDED);
                    }
                });

                view.setProgressChangeListener(true);
                view.prepare(mPlayer.getDuration());
                if (mPlayer.isPlaying()) {
                    startMediaPlayerUpdater(view);
                    view.play();
                }
                else {
                    view.pause();
                }
            }
        }
    }

    @Override
    public void onUnbind(long messageId, AudioContentViewControl view) {
        if (mMediaPlayerMessageId == messageId) {
            mAudioControl = null;
            if (mPlayer != null) {
                mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        mPlayer.seekTo(0);
                        setAudioStatus(AudioContentView.STATUS_ENDED);
                    }
                });
            }

            view.setProgressChangeListener(false);
            if (!MessagesProvider.exists(getActivity(), messageId)) {
                resetAudio(view);
            }

            else {
                stopMediaPlayerUpdater();
            }
        }
    }

    @Override
    public boolean isPlaying() {
        return mPlayer.isPlaying();
    }

    @Override
    public void seekTo(int position) {
        mPlayer.seekTo(position);
    }

    private void startMediaPlayerUpdater(final AudioContentViewControl view) {
        updatePosition(view);
        mMediaPlayerUpdater = new Runnable() {
            @Override
            public void run() {
                updatePosition(view);
                mHandler.postDelayed(this, 100);
            }
        };
        mHandler.postDelayed(mMediaPlayerUpdater, 100);
    }

    private void stopMediaPlayerUpdater() {
        if (mMediaPlayerUpdater != null) {
            mHandler.removeCallbacks(mMediaPlayerUpdater);
            mMediaPlayerUpdater = null;
        }
    }

}
