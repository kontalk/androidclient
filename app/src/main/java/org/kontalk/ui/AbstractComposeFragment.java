/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.nispok.snackbar.Snackbar;
import com.nispok.snackbar.SnackbarManager;
import com.nispok.snackbar.enums.SnackbarType;
import com.nispok.snackbar.listeners.ActionClickListener;

import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jxmpp.jid.Jid;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDiskIOException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.Contacts;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.crypto.Coder;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.message.AttachmentComponent;
import org.kontalk.message.AudioComponent;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.GroupCommandComponent;
import org.kontalk.message.ImageComponent;
import org.kontalk.message.LocationComponent;
import org.kontalk.message.MessageComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.message.VCardComponent;
import org.kontalk.position.Position;
import org.kontalk.position.PositionManager;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Conversations;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.service.DownloadService;
import org.kontalk.service.msgcenter.MessageCenterClient;
import org.kontalk.service.msgcenter.MessageCenterClient.ConnectionLifecycleListener;
import org.kontalk.service.msgcenter.MessageCenterClient.PresenceListener;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.ui.adapter.MessageListAdapter;
import org.kontalk.ui.view.AttachmentRevealFrameLayout;
import org.kontalk.ui.view.AudioContentView;
import org.kontalk.ui.view.AudioContentViewControl;
import org.kontalk.ui.view.AudioPlayerControl;
import org.kontalk.ui.view.ComposerBar;
import org.kontalk.ui.view.ComposerListener;
import org.kontalk.ui.view.MessageListItem;
import org.kontalk.ui.view.ReplyBar;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Permissions;
import org.kontalk.util.Preferences;
import org.kontalk.util.SystemUtils;

import static android.content.res.Configuration.KEYBOARDHIDDEN_NO;


/**
 * Abstract message composing fragment.
 *
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public abstract class AbstractComposeFragment extends ListFragment implements
    ComposerListener, View.OnLongClickListener,
    // TODO these two interfaces should be handled by an inner class
    AudioDialog.AudioDialogListener, AudioPlayerControl,
    AbsListView.MultiChoiceModeListener {
    static final String TAG = ComposeMessage.TAG;

    private static final int MESSAGE_LIST_QUERY_TOKEN = 8720;
    private static final int CONVERSATION_QUERY_TOKEN = 8721;
    private static final int MESSAGE_PAGE_QUERY_TOKEN = 8723;

    /**
     * How many messages to load per page.
     */
    private static final int MESSAGE_PAGE_SIZE = 1000;

    private static final int SELECT_ATTACHMENT_OPENABLE = 1;
    private static final int SELECT_ATTACHMENT_CONTACT = 2;
    private static final int SELECT_ATTACHMENT_PHOTO = 3;
    private static final int SELECT_ATTACHMENT_LOCATION = 4;
    private static final int REQUEST_INVITE_USERS = 5;

    // use this as base for request codes for child classes
    protected static final int REQUEST_FIRST_CHILD = 100;

    protected enum WarningType {
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

    /* Attachment chooser stuff. */
    private AttachmentRevealFrameLayout mAttachmentContainer;

    protected ComposerBar mComposer;
    protected ReplyBar mReplyBar;

    MessageListQueryHandler mQueryHandler;
    MessageListAdapter mListAdapter;
    /**
     * Header view for the list view: "previous messages" button.
     */
    private View mHeaderView;
    private View mNextPageButton;
    private TextView mStatusText;
    private MenuItem mDeleteThreadMenu;
    private MenuItem mToggleEncryptionMenu;

    private ImageView mBackground;

    /**
     * The thread id.
     */
    long threadId = -1;
    protected Conversation mConversation;
    protected String mUserName;

    protected boolean mConnected;

    /**
     * Available resources.
     */
    protected Set<String> mAvailableResources = new HashSet<>();

    /**
     * Media player stuff.
     */
    private int mMediaPlayerStatus = AudioContentView.STATUS_IDLE;
    private Handler mHandler;
    private Runnable mMediaPlayerUpdater;
    private AudioContentViewControl mAudioControl;
    private AudioFragment mAudioFragment;

    /**
     * Audio recording dialog.
     */
    private AudioDialog mAudioDialog;

    private PeerObserver mPeerObserver;
    private File mCurrentPhoto;

    protected LocalBroadcastManager mLocalBroadcastManager;
    private PresenceReceiver mPresenceReceiver;

    private boolean mOfflineModeWarned;
    protected CharSequence mCurrentStatus;

    private int mCheckedItemCount;

    /**
     * Returns a new fragment instance from a picked contact.
     */
    public static AbstractComposeFragment fromUserId(Context context, String userId, boolean creatingGroup) {
        AbstractComposeFragment f = new ComposeMessageFragment();
        Conversation conv = Conversation.loadFromUserId(context, userId);
        // not found - create new
        if (conv == null) {
            Bundle args = new Bundle();
            args.putString("action", ComposeMessage.ACTION_VIEW_USERID);
            args.putParcelable("data", Threads.getUri(userId));
            // non existing group threads can't exist, so no reason to use creatingGroup
            f.setArguments(args);
            return f;
        }

        return fromConversation(context, conv, creatingGroup);
    }

    /**
     * Returns a new fragment instance from a {@link Conversation} instance.
     */
    public static AbstractComposeFragment fromConversation(Context context,
        Conversation conv, boolean creatingGroup) {
        return fromConversation(context, conv.getThreadId(), conv.isGroupChat(), creatingGroup);
    }

    /**
     * Returns a new fragment instance from a thread ID.
     */
    private static AbstractComposeFragment fromConversation(Context context,
        long threadId, boolean group, boolean creatingGroup) {
        AbstractComposeFragment f = group ?
            new GroupMessageFragment() : new ComposeMessageFragment();
        Bundle args = new Bundle();
        args.putString("action", ComposeMessage.ACTION_VIEW_CONVERSATION);
        args.putParcelable("data",
            ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId));
        args.putBoolean(ComposeMessage.EXTRA_CREATING_GROUP, creatingGroup);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // setListAdapter() is post-poned

        ListView list = getListView();

        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        list.setMultiChoiceModeListener(this);

        // add header view (this must be done before setting the adapter)
        mHeaderView = LayoutInflater.from(getActivity())
            .inflate(R.layout.message_list_header, list, false);
        mNextPageButton = mHeaderView.findViewById(R.id.load_next_page);
        mNextPageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // disable button in the meantime
                enableHeaderView(false);
                // start query for the next page
                startMessagesQuery(mQueryHandler.getLastId());
            }
        });
        list.addHeaderView(mHeaderView, null, false);

        // set custom background (if any)
        mBackground = getView().findViewById(R.id.background);
        Drawable bg = Preferences.getConversationBackground(getActivity());
        if (bg != null) {
            mBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
            mBackground.setImageDrawable(bg);
        }
        else {
            mBackground.setScaleType(ImageView.ScaleType.FIT_XY);
            mBackground.setImageResource(R.drawable.app_background_tile);
        }

        processArguments(savedInstanceState);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(context);
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.compose_message, container, false);

        // footer (for tablet presence status)
        mStatusText = view.findViewById(R.id.status_text);

        // reply bar
        mReplyBar = view.findViewById(R.id.reply_bar);
        mReplyBar.setOnCancelListener(new ReplyBar.OnCancelListener() {
            @Override
            public void onCancel(ReplyBar view) {
                view.hide();
            }
        });

        mComposer = view.findViewById(R.id.composer_bar);
        mComposer.setComposerListener(this);
        mComposer.setRootView(view);

        Configuration config = getResources().getConfiguration();
        mComposer.onKeyboardStateChanged(config.keyboardHidden == KEYBOARDHIDDEN_NO);

        initAttachmentView(view);

        return view;
    }

    private final MessageListAdapter.OnContentChangedListener mContentChangedListener = new MessageListAdapter.OnContentChangedListener() {
        public void onContentChanged(MessageListAdapter adapter) {
            if (isVisible())
                startQuery();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
        mQueryHandler = new MessageListQueryHandler(this);
        mHandler = new Handler();

        // list adapter creation is post-poned
    }

    public boolean isActionModeActive() {
        return mCheckedItemCount > 0;
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        if (checked)
            mCheckedItemCount++;
        else
            mCheckedItemCount--;
        mode.setTitle(getResources()
            .getQuantityString(R.plurals.context_selected,
                mCheckedItemCount, mCheckedItemCount));

        mode.invalidate();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.compose_message_ctx, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        MenuItem deleteMenu = menu.findItem(R.id.menu_delete);
        MenuItem replyMenu = menu.findItem(R.id.menu_reply);
        MenuItem retryMenu = menu.findItem(R.id.menu_retry);
        MenuItem shareMenu = menu.findItem(R.id.menu_share);
        MenuItem copyTextMenu = menu.findItem(R.id.menu_copy_text);
        MenuItem detailsMenu = menu.findItem(R.id.menu_details);
        MenuItem openMenu = menu.findItem(R.id.menu_open);
        MenuItem dlMenu = menu.findItem(R.id.menu_download);
        MenuItem cancelDlMenu = menu.findItem(R.id.menu_cancel_download);

        // initial status
        deleteMenu.setVisible(true);
        replyMenu.setVisible(false);
        retryMenu.setVisible(false);
        shareMenu.setVisible(false);
        copyTextMenu.setVisible(true);
        detailsMenu.setVisible(false);
        openMenu.setVisible(false);
        dlMenu.setVisible(false);
        cancelDlMenu.setVisible(false);

        boolean singleItem = (mCheckedItemCount == 1);
        if (singleItem) {
            CompositeMessage msg = getCheckedItem();

            // group command can't be deleted or have details
            if (msg.hasComponent(GroupCommandComponent.class)) {
                deleteMenu.setVisible(false);
            }
            else {
                // message waiting for user review or not delivered
                if (msg.getStatus() == Messages.STATUS_PENDING || msg.getStatus() == Messages.STATUS_NOTDELIVERED) {
                    retryMenu.setVisible(true);
                }

                // some commands can be used only on unencrypted messages
                if (!msg.isEncrypted()) {
                    AttachmentComponent attachment = msg.getComponent(AttachmentComponent.class);
                    TextComponent text = msg.getComponent(TextComponent.class);

                    // sharing media messages has no purpose if media file hasn't been
                    // retrieved yet; also exclude location messages for now because they're unsupported
                    if ((text != null || attachment == null || attachment.getLocalUri() != null) && !msg.hasComponent(LocationComponent.class))
                        shareMenu.setVisible(true);

                    // non-empty text: copy text to clipboard
                    if (text != null && !TextUtils.isEmpty(text.getContent()))
                        copyTextMenu.setVisible(true);

                    // incoming text message: enable reply
                    if (msg.isIncoming() && text != null) {
                        replyMenu.setVisible(true);
                    }

                    if (attachment != null) {

                        // message has a local uri - add open file entry
                        if (attachment.getLocalUri() != null) {
                            int resId;
                            if (attachment instanceof ImageComponent)
                                resId = R.string.view_image;
                            else if (attachment instanceof AudioComponent)
                                resId = R.string.open_audio;
                            else
                                resId = R.string.open_file;

                            openMenu.setTitle(resId);
                            openMenu.setVisible(true);
                        }

                        // message has a fetch url - add download control entry
                        if (msg.getDirection() == Messages.DIRECTION_IN && attachment.getFetchUrl() != null) {
                            if (!DownloadService.isQueued(attachment.getFetchUrl())) {
                                int string;
                                // already fetched
                                if (attachment.getLocalUri() != null)
                                    string = R.string.download_again;
                                else
                                    string = R.string.download_file;

                                dlMenu.setTitle(string);
                                dlMenu.setVisible(true);
                            }
                            else {
                                cancelDlMenu.setVisible(true);
                            }
                        }
                    }
                }

                detailsMenu.setVisible(true);
            }
        }
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_delete: {
                // using clone because listview returns its original copy
                deleteSelectedMessages(SystemUtils
                    .cloneSparseBooleanArray(getListView().getCheckedItemPositions()));
                mode.finish();
                return true;
            }

            case R.id.menu_reply: {
                CompositeMessage msg = getCheckedItem();
                replyMessage(msg);
                mode.finish();
                return true;
            }

            case R.id.menu_retry: {
                CompositeMessage msg = getCheckedItem();
                retryMessage(msg);
                mode.finish();
                return true;
            }

            case R.id.menu_share: {
                CompositeMessage msg = getCheckedItem();
                shareMessage(msg);
                mode.finish();
                return true;
            }

            case R.id.menu_copy_text: {
                if (mCheckedItemCount == 1) {
                    CompositeMessage msg = getCheckedItem();
                    copyMessage(msg);
                }
                else {
                    copySelectedMessages(getListView().getCheckedItemPositions());
                }
                mode.finish();
                return true;
            }

            case R.id.menu_open: {
                CompositeMessage msg = getCheckedItem();
                openFile(msg);
                mode.finish();
                return true;
            }

            case R.id.menu_download: {
                CompositeMessage msg = getCheckedItem();
                startDownload(msg);
                mode.finish();
                return true;
            }

            case R.id.menu_cancel_download: {
                CompositeMessage msg = getCheckedItem();
                stopDownload(msg);
                mode.finish();
                return true;
            }

            case R.id.menu_details: {
                CompositeMessage msg = getCheckedItem();
                showMessageDetails(msg);
                mode.finish();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mCheckedItemCount = 0;
        getListView().clearChoices();
        mListAdapter.notifyDataSetChanged();
    }

    private CompositeMessage getCheckedItem() {
        if (mCheckedItemCount != 1)
            throw new IllegalStateException("checked items count must be exactly 1");

        Cursor cursor = (Cursor) getListView().getItemAtPosition(getCheckedItemPosition());
        return CompositeMessage.fromCursor(getActivity(), cursor);
    }

    private int getCheckedItemPosition() {
        SparseBooleanArray checked = getListView().getCheckedItemPositions();
        return checked.keyAt(checked.indexOfValue(true));
    }

    private void copyMessage(CompositeMessage msg) {
        ClipboardManager cpm = (ClipboardManager) getActivity()
            .getSystemService(Context.CLIPBOARD_SERVICE);
        cpm.setText(msg.toTextContent());

        Toast.makeText(getContext(), R.string.message_text_copied,
            Toast.LENGTH_SHORT).show();
    }

    private void copySelectedMessages(final SparseBooleanArray checked) {
        String prevUserId = null;
        String selfJid = Authenticator.getSelfJID(getContext());
        StringBuilder massText = new StringBuilder();

        for (int i = 0, c = getListView().getCount()+getListView().getHeaderViewsCount(); i < c; ++i) {
            if (checked.get(i)) {
                Cursor cursor = (Cursor) getListView().getItemAtPosition(i);
                CompositeMessage msg = CompositeMessage.fromCursor(getContext(), cursor);
                String userId = msg.getDirection() == Messages.DIRECTION_IN ?
                    msg.getSender() : Authenticator.getSelfJID(getContext());

                if (prevUserId == null || !prevUserId.equalsIgnoreCase(userId)) {
                    String displayName;
                    if (msg.getDirection() == Messages.DIRECTION_IN) {
                        Contact contact = Contact.findByUserId(getContext(), userId);
                        displayName = contact.getDisplayName();
                    }
                    else {
                        displayName = Authenticator.getDefaultDisplayName(getContext());
                    }

                    if (massText.length() > 0)
                        massText.append("\n");

                    massText.append(displayName)
                        .append(":\n");

                    prevUserId = userId;
                }

                String text = msg.toTextContent();
                if (text != null) {
                    massText.append(text)
                        .append("\n");
                }
            }
        }

        ClipboardManager cpm = (ClipboardManager) getActivity()
            .getSystemService(Context.CLIPBOARD_SERVICE);
        cpm.setText(massText.toString());

        Toast.makeText(getContext(), R.string.message_text_copied,
            Toast.LENGTH_SHORT).show();
    }

    private void deleteSelectedMessages(final SparseBooleanArray checked) {
        final List<CompositeMessage.DeleteMessageHolder> list = new LinkedList<>();
        for (int i = 0, c = getListView().getCount()+getListView().getHeaderViewsCount(); i < c; ++i) {
            if (checked.get(i)) {
                Cursor cursor = (Cursor) getListView().getItemAtPosition(i);
                // skip group command messages
                if (!GroupCommandComponent.isCursor(cursor))
                    list.add(new CompositeMessage.DeleteMessageHolder(cursor));
            }
        }

        new MaterialDialog.Builder(getContext())
            .content(R.string.confirm_will_delete_messages)
            .positiveText(android.R.string.ok)
            .positiveColorRes(R.color.button_danger)
            .onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    Context ctx = dialog.getContext();
                    for (CompositeMessage.DeleteMessageHolder item : list) {
                        CompositeMessage.deleteFromCursor(ctx, item);
                    }
                    mListAdapter.notifyDataSetChanged();
                }
            })
            .negativeText(android.R.string.cancel)
            .show();
    }

    private void initAttachmentView(View view) {
        mAttachmentContainer = view.findViewById(R.id.attachment_container);

        View.OnClickListener hideAttachmentListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideAttachmentView();
            }
        };
        view.findViewById(R.id.attachment_overlay).setOnClickListener(hideAttachmentListener);
        view.findViewById(R.id.attach_hide).setOnClickListener(hideAttachmentListener);

        view.findViewById(R.id.attach_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectPhotoAttachment();
                hideAttachmentView();
            }
        });

        view.findViewById(R.id.attach_gallery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectGalleryAttachment();
                hideAttachmentView();
            }
        });

        view.findViewById(R.id.attach_video).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), R.string.msg_not_implemented, Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.attach_audio).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectAudioAttachment();
                hideAttachmentView();
            }
        });

        view.findViewById(R.id.attach_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getContext(), R.string.msg_not_implemented, Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.attach_vcard).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectContactAttachment();
                hideAttachmentView();
            }
        });

        view.findViewById(R.id.attach_location).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectPositionAttachment();
                hideAttachmentView();
            }
        });
    }

    @Override
    public void onAttachClick() {
        toggleAttachmentView();
    }

    @Override
    public void onTextEntryFocus() {
        tryHideAttachmentView(true);
    }

    @Override
    public boolean canOpenEmoji() {
        if (SystemUtils.supportsMultiWindow() && getActivity().isInMultiWindowMode()) {
            Toast.makeText(getContext(), R.string.err_emoji_disabled_in_multiwindow,
                Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    /**
     * Sends out the text message in the composing entry.
     */
    @Override
    public void sendTextMessage(String message) {
        if (!TextUtils.isEmpty(message)) {
            offlineModeWarning();

            // start thread
            long inReplyTo = mReplyBar.getMessageId();
            new TextMessageThread(message, inReplyTo).start();
            if (inReplyTo > 0)
                mReplyBar.hide();
        }
    }

    /**
     * Sends out a binary message.
     */
    @Override
    public void sendBinaryMessage(Uri uri, String mime, boolean media, Class<? extends MessageComponent<?>> klass) {
        Log.v(TAG, "sending binary content: " + uri);
        offlineModeWarning();
        // start thread
        new BinaryMessageThread(uri, mime, media, klass).start();
    }

    /**
     * Sends out a location message.
     */
    @Override
    public void sendLocationMessage(String message, double lat, double lon, String geoText, String geoStreet) {
        offlineModeWarning();
        // start thread
        new LocationMessageThread(message, lat, lon, geoText, geoStreet).start();
    }

    private final class TextMessageThread extends Thread {
        private final String mText;
        private final long mInReplyTo;

        TextMessageThread(String text, long inReplyTo) {
            mText = text;
            mInReplyTo = inReplyTo;
        }

        @Override
        public void run() {
            final Activity context = getActivity();
            if (context == null)
                return;

            try {
                final Conversation conv = mConversation;
                Uri newMsg;
                try {
                    newMsg = Kontalk.get().getMessagesController()
                        .sendTextMessage(conv, mText, mInReplyTo).get();
                }
                catch (ExecutionException e) {
                    // unwrap exception
                    throw e.getCause();
                }

                // update thread id from the inserted message
                if (threadId <= 0) {
                    threadId = MessagesProviderClient.getThreadByMessage(context, newMsg);
                    if (threadId > 0) {
                        startQuery();
                    }
                    else {
                        Log.v(TAG, "no data - cannot start query for this composer");
                    }
                }
            }
            catch (SQLiteDiskIOException e) {
                Log.d(TAG, "error storing message", e);
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getActivity(), R.string.error_store_outbox,
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
            catch (Throwable e) {
                Log.d(TAG, "error storing message", e);
                ReportingManager.logException(e);
                context.runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getActivity(),
                            R.string.err_store_message_failed,
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    }

    private final class BinaryMessageThread extends Thread {
        private final Uri mUri;
        private final String mMime;
        private final boolean mMedia;
        private final Class<? extends MessageComponent<?>> mKlass;

        BinaryMessageThread(Uri uri, String mime, boolean media,
            Class<? extends MessageComponent<?>> klass) {
            mUri = uri;
            mMime = mime;
            mMedia = media;
            mKlass = klass;
        }

        @Override
        public void run() {
            final Activity context = getActivity();
            if (context == null)
                return;

            try {
                final Conversation conv = mConversation;
                Uri newMsg = Kontalk.get().getMessagesController()
                    .sendBinaryMessage(conv, mUri, mMime, mMedia, mKlass);

                // update thread id from the inserted message
                if (threadId <= 0) {
                    threadId = MessagesProviderClient.getThreadByMessage(context, newMsg);
                    if (threadId > 0) {
                        // we can run it here because progress=false
                        startQuery();
                    }
                    else {
                        Log.v(TAG, "no data - cannot start query for this composer");
                    }
                }
            }
            catch (SQLiteDiskIOException e) {
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getActivity(), R.string.error_store_outbox,
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
            catch (Exception e) {
                ReportingManager.logException(e);
                context.runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getActivity(),
                            R.string.err_store_message_failed,
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    }

    private final class LocationMessageThread extends Thread {
        private final String mText;
        private final double mLatitude;
        private final double mLongitude;
        private final String mGeoText;
        private final String mGeoStreet;

        LocationMessageThread(String text, double lat, double lon, String geoText, String geoStreet) {
            mText = text;
            mLatitude = lat;
            mLongitude = lon;
            mGeoText = geoText;
            mGeoStreet = geoStreet;
        }

        @Override
        public void run() {
            final Activity context = getActivity();
            if (context == null)
                return;

            try {
                final Conversation conv = mConversation;
                Uri newMsg = Kontalk.get().getMessagesController()
                    .sendLocationMessage(conv, mText, mLatitude, mLongitude, mGeoText, mGeoStreet);

                // update thread id from the inserted message
                if (threadId <= 0) {
                    threadId = MessagesProviderClient.getThreadByMessage(context, newMsg);
                    if (threadId > 0) {
                        startQuery();
                    }
                    else {
                        Log.v(TAG, "no data - cannot start query for this composer");
                    }
                }
            }
            catch (SQLiteDiskIOException e) {
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getActivity(), R.string.error_store_outbox,
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
            catch (Exception e) {
                ReportingManager.logException(e);
                context.runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getActivity(),
                            R.string.err_store_message_failed,
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    }

    /**
     * Sends an inactive chat state message.
     */
    public abstract boolean sendInactive();

    protected abstract void onInflateOptionsMenu(Menu menu, MenuInflater inflater);

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        onInflateOptionsMenu(menu, inflater);
        mDeleteThreadMenu = menu.findItem(R.id.delete_thread);
        mToggleEncryptionMenu = menu.findItem(R.id.toggle_encryption);
        updateUI();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // action mode is active - no processing
        if (isActionModeActive())
            return true;

        switch (item.getItemId()) {
            case R.id.delete_thread:
                if (threadId > 0)
                    deleteThread();
                return true;

            case R.id.invite_group:
                addUsers();
                return true;

            case R.id.toggle_encryption:
                toggleEncryption();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void toggleEncryption() {
        if (mConversation.isEncryptionEnabled()) {
            new MaterialDialog.Builder(getActivity())
                .title(R.string.title_disable_encryption)
                .content(R.string.msg_disable_encryption)
                .positiveText(R.string.menu_disable_encryption)
                .positiveColorRes(R.color.button_danger)
                .negativeText(android.R.string.cancel)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog materialDialog, @NonNull DialogAction dialogAction) {
                        setEncryption(false);
                        updateUI();
                    }
                })
                .show();
        }
        else {
            setEncryption(true);
            updateUI();
        }
    }

    void setEncryption(boolean encryption) {
        if (mConversation != null)
            mConversation.setEncryptionEnabled(encryption);
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        MessageListItem item = (MessageListItem) view;
        final CompositeMessage msg = item.getMessage();

        AttachmentComponent attachment = msg.getComponent(AttachmentComponent.class);

        LocationComponent location = msg.getComponent(LocationComponent.class);

        if (attachment != null && (attachment.getFetchUrl() != null || attachment.getLocalUri() != null)) {

            // outgoing message or already fetched
            if (attachment.getLocalUri() != null) {
                // open file
                openFile(msg);
            }
            else {
                // info & download dialog
                CharSequence message = MessageUtils
                    .getFileInfoMessage(getActivity(), msg, getDecodedPeer(msg));

                MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                    .title(R.string.title_file_info)
                    .content(message)
                    .negativeText(android.R.string.cancel)
                    .cancelable(true);

                if (!DownloadService.isQueued(attachment.getFetchUrl())) {
                    MaterialDialog.SingleButtonCallback startDL = new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            // start file download
                            startDownload(msg);
                        }
                    };
                    builder.positiveText(R.string.download)
                        .onPositive(startDL);
                }
                else {
                    MaterialDialog.SingleButtonCallback stopDL = new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            // cancel file download
                            stopDownload(msg);
                        }
                    };
                    builder.positiveText(R.string.download_cancel)
                        .onPositive(stopDL);
                }

                builder.show();
            }
        }

        else if (location != null) {
            String userId = item.getMessage().getSender();
            if (item.getMessage().getSender() == null)
                userId = Authenticator.getSelfJID(getContext());

            Intent intent = new Intent(getActivity(), PositionActivity.class);
            Position p = new Position(location.getLatitude(), location.getLongitude(),
                "", "");
            intent.putExtra(PositionActivity.EXTRA_USERPOSITION, p);
            intent.putExtra(PositionActivity.EXTRA_USERID, userId);
            startActivity(intent);
        }

        else {
            item.onClick();
        }
    }

    private void startDownload(CompositeMessage msg) {
        AttachmentComponent attachment = msg
            .getComponent(AttachmentComponent.class);

        if (attachment != null && attachment.getFetchUrl() != null) {
            DownloadService.start(getContext(), msg.getDatabaseId(),
                msg.getSender(), attachment.getMime(), msg.getTimestamp(),
                attachment.getSecurityFlags() != Coder.SECURITY_CLEARTEXT,
                attachment.getFetchUrl());
        }
        else {
            // corrupted message :(
            Toast.makeText(getActivity(), R.string.err_attachment_corrupted,
                Toast.LENGTH_LONG).show();
        }
    }

    private void stopDownload(CompositeMessage msg) {
        AttachmentComponent attachment = msg.getComponent(AttachmentComponent.class);

        if (attachment != null && attachment.getFetchUrl() != null) {
            DownloadService.abort(getContext(),
                Uri.parse(attachment.getFetchUrl()));
        }
    }

    private void openFile(CompositeMessage msg) {
        AttachmentComponent attachment = msg.getComponent(AttachmentComponent.class);

        if (attachment != null) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            Uri uri = MediaStorage.getWorldReadableUri(getContext(),
                attachment.getLocalUri(), i, true);
            i.setDataAndType(uri, attachment.getMime());
            try {
                startActivity(i);
            }
            catch (ActivityNotFoundException e) {
                Toast.makeText(getActivity(), R.string.chooser_error_no_app,
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    private void chooseContact() {
        // TODO one day it will be like this
        // Intent i = new Intent(Intent.ACTION_PICK, Users.CONTENT_URI);
        Intent i = new Intent(getContext(), ContactsListActivity.class);
        i.putExtra(ContactsListActivity.MODE_MULTI_SELECT, true);
        i.putExtra(ContactsListActivity.MODE_ADD_USERS, true);
        startActivityForResult(i, REQUEST_INVITE_USERS);
    }

    boolean tryHideAttachmentView() {
        return tryHideAttachmentView(false);
    }

    boolean tryHideAttachmentView(boolean instant) {
        if (isAttachmentViewVisible()) {
            mAttachmentContainer.hide();
            return true;
        }
        return false;
    }

    private boolean isAttachmentViewVisible() {
        return mAttachmentContainer.getVisibility() == View.VISIBLE;
    }

    void hideAttachmentView() {
        mAttachmentContainer.hide();
    }

    /**
     * Show or hide the attachment selector.
     */
    private void toggleAttachmentView() {
        mAttachmentContainer.toggle();
    }

    @AfterPermissionGranted(Permissions.RC_CAMERA)
    void startCameraAttachment() {
        final Context context = getContext();
        if (context == null)
            return;

        try {
            mCurrentPhoto = MediaStorage.getOutgoingPhotoFile();
            Uri uri = Uri.fromFile(mCurrentPhoto);

            final Intent intent = SystemUtils.externalIntent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                intent.setClipData(ClipData.newUri(context.getContentResolver(),
                    "Picture path", uri));
            }

            startActivityForResult(intent, SELECT_ATTACHMENT_PHOTO);
        }
        catch (IOException e) {
            Log.e(TAG, "error creating temp file", e);
            Toast.makeText(getActivity(), R.string.chooser_error_no_camera,
                Toast.LENGTH_LONG).show();
        }

    }

    private void requestCameraPermission() {
        if (!Permissions.canUseCamera(getContext())) {
            Permissions.requestCamera(this, getString(R.string.err_camera_picture_denied));
        }
        else {
            startCameraAttachment();
        }
    }

    /**
     * Starts an activity for shooting a picture.
     */
    void selectPhotoAttachment() {
        try {
            // check if camera is available
            final PackageManager packageManager = getActivity().getPackageManager();
            final Intent intent = SystemUtils.externalIntent(MediaStore.ACTION_IMAGE_CAPTURE);
            List<ResolveInfo> list =
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (list.size() <= 0) throw new UnsupportedOperationException();

            requestCameraPermission();
        }
        catch (UnsupportedOperationException ue) {
            Toast.makeText(getActivity(), R.string.chooser_error_no_camera_app,
                Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Starts an activity for picture attachment selection.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    void selectGalleryAttachment() {
        boolean useSAF = MediaStorage.isStorageAccessFrameworkAvailable();
        Intent pictureIntent = createGalleryIntent(useSAF);

        try {
            startActivityForResult(pictureIntent, SELECT_ATTACHMENT_OPENABLE);
        }
        catch (ActivityNotFoundException e1) {
            try {
                if (useSAF) {
                    // try direct file system access
                    pictureIntent = createGalleryIntent(false);
                    startActivityForResult(pictureIntent, SELECT_ATTACHMENT_OPENABLE);
                }
                else {
                    // simulate error
                    throw new ActivityNotFoundException("gallery");
                }
            }
            catch (ActivityNotFoundException e2) {
                Toast.makeText(getActivity(), R.string.chooser_error_no_gallery_app,
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private Intent createGalleryIntent(boolean useSAF) {
        Intent intent;
        if (!useSAF) {
            intent = SystemUtils.externalIntent(Intent.ACTION_GET_CONTENT)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        else {
            intent = SystemUtils.externalIntent(Intent.ACTION_OPEN_DOCUMENT);
        }

        return intent
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    }

    /**
     * Starts activity for a vCard attachment from a contact.
     */
    void selectContactAttachment() {
        try {
            Intent i = SystemUtils.externalIntent(Intent.ACTION_PICK, Contacts.CONTENT_URI);
            startActivityForResult(i, SELECT_ATTACHMENT_CONTACT);
        }
        catch (ActivityNotFoundException e) {
            // no contacts app found (crap device eh?)
            Toast.makeText(getActivity(),
                R.string.err_no_contacts_app,
                Toast.LENGTH_LONG).show();
        }
    }

    void selectAudioAttachment() {
        Activity context = getActivity();
        if (context != null) {
            // create audio fragment if needed
            AudioFragment audio = getAudioFragment();

            // stop everything
            if (mAudioControl != null) {
                resetAudio(mAudioControl);
            }
            else {
                audio.resetPlayer();
                audio.setMessageId(-1);
            }

            // show dialog
            mAudioDialog = new AudioDialog(context, audio, this);
            mAudioDialog.setOwnerActivity(context);
            mAudioDialog.show();
        }
    }

    void selectPositionAttachment() {
        startActivityForResult(new Intent(getContext(), PositionActivity.class), SELECT_ATTACHMENT_LOCATION);
    }

    private AudioFragment getAudioFragment() {
        FragmentManager fm = getFragmentManager();
        if (fm != null) {
            AudioFragment found = (AudioFragment) fm.findFragmentByTag("audio");
            if (found != null) {
                mAudioFragment = found;
            }
            else {
                mAudioFragment = new AudioFragment();
                fm.beginTransaction()
                    .add(mAudioFragment, "audio")
                    .commit();
            }
        }

        return mAudioFragment;
    }

    protected abstract void deleteConversation();

    private void deleteThread() {
        new MaterialDialog.Builder(getActivity())
            .content(R.string.confirm_will_delete_thread)
            .positiveText(android.R.string.ok)
            .positiveColorRes(R.color.button_danger)
            .negativeText(android.R.string.cancel)
            .onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    deleteConversation();
                }
            })
            .show();
    }

    void addUsers() {
        chooseContact();
    }

    protected abstract void addUsers(String[] members);

    private void replyMessage(CompositeMessage msg) {
        TextComponent textComponent = msg.getComponent(TextComponent.class);
        if (textComponent != null) {
            String sender = msg.getSender();
            Contact contact = Contact.findByUserId(getContext(), sender);

            mReplyBar.show(msg.getDatabaseId(), contact.getDisplayName(), textComponent.getContent());
        }
    }

    private void retryMessage(CompositeMessage msg) {
        Kontalk.get().getMessagesController()
            .retryMessage(msg.getDatabaseId(), mConversation.isEncryptionEnabled());
    }

    void scrollToPosition(int position) {
        getListView().setSelection(position);
    }

    private boolean isSearching() {
        Bundle args = getArguments();
        return args != null && args.getLong(ComposeMessage.EXTRA_MESSAGE, -1) >= 0;
    }

    protected synchronized void startQuery() {
        Conversation.startQuery(mQueryHandler,
            CONVERSATION_QUERY_TOKEN, threadId);
        // message list query will be started by query handler
    }

    void startMessagesQuery() {
        CompositeMessage.startQuery(mQueryHandler, MESSAGE_LIST_QUERY_TOKEN,
            threadId, isSearching() ? 0 : MESSAGE_PAGE_SIZE, 0);
    }

    void startMessagesQuery(long lastId) {
        CompositeMessage.startQuery(mQueryHandler, MESSAGE_PAGE_QUERY_TOKEN,
            threadId, isSearching() ? 0 : MESSAGE_PAGE_SIZE, lastId);
    }

    private void stopQuery() {
        hideHeaderView();
        if (mListAdapter != null)
            mListAdapter.changeCursor(null);

        if (mQueryHandler != null) {
            // be sure to cancel all queries
            mQueryHandler.abort();
        }
    }

    private void showMessageDetails(CompositeMessage msg) {
        MessageUtils.showMessageDetails(getActivity(), msg, getDecodedPeer(msg), getDecodedName(msg));
    }

    /**
     * Returns the phone number of the message sender, if available.
     */
    protected abstract String getDecodedPeer(CompositeMessage msg);

    /**
     * Returns the display name of the message sender, if available.
     */
    protected abstract String getDecodedName(CompositeMessage msg);

    // TODO abstract those intent creators
    private void shareMessage(CompositeMessage msg) {
        Intent i = null;
        AttachmentComponent attachment = msg.getComponent(AttachmentComponent.class);

        if (attachment != null) {
            i = ComposeMessage.sendMediaMessage(getContext(),
                attachment.getLocalUri(), attachment.getMime());
        }
        else if (msg.getComponent(TextComponent.class) != null) {
            TextComponent txt = msg.getComponent(TextComponent.class);
            i = ComposeMessage.sendTextMessage(txt.getContent());
        }
        else if (msg.getComponent(LocationComponent.class) != null) {
            LocationComponent location = msg.getComponent(LocationComponent.class);
            i = new Intent(android.content.Intent.ACTION_VIEW,
                Uri.parse("geo:" + location.getLatitude() + ","
                    + location.getLongitude() + "?q="
                    + location.getLatitude() + "," + location.getLongitude()));
        }

        if (i != null) {
            startActivity(i);
        }
        else {
            // TODO ehm...
            Log.w(TAG, "error sharing message");
        }
    }

    protected void loadConversationMetadata(Uri uri) {
        threadId = ContentUris.parseId(uri);
        mConversation = Conversation.loadFromId(getActivity(), threadId);
        if (mConversation == null) {
            Log.w(TAG, "conversation for thread " + threadId + " not found!");
            startActivity(new Intent(getActivity(), ConversationsActivity.class));
            getActivity().finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // image from storage/picture from camera
        // since there are like up to 3 different ways of doing this...
        if (requestCode == SELECT_ATTACHMENT_OPENABLE || requestCode == SELECT_ATTACHMENT_PHOTO) {
            if (resultCode == Activity.RESULT_OK) {
                Uri[] uris = null;
                String[] mimes = null;

                // returning from camera
                if (requestCode == SELECT_ATTACHMENT_PHOTO) {
                    if (mCurrentPhoto != null) {
                        Uri uri = Uri.fromFile(mCurrentPhoto);
                        // notify media scanner
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        mediaScanIntent.setData(uri);
                        getActivity().sendBroadcast(mediaScanIntent);
                        mCurrentPhoto = null;

                        uris = new Uri[]{uri};
                    }
                }
                else {
                    if (mCurrentPhoto != null) {
                        mCurrentPhoto.delete();
                        mCurrentPhoto = null;
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && data.getClipData() != null) {
                        ClipData cdata = data.getClipData();
                        uris = new Uri[cdata.getItemCount()];

                        for (int i = 0; i < uris.length; i++) {
                            ClipData.Item item = cdata.getItemAt(i);
                            uris[i] = item.getUri();
                        }
                    }
                    else {
                        uris = new Uri[]{data.getData()};
                        mimes = new String[]{data.getType()};
                    }

                    // SAF available, request persistable permissions
                    if (MediaStorage.isStorageAccessFrameworkAvailable()) {
                        for (Uri uri : uris) {
                            if (uri != null && !"file".equals(uri.getScheme())) {
                                try {
                                    MediaStorage.requestPersistablePermissions(getActivity(), uri);
                                }
                                catch (SecurityException e) {
                                    // we'll try to access the file anyway later
                                    Log.w(TAG, "unable to request persistable permissions - will try access anyway");
                                }
                            }
                        }
                    }
                }

                for (int i = 0; uris != null && i < uris.length; i++) {
                    Uri uri = uris[i];
                    if (uri == null)
                        continue;

                    String mime = (mimes != null && mimes.length >= uris.length) ?
                        mimes[i] : null;

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
        // contact card (vCard)
        else if (requestCode == SELECT_ATTACHMENT_CONTACT) {
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = data.getData();
                if (uri != null) {
                    Uri vcardUri = null;

                    // get lookup key
                    final Cursor c = getContext().getContentResolver()
                        .query(uri, new String[]{Contacts.LOOKUP_KEY}, null, null, null);
                    if (c != null) {
                        try {
                            if (c.moveToFirst()) {
                                String lookupKey = c.getString(0);
                                vcardUri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey);
                            }
                        }
                        catch (Exception e) {
                            Log.w(TAG, "unable to lookup selected contact. Did you grant me the permission?", e);
                            ReportingManager.logException(e);
                        }
                        finally {
                            c.close();
                        }
                    }

                    if (vcardUri != null) {
                        sendBinaryMessage(vcardUri, VCardComponent.MIME_TYPE, false, VCardComponent.class);
                    }
                    else {
                        Toast.makeText(getContext(), R.string.err_no_contact,
                            Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
        // user location
        else if (requestCode == SELECT_ATTACHMENT_LOCATION) {
            if (resultCode == Activity.RESULT_OK) {
                Position position = (Position) data.getSerializableExtra("position");
                String mapsUrl = PositionManager.getMapsUrl(getContext(), position.getLatitude(), position.getLongitude());
                sendLocationMessage(mapsUrl, position.getLatitude(), position.getLongitude(),
                    position.getName(), position.getAddress());
            }
        }
        // invite user
        else if (requestCode == REQUEST_INVITE_USERS) {
            if (resultCode == Activity.RESULT_OK) {

                ArrayList<Uri> uris;
                Uri threadUri = data.getData();
                if (threadUri != null) {
                    String userId = threadUri.getLastPathSegment();
                    addUsers(new String[]{userId});
                }
                else if ((uris = data.getParcelableArrayListExtra("org.kontalk.contacts")) != null) {
                    String[] users = new String[uris.size()];
                    for (int i = 0; i < users.length; i++)
                        users[i] = uris.get(i).getLastPathSegment();
                    addUsers(users);
                }

            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putParcelable(Uri.class.getName(), Threads.getUri(getUserId()));
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
        // audio player stuff
        out.putInt("mediaPlayerStatus", mMediaPlayerStatus);
    }

    /**
     * Handles ACTION_VIEW intents.
     */
    protected abstract void handleActionView(Uri uri);

    /**
     * Handles ACTION_VIEW_USERID intents: providing the user ID/JID.
     */
    protected abstract boolean handleActionViewConversation(Uri uri, Bundle args);

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

            // audio playing
            setAudioStatus(savedInstanceState.getInt("mediaPlayerStatus", AudioContentView.STATUS_IDLE));

            // audio dialog stuff
            mAudioDialog = AudioDialog.onRestoreInstanceState(getActivity(),
                savedInstanceState, getAudioFragment(), this);
            if (mAudioDialog != null) {
                Log.d(TAG, "recreating audio dialog");
                mAudioDialog.show();
            }
        }
        else {
            args = getArguments();
        }

        if (args != null && args.size() > 0) {
            final String action = args.getString("action");

            // view intent
            if (Intent.ACTION_VIEW.equals(action)) {
                Uri uri = args.getParcelable("data");
                handleActionView(uri);
            }

            // view conversation - just threadId provided
            else if (ComposeMessage.ACTION_VIEW_CONVERSATION.equals(action)) {
                Uri uri = args.getParcelable("data");
                loadConversationMetadata(uri);
            }

            // view conversation - just userId provided
            else if (ComposeMessage.ACTION_VIEW_USERID.equals(action)) {
                Uri uri = args.getParcelable("data");
                if (!handleActionViewConversation(uri, args)) {
                    getActivity().finish();
                    return;
                }
            }
        }

        // set title if we are autonomous
        if (args != null) {
            String title = mUserName;
            //if (mUserPhone != null) title += " <" + mUserPhone + ">";
            setActivityTitle(title, "");
        }

        // update conversation stuff
        if (mConversation != null)
            onConversationCreated();

        onArgumentsProcessed();
    }

    protected abstract void onArgumentsProcessed();

    public void setActivityTitle(@Nullable CharSequence title, @Nullable CharSequence status) {
        if (mStatusText != null) {
            // tablet UI - ignore title
            mStatusText.setText(status);
        }
        else {
            ComposeMessageParent parent = (ComposeMessageParent) getActivity();
            parent.setTitle(title, status);
        }
    }

    public void setActivityStatusUpdating() {
        if (mStatusText != null) {
            CharSequence text = mStatusText.getText();
            if (text != null && text.length() > 0) {
                mStatusText.setText(ComposeMessage.applyUpdatingStyle(text));
            }
        }
        else {
            ComposeMessageParent parent = (ComposeMessageParent) getActivity();
            parent.setUpdatingSubtitle();
        }
    }

    public ComposeMessage getParentActivity() {
        Activity _activity = getActivity();
        return (_activity instanceof ComposeMessage) ? (ComposeMessage) _activity
            : null;
    }

    void processStart() {
        ComposeMessage activity = getParentActivity();
        // opening for contact picker - do nothing
        if (threadId < 0 && activity != null
            && activity.getSendIntent() != null)
            return;

        if (mListAdapter == null) {
            Pattern highlight = null;
            Bundle args = getArguments();
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
            startQuery();
        }
        else {
            mConversation = Conversation.createNew(getActivity());
            mConversation.setRecipient(getUserId());
            onConversationCreated();
        }
    }

    /**
     * Called when the {@link Conversation} object has been created.
     */
    protected void onConversationCreated() {
        // restore any draft
        mComposer.restoreText(mConversation.getDraft());

        if (mConversation.getThreadId() > 0) {
            if (mConversation.getUnreadCount() > 0) {
                /*
                 * FIXME this has the usual issue about resuming while screen is
                 * still locked, having focus and so on...
                 * See issue #28.
                 */
                Log.v(TAG, "marking thread as read");
                mConversation.markAsRead();
            }
        }
        else {
            // new conversation -- observe peer Uri
            registerPeerObserver();
        }

        // subscribe to presence notifications
        subscribePresence();

        updateUI();
    }

    /**
     * Called when a presence is received.
     */
    protected abstract void onPresence(String jid, Presence.Type type,
        boolean removed, Presence.Mode mode, String fingerprint);

    protected abstract void onConnected();

    protected abstract void onDisconnected();

    /**
     * Called when the roster has been loaded (ACTION_ROSTER).
     */
    protected abstract void onRosterLoaded();

    /**
     * Called when the contact starts typing.
     */
    protected abstract void onStartTyping(String jid, String groupJid);

    /**
     * Called when the contact stops typing.
     */
    protected abstract void onStopTyping(String jid, String groupJid);

    /**
     * Should return true if the contact is a user ID in the current context.
     */
    protected abstract boolean isUserId(String jid);

    class PresenceReceiver extends BroadcastReceiver implements
            ConnectionLifecycleListener,
            PresenceListener {
        public void onReceive(Context context, Intent intent) {
            // activity is terminating
            if (getContext() == null)
                return;

            String action = intent.getAction();

            if (MessageCenterService.ACTION_MESSAGE.equals(action)) {
                String from = intent.getStringExtra(MessageCenterService.EXTRA_FROM);
                String chatState = intent.getStringExtra("org.kontalk.message.chatState");

                // we are receiving a composing notification from our peer
                if (from != null && isUserId(from)) {
                    String groupJid = intent.getStringExtra(MessageCenterService.EXTRA_GROUP_JID);

                    if (chatState != null && ChatState.composing.toString().equals(chatState)) {
                        onStartTyping(from, groupJid);
                    }
                    else {
                        onStopTyping(from, groupJid);
                    }
                }
            }
        }

        @Override
        public void onConnected() {
            // reset compose sent flag
            mComposer.resetCompose();
            // reset available resources list
            mAvailableResources.clear();

            mConnected = true;
            AbstractComposeFragment.this.onConnected();
        }

        @Override
        public void onDisconnected() {
            mConnected = false;
            AbstractComposeFragment.this.onDisconnected();
        }

        @Override
        public void onRosterLoaded() {
            AbstractComposeFragment.this.onRosterLoaded();
        }

        @Override
        public void onPresence(Jid from,
            Presence.Type type, Presence.Mode mode, int priority,
            String status, Date delay,
            String rosterName, boolean subscribedFrom, boolean subscribedTo,
            String fingerprint) {

            // since this listener is used also for global presence (for groups),
            // check that the origin is among group members
            if (mConversation.isGroupChat() && !isUserId(from.toString())) {
                // not for us
                return;
            }

            boolean removed = false;
            if (type == Presence.Type.available) {
                mAvailableResources.add(from.toString());
            }
            else if (type == Presence.Type.unavailable) {
                removed = mAvailableResources.remove(from.toString());
            }

            AbstractComposeFragment.this.onPresence(from.toString(), type, removed, mode, fingerprint);
        }
    }

    private void subscribePresence() {
        if (mPresenceReceiver == null) {
            mPresenceReceiver = new PresenceReceiver();

            // listen for user presence, connection and incoming messages
            IntentFilter filter = new IntentFilter();
            filter.addAction(MessageCenterService.ACTION_MESSAGE);

            mLocalBroadcastManager.registerReceiver(mPresenceReceiver, filter);

            Context ctx = getContext();
            MessageCenterClient msgc = MessageCenterClient.getInstance(ctx);
            msgc.addConnectionLifecycleListener(mPresenceReceiver);

            if (mConversation.isGroupChat()) {
                // we will filter out unwanted presences.
                // It may be inelegant, but this way we don't have to change our
                // subscription when group members change
                msgc.addGlobalPresenceListener(mPresenceReceiver);
            }
            else {
                msgc.addPresenceListener(mPresenceReceiver, getUserId());
            }

            // request connection and roster load status
            if (ctx != null) {
                MessageCenterService.requestConnectionStatus(ctx);
                MessageCenterService.requestRosterStatus(ctx);
            }
        }
    }

    private void unsubscribePresence() {
        if (mPresenceReceiver != null) {
            mLocalBroadcastManager.unregisterReceiver(mPresenceReceiver);
            MessageCenterClient.getInstance(getContext())
                .removeConnectionLifecycleListener(mPresenceReceiver)
                .removePresenceListener(mPresenceReceiver, getUserId())
                .removeGlobalPresenceListener(mPresenceReceiver);
            mPresenceReceiver = null;
        }
    }

    protected boolean isWarningVisible(WarningType type) {
        Snackbar bar = SnackbarManager.getCurrentSnackbar();
        if (bar != null) {
            WarningType oldType = (WarningType) bar.getTag();
            if (oldType != null && oldType == type)
                return true;
        }
        return false;
    }

    /** Hides the warning bar. */
    protected void hideWarning() {
        SnackbarManager.dismiss();
    }

    /** Hides the warning bar only if type matches. */
    protected void hideWarning(WarningType type) {
        Snackbar bar = SnackbarManager.getCurrentSnackbar();
        if (bar != null) {
            WarningType oldType = (WarningType) bar.getTag();
            if (oldType == type)
                bar.dismiss();
        }
    }

    protected void showWarning(CharSequence text, final View.OnClickListener listener, WarningType type) {
        View view = getView();
        Activity context = getActivity();
        if (view == null || context == null)
            return;

        Snackbar bar = SnackbarManager.getCurrentSnackbar();
        if (bar != null) {
            WarningType oldType = (WarningType) bar.getTag();
            if (oldType != null && oldType.getValue() > type.getValue())
                return;

            bar.dismiss();
        }

        bar = Snackbar.with(context)
            .type(SnackbarType.MULTI_LINE)
            .text(text)
            .duration(Snackbar.SnackbarDuration.LENGTH_INDEFINITE)
            .dismissOnActionClicked(false)
            .allowMultipleActionClicks(true);

        if (listener != null) {
            bar.swipeToDismiss(false)
                .actionLabel(R.string.warning_button_details)
                .actionListener(new ActionClickListener() {
                    @Override
                    public void onActionClicked(Snackbar snackbar) {
                        listener.onClick(null);
                    }
                });
        }
        else {
            bar.swipeToDismiss(true)
                .animation(false);
        }

        int colorId = 0;
        int textColorId = 0;
        switch (type) {
            case FATAL:
                textColorId = R.color.warning_bar_text_fatal;
                colorId = R.color.warning_bar_background_fatal;
                break;
            case WARNING:
                textColorId = R.color.warning_bar_text_warning;
                colorId = R.color.warning_bar_background_warning;
                break;
        }

        bar.setTag(type);
        bar.color(ContextCompat.getColor(context, colorId))
            .textColor(ContextCompat.getColor(context, textColorId));

        if (listener != null) {
            SnackbarManager.show(bar);
        }
        else {
            SnackbarManager.show(bar, (ViewGroup) view.findViewById(R.id.warning_bar));
        }
    }

    protected void setStatusText(CharSequence text) {
        ComposeMessageParent parent = (ComposeMessageParent) getActivity();
        if (parent instanceof ComposeMessage)
            setActivityTitle(null, text);
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

    synchronized void unregisterPeerObserver() {
        if (mPeerObserver != null) {
            Context context = mPeerObserver.mContext;
            context.getContentResolver().unregisterContentObserver(mPeerObserver);
            mPeerObserver = null;
        }
    }

    private final class PeerObserver extends ContentObserver {
        final Context mContext;

        PeerObserver(Context context, Handler handler) {
            super(handler);
            mContext = context;
        }

        @Override
        public void onChange(boolean selfChange) {
            Conversation conv = Conversation.loadFromUserId(mContext, getUserId());

            if (conv != null) {
                mConversation = conv;
                threadId = mConversation.getThreadId();

                // auto-unregister
                unregisterPeerObserver();
            }

            // fire cursor update
            Log.v(TAG, "peer observer active");
            processStart();
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
        MessageCenterService.hold(getActivity(), true);

        ComposeMessage activity = getParentActivity();
        if (activity == null || !activity.hasLostFocus() || activity.hasWindowFocus()) {
            onFocus();
        }
    }

    public void onFocus() {
        // resume content watcher
        resumeContentListener();

        // set notifications on pause
        MessagingNotification.setPaused(getUserId());

        // we are updating the status now
        setActivityStatusUpdating();

        // cursor was previously destroyed -- reload everything
        processStart();
    }

    @Override
    public void onPause() {
        super.onPause();

        // unsubcribe presence notifications
        unsubscribePresence();

        // notify composer bar
        mComposer.onPause();

        // hide attachment view
        tryHideAttachmentView(true);

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
                mConversation.getRequestStatus() != Threads.REQUEST_WAITING &&
                !mConversation.isGroupChat()) {

                mConversation.delete(false);
            }

            // update draft
            else {
                try {
                    MessagesProviderClient.updateDraft(getContext(), threadId, text.toString());
                }
                catch (SQLiteDiskIOException e) {
                    // TODO warn user
                    Log.w(TAG, "error saving draft", e);
                    len = 0;
                }
            }
        }

        // new thread, create empty conversation
        else {
            if (len > 0) {
                // save to local storage
                try {
                    MessagesProviderClient.insertEmptyThread(getActivity(), getUserId(), text.toString());
                }
                catch (SQLiteDiskIOException e) {
                    // TODO warn user
                    Log.w(TAG, "error saving draft", e);
                    len = 0;
                }
            }
        }

        if (len > 0) {
            Toast.makeText(getActivity(), R.string.msg_draft_saved,
                Toast.LENGTH_LONG).show();
        }

        if (mComposer.isComposeSent()) {
            // send inactive state notification
            sendInactive();
            mComposer.resetCompose();
        }

        // release message center
        MessageCenterService.release(getActivity());

        // release audio player
        AudioFragment audio = getAudioFragment();
        if (audio != null) {
            stopMediaPlayerUpdater();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                if (!getActivity().isChangingConfigurations()) {
                    audio.setMessageId(-1);
                    audio.finish(true);
                }
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        unregisterPeerObserver();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopQuery();
        if (mComposer != null) {
            mComposer.onDestroy();
        }
        if (mAudioDialog != null) {
            mAudioDialog.dismiss();
            mAudioDialog = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
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

    void showHeaderView() {
        mHeaderView.setVisibility(View.VISIBLE);
    }

    void hideHeaderView() {
        mHeaderView.setVisibility(View.GONE);
    }

    void enableHeaderView(boolean enabled) {
        mNextPageButton.setEnabled(enabled);
    }

    protected void updateUI() {
        boolean threadEnabled = (threadId > 0);

        if (mDeleteThreadMenu != null) {
            mDeleteThreadMenu.setEnabled(threadEnabled);
        }

        Context context = getActivity();
        if (context != null) {
            if (mConversation != null && Preferences.getEncryptionEnabled(context)) {
                boolean encryption = mConversation.isEncryptionEnabled();
                if (mToggleEncryptionMenu != null) {
                    mToggleEncryptionMenu
                        .setVisible(true)
                        .setEnabled(true)
                        .setChecked(encryption);
                }
                if (mBackground != null) {
                    if (encryption) {
                        mBackground.clearColorFilter();
                    }
                    else {
                        mBackground.setColorFilter(ContextCompat
                            .getColor(context, R.color.app_background_unsafe_filter));
                    }
                }
            }
            else {
                if (mToggleEncryptionMenu != null) {
                    mToggleEncryptionMenu
                        .setVisible(false)
                        .setEnabled(false)
                        .setChecked(false);
                }
                if (mBackground != null) {
                    mBackground.setColorFilter(ContextCompat
                        .getColor(context, R.color.app_background_unsafe_filter));
                }
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

    public Conversation getConversation() {
        return mConversation;
    }

    public Contact getContact() {
        return (mConversation != null) ? mConversation.getContact() : null;
    }

    public long getThreadId() {
        return threadId;
    }

    protected void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    /**
     * Returns the user id of this conversation.
     */
    public abstract String getUserId();

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
            ConversationsActivity activity = (ConversationsActivity) getActivity();
            activity.getListFragment().endConversation(this);
        }
    }

    private void offlineModeWarning() {
        if (Preferences.getOfflineMode() && !mOfflineModeWarned) {
            mOfflineModeWarned = true;
            Toast.makeText(getActivity(), R.string.warning_offline_mode,
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void textChanged(CharSequence text) {
        Snackbar bar = SnackbarManager.getCurrentSnackbar();
        if (bar != null) {
            WarningType type = (WarningType) bar.getTag();
            if (type != null && type.getValue() < WarningType.FATAL.getValue()) {
                bar.dismiss();
            }
        }
    }

    @Override
    public void onRecordingSuccessful(File file) {
        if (file != null)
            sendBinaryMessage(Uri.fromFile(file), AudioDialog.DEFAULT_MIME, false, AudioComponent.class);
    }

    @Override
    public void onRecordingCancel() {
        mAudioDialog = null;
    }

    @Override
    public void buttonClick(File audioFile, AudioContentViewControl view, long messageId) {
        AudioFragment audio = getAudioFragment();
        if (audio.getMessageId() == messageId) {
            switch (mMediaPlayerStatus) {
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
            switch (mMediaPlayerStatus) {
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
        stopMediaPlayerUpdater();
        try {
            AudioFragment audio = getAudioFragment();
            audio.preparePlayer(audioFile);

            // prepare was successful
            audio.setMessageId(messageId);
            mAudioControl = view;

            view.prepare(audio.getPlayerDuration());
            audio.seekPlayerTo(view.getPosition());
            view.setProgressChangeListener(true);
            audio.setListener(new AudioFragment.AudioFragmentListener() {
                @Override
                public void onCompletion(AudioFragment audio) {
                    stopMediaPlayerUpdater();
                    view.end();
                    // this is mainly to get the wake lock released
                    audio.pausePlaying();
                    audio.seekPlayerTo(0);
                    setAudioStatus(AudioContentView.STATUS_ENDED);
                }

                @Override
                public void onAudioFocusLost(AudioFragment audio) {
                    stopAllSounds();
                }

                @Override
                public void onPause(AudioFragment audio) {
                    view.pause();
                    stopMediaPlayerUpdater();
                    setAudioStatus(AudioContentView.STATUS_PAUSED);
                }

                @Override
                public void onError(AudioFragment audio) {
                    stopMediaPlayerUpdater();
                    view.end();
                    setAudioStatus(AudioContentView.STATUS_ENDED);
                    Toast.makeText(getContext(), R.string.err_playing_audio, Toast.LENGTH_LONG)
                        .show();
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
        if (getAudioFragment().startPlaying()) {
            view.play();
            setAudioStatus(AudioContentView.STATUS_PLAYING);
            startMediaPlayerUpdater(view);
        }
    }

    private void updatePosition(AudioContentViewControl view) {
        // we don't use getElapsedTime() here because it might get moved by seeking
        view.updatePosition(getAudioFragment().getPlayerPosition());
    }

    @Override
    public void pauseAudio(AudioContentViewControl view) {
        view.pause();
        getAudioFragment().pausePlaying();
        stopMediaPlayerUpdater();
        setAudioStatus(AudioContentView.STATUS_PAUSED);
    }

    private void resetAudio(AudioContentViewControl view) {
        if (view != null) {
            stopMediaPlayerUpdater();
            view.end();
        }
        AudioFragment audio = getAudioFragment();
        if (audio != null) {
            audio.resetPlayer();
            audio.setMessageId(-1);
        }
    }

    private void setAudioStatus(int audioStatus) {
        mMediaPlayerStatus = audioStatus;
    }

    @Override
    public void stopAllSounds() {
        resetAudio(mAudioControl);
    }

    @Override
    public void onBind(long messageId, final AudioContentViewControl view) {
        final AudioFragment audio = getAudioFragment();
        if (audio != null && audio.getMessageId() == messageId) {
            mAudioControl = view;
            audio.setListener(new AudioFragment.AudioFragmentListener() {
                @Override
                public void onCompletion(AudioFragment audio) {
                    stopMediaPlayerUpdater();
                    view.end();
                    audio.seekPlayerTo(0);
                    setAudioStatus(AudioContentView.STATUS_ENDED);
                }

                @Override
                public void onAudioFocusLost(AudioFragment audio) {
                    stopAllSounds();
                }

                @Override
                public void onPause(AudioFragment audio) {
                    view.pause();
                    stopMediaPlayerUpdater();
                    setAudioStatus(AudioContentView.STATUS_PAUSED);
                }

                @Override
                public void onError(AudioFragment audio) {
                    stopMediaPlayerUpdater();
                    view.end();
                    setAudioStatus(AudioContentView.STATUS_ENDED);
                    Toast.makeText(getContext(), R.string.err_playing_audio, Toast.LENGTH_LONG)
                        .show();
                }
            });

            view.setProgressChangeListener(true);
            view.prepare(audio.getPlayerDuration());
            if (audio.isPlaying()) {
                startMediaPlayerUpdater(view);
                view.play();
            }
            else {
                view.pause();
            }
        }
    }

    @Override
    public void onUnbind(long messageId, AudioContentViewControl view) {
        AudioFragment audio = getAudioFragment();
        if (audio != null && audio.getMessageId() == messageId) {
            mAudioControl = null;
            audio.setListener(new AudioFragment.AudioFragmentListener() {
                @Override
                public void onCompletion(AudioFragment audio) {
                    audio.seekPlayerTo(0);
                    setAudioStatus(AudioContentView.STATUS_ENDED);
                }

                @Override
                public void onAudioFocusLost(AudioFragment audio) {
                    stopAllSounds();
                }

                @Override
                public void onPause(AudioFragment audio) {
                    setAudioStatus(AudioContentView.STATUS_PAUSED);
                }

                @Override
                public void onError(AudioFragment audio) {
                    setAudioStatus(AudioContentView.STATUS_ENDED);
                }
            });

            view.setProgressChangeListener(false);
            if (!MessagesProviderClient.exists(getActivity(), messageId)) {
                resetAudio(view);
            }

            else {
                stopMediaPlayerUpdater();
            }
        }
    }

    @Override
    public boolean isPlaying() {
        AudioFragment audio = getAudioFragment();
        return audio != null && audio.isPlaying();
    }

    @Override
    public void seekTo(int position) {
        AudioFragment audio = getAudioFragment();
        if (audio != null)
            audio.seekPlayerTo(position);
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

    /**
     * The conversation list query handler.
     */
    private static final class MessageListQueryHandler extends AsyncQueryHandler {
        private WeakReference<AbstractComposeFragment> mParent;
        private boolean mCancel;
        private long mLastId;

        MessageListQueryHandler(AbstractComposeFragment parent) {
            super(parent.getActivity().getApplicationContext().getContentResolver());
            mParent = new WeakReference<>(parent);
        }

        @Override
        public synchronized void startQuery(int token, Object cookie, Uri uri, String[] projection, String selection, String[] selectionArgs, String orderBy) {
            mCancel = false;
            super.startQuery(token, cookie, uri, projection, selection, selectionArgs, orderBy);
        }

        @Override
        protected synchronized void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final AbstractComposeFragment parent = mParent.get();
            if (parent == null || cursor == null || parent.isFinishing() || mCancel) {
                // close cursor - if any
                if (cursor != null)
                    cursor.close();

                mCancel = false;
                if (parent != null) {
                    parent.unregisterPeerObserver();
                    parent.mListAdapter.changeCursor(null);
                }
                return;
            }

            switch (token) {
                case MESSAGE_LIST_QUERY_TOKEN:

                    // no messages to show - exit
                    if (cursor.getCount() == 0
                        && (parent.mConversation == null ||
                        // no draft
                        (parent.mConversation.getDraft() == null &&
                            // no subscription request
                            parent.mConversation.getRequestStatus() != Threads.REQUEST_WAITING &&
                            // no text in compose entry
                            parent.mComposer.getText().length() == 0 &&
                            // no group chat
                            !parent.mConversation.isGroupChat()))) {

                        Log.i(TAG, "no data to view - exit");

                        // close conversation
                        parent.closeConversation();

                    }
                    else {
                        // first query - use last id of this new cursor
                        if (cursor.getCount() > 0) {
                            cursor.moveToFirst();
                            mLastId = Conversation.getMessageId(cursor);
                        }

                        // save reloading status for next time
                        Bundle args = parent.getArguments();

                        // see if we have to scroll to a specific message
                        int newSelectionPos = -1;

                        if (args != null && !args.getBoolean(ComposeMessage.EXTRA_RELOADING)) {
                            long msgId = args.getLong(ComposeMessage.EXTRA_MESSAGE, -1);
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

                            args.putBoolean(ComposeMessage.EXTRA_RELOADING, true);
                        }

                        parent.mListAdapter.changeCursor(cursor);
                        if (newSelectionPos >= 0) {
                            // +1 is for the header view
                            final int pos = newSelectionPos + 1;
                            parent.getListView().post(new Runnable() {
                                @Override
                                public void run() {
                                    parent.scrollToPosition(pos);
                                }
                            });
                        }

                        if (newSelectionPos < 0 && cursor.getCount() >= MESSAGE_PAGE_SIZE)
                            parent.showHeaderView();

                        parent.updateUI();
                    }

                    break;

                case MESSAGE_PAGE_QUERY_TOKEN:
                    if (cursor.getCount() > 0) {
                        int newSelectionPos = -1;

                        // there is no more data after this page
                        if (cursor.getCount() < MESSAGE_PAGE_SIZE)
                            parent.hideHeaderView();

                        // save last id of this new cursor
                        cursor.moveToFirst();
                        mLastId = Conversation.getMessageId(cursor);

                        // join with the old cursor (if any)
                        Cursor oldCursor = parent.mListAdapter.getCursor();
                        if (oldCursor != null) {
                            // the new selection will be the next item after this new cursor
                            newSelectionPos = cursor.getCount();
                            cursor = new MergeCursor(new Cursor[]{cursor, oldCursor});
                        }

                        parent.mListAdapter.swapCursor(cursor);
                        if (newSelectionPos >= 0)
                            parent.getListView().setSelection(newSelectionPos);

                        parent.updateUI();
                    }
                    else {
                        // this happens when the first page is exactly PAGE_SIZE big
                        parent.hideHeaderView();
                    }

                    parent.enableHeaderView(true);
                    break;

                case CONVERSATION_QUERY_TOKEN:
                    if (cursor.moveToFirst()) {
                        parent.mConversation = Conversation.createFromCursor(
                            parent.getActivity(), cursor);
                        parent.onConversationCreated();
                    }

                    cursor.close();

                    parent.startMessagesQuery();
                    break;

                default:
                    Log.e(TAG, "onQueryComplete called with unknown token " + token);
            }

        }

        public synchronized void abort() {
            mCancel = true;
            mLastId = 0;
            cancelOperation(MESSAGE_LIST_QUERY_TOKEN);
            cancelOperation(CONVERSATION_QUERY_TOKEN);
            cancelOperation(MESSAGE_PAGE_QUERY_TOKEN);
        }

        public long getLastId() {
            return mLastId;
        }

    }

}
