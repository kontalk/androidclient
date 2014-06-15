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

import static android.content.res.Configuration.KEYBOARDHIDDEN_NO;
import static org.kontalk.service.MessageCenterService.PRIVACY_ACCEPT;
import static org.kontalk.service.MessageCenterService.PRIVACY_BLOCK;
import static org.kontalk.service.MessageCenterService.PRIVACY_UNBLOCK;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.EndpointServer;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PGP;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.message.AttachmentComponent;
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
import org.kontalk.service.MessageCenterService;
import org.kontalk.sync.Syncer;
import org.kontalk.ui.IconContextMenu.IconContextMenuOnClickListener;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.MessageUtils.SmileyImageSpan;
import org.kontalk.util.Preferences;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;

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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.Contacts;
import android.provider.MediaStore;
import android.support.v4.app.ListFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;


/**
 * The composer fragment.
 * @author Daniele Ricci
 */
public class ComposeMessageFragment extends ListFragment implements
		View.OnLongClickListener, IconContextMenuOnClickListener {
	private static final String TAG = ComposeMessageFragment.class
			.getSimpleName();

	private static final int MESSAGE_LIST_QUERY_TOKEN = 8720;
	private static final int CONVERSATION_QUERY_TOKEN = 8721;

	private static final int SELECT_ATTACHMENT_OPENABLE = Activity.RESULT_FIRST_USER + 1;
	private static final int SELECT_ATTACHMENT_CONTACT = Activity.RESULT_FIRST_USER + 2;

    /** Context menu group ID for this fragment. */
    private static final int CONTEXT_MENU_GROUP_ID = 2;

    /* Attachment chooser stuff. */
    private static final int CONTEXT_MENU_ATTACHMENT = 1;
    private static final int ATTACHMENT_ACTION_PICTURE = 1;
    private static final int ATTACHMENT_ACTION_CONTACT = 2;
    private IconContextMenu attachmentMenu;

	private MessageListQueryHandler mQueryHandler;
	private MessageListAdapter mListAdapter;
	private EditText mTextEntry;
	private View mSendButton;
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
	private String userId;
	private String userName;
	private String userPhone;

	/** Presence probe packet id. */
	private String mPresenceId;
	/** Last most available stanza. */
	private PresenceData mMostAvailable;
	/** Available resources. */
	private Set<String> mAvailableResources = new HashSet<String>();

	private PeerObserver mPeerObserver;
    private File mCurrentPhoto;

	private LocalBroadcastManager mLocalBroadcastManager;
    private BroadcastReceiver mPresenceReceiver;
    private BroadcastReceiver mPrivacyListener;

    private QuickAction mSmileyPopup;
    private boolean mOfflineModeWarned;
    private boolean mComposeSent;
    private boolean mIsTyping;
    private CharSequence mCurrentStatus;
    private TextWatcher mChatStateListener;
    private AdapterView.OnItemClickListener mSmileySelectListener;

    private static final class PresenceData {
        public String status;
        public int priority;
        public Date stamp;
    }

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

		// set custom background (if any)
		Drawable bg = Preferences.getConversationBackground(getActivity());
		if (bg != null) {
		    list.setCacheColorHint(Color.TRANSPARENT);
		    list.setBackgroundDrawable(bg);
		}

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
			    // convert smiley codes
			    mTextEntry.removeTextChangedListener(this);
                MessageUtils.convertSmileys(getActivity(), s, SmileyImageSpan.SIZE_EDITABLE);
                mTextEntry.addTextChangedListener(this);

                // enable the send button if there is something to send
                mSendButton.setEnabled(s.length() > 0);
			}
		});
		mTextEntry.setOnEditorActionListener(new OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    InputMethodManager imm = (InputMethodManager) getActivity()
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getApplicationWindowToken(), 0);
                    submitSend();
                    return true;
                }
                return false;
            }
        });
		mChatStateListener = new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (Preferences.getSendTyping(getActivity())) {
                    // send typing notification if necessary
                    if (!mComposeSent && mAvailableResources.size() > 0) {
                        MessageCenterService.sendChatState(getActivity(), userId, ChatState.composing);
                        mComposeSent = true;
                    }
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

		mSendButton = getView().findViewById(R.id.send_button);
		mSendButton.setEnabled(mTextEntry.length() > 0);
		mSendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
			    submitSend();
			}
		});

        mSmileySelectListener = new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Editable text = mTextEntry.getText();
                int startPos = mTextEntry.getSelectionStart();
                int endPos = mTextEntry.getSelectionEnd();

                if (startPos < 0) startPos = text.length();
                if (endPos < 0) endPos = startPos;
                int startMin = Math.min(startPos, endPos);

                // add unicode emoji
                char[] value = Character.toChars((int) id);
                text.replace(startMin, Math.max(startPos, endPos),
                    String.valueOf(value), 0, value.length);

                // textview change listener will do the rest

                // dismiss smileys popup
                // TEST mSmileyPopup.dismiss();
            }
        };

		ImageButton smileyButton = (ImageButton) getView().findViewById(R.id.smiley_button);
        smileyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSmileysPopup(v);
            }
        });

		Configuration config = getResources().getConfiguration();
		onKeyboardStateChanged(config.keyboardHidden == KEYBOARDHIDDEN_NO);

		mLocalBroadcastManager = LocalBroadcastManager.getInstance(getActivity());

		processArguments(savedInstanceState);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		onKeyboardStateChanged(newConfig.keyboardHidden == KEYBOARDHIDDEN_NO);
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

		// list adapter creation is post-poned
	}

    private void submitSend() {
        mTextEntry.removeTextChangedListener(mChatStateListener);
        // send message
        sendTextMessage(null, true);
        // reset compose sent flag
        mComposeSent = false;
        mTextEntry.addTextChangedListener(mChatStateListener);
    }

	/** Sends out a binary message. */
	public void sendBinaryMessage(Uri uri, String mime, boolean media,
			Class<? extends MessageComponent<?>> klass) {
		Log.v(TAG, "sending binary content: " + uri);
		Uri newMsg = null;
        File previewFile = null;
        long length = -1;

		try {
		    // TODO convert to thread (?)

		    offlineModeWarning();

			String msgId = "draft" + (new Random().nextInt());

			// generate thumbnail
			// FIXME this is blocking!!!!
			if (media) {
				// FIXME hard-coded to ImageComponent
				String filename = ImageComponent.buildMediaFilename(msgId, MediaStorage.THUMBNAIL_MIME);
				previewFile = MediaStorage.cacheThumbnail(getActivity(), uri,
						filename);
			}

			length = MediaStorage.getLength(getActivity(), uri);

			// save to database
            ContentValues values = new ContentValues();
			// must supply a message ID...
			values.put(Messages.MESSAGE_ID, msgId);
			values.put(Messages.PEER, userId);

			/* TODO ask for a text to send with the image
			values.put(Messages.BODY_MIME, TextComponent.MIME_TYPE);
			values.put(Messages.BODY_CONTENT, content.getBytes());
			values.put(Messages.BODY_LENGTH, content.length());
			 */

			values.put(Messages.UNREAD, false);
			values.put(Messages.ENCRYPTED, false);
			values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
			values.put(Messages.TIMESTAMP, System.currentTimeMillis());
			values.put(Messages.STATUS, Messages.STATUS_SENDING);

			if (previewFile != null)
				values.put(Messages.ATTACHMENT_PREVIEW_PATH, previewFile.getAbsolutePath());

			values.put(Messages.ATTACHMENT_MIME, mime);
			values.put(Messages.ATTACHMENT_LOCAL_URI, uri.toString());
			values.put(Messages.ATTACHMENT_LENGTH, length);

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
			// FIXME do not encrypt binary messages for now
			String previewPath = (previewFile != null) ? previewFile.getAbsolutePath() : null;
			MessageCenterService.sendBinaryMessage(getActivity(),
			    userId, mime, uri, length, previewPath, ContentUris.parseId(newMsg));
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

                // save to local storage
                ContentValues values = new ContentValues();
                // must supply a message ID...
                values.put(Messages.MESSAGE_ID, "draft" + (new Random().nextInt()));
                values.put(Messages.PEER, userId);
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
                        userId, mText, Preferences
                            .getEncryptionEnabled(getActivity()),
                        ContentUris.parseId(newMsg));
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
	public void sendTextMessage(String text, boolean fromTextEntry) {
	    if (fromTextEntry)
	        text = mTextEntry.getText().toString();

		if (!TextUtils.isEmpty(text)) {
			/*
			 * TODO show an animation to warn the user that the message
			 * is being sent (actually stored).
			 */

		    offlineModeWarning();

			// start thread
			new TextMessageThread(text).start();

			if (fromTextEntry) {
	            // empty text
                mTextEntry.setText("");

                // hide softkeyboard
                InputMethodManager imm = (InputMethodManager) getActivity()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mTextEntry.getWindowToken(),
                        InputMethodManager.HIDE_IMPLICIT_ONLY);
			}
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.compose_message_menu, menu);

		mDeleteThreadMenu = menu.findItem(R.id.delete_thread);
		mViewContactMenu = menu.findItem(R.id.view_contact);
		mCallMenu = menu.findItem(R.id.call_contact);
		mBlockMenu = menu.findItem(R.id.block_user);
		mUnblockMenu = menu.findItem(R.id.unblock_user);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
    		case R.id.call_contact:
    			startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:"
    					+ userPhone)));
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
	                    userPhone != null ? userPhone : userId);

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
	}

	private void startDownload(CompositeMessage msg) {
		AttachmentComponent attachment = (AttachmentComponent) msg
				.getComponent(AttachmentComponent.class);

		if (attachment != null && attachment.getFetchUrl() != null) {
            Intent i = new Intent(getActivity(), DownloadService.class);
            i.setAction(DownloadService.ACTION_DOWNLOAD_URL);
            i.putExtra(CompositeMessage.MSG_ID, msg.getId());
            i.putExtra(CompositeMessage.MSG_SENDER, msg.getSender());
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
        }
    }

    public void viewContact() {
        if (mConversation != null) {
            Contact contact = mConversation.getContact();
            if (contact != null)
                startActivity(new Intent(Intent.ACTION_VIEW,
                        contact.getUri()));
        }
    }

	/** Starts dialog for attachment selection. */
	public void selectAttachment() {
	    if (attachmentMenu == null) {
	        attachmentMenu = new IconContextMenu(getActivity(), CONTEXT_MENU_ATTACHMENT);
	        attachmentMenu.addItem(getResources(), R.string.attachment_picture, R.drawable.ic_launcher_gallery, ATTACHMENT_ACTION_PICTURE);
	        attachmentMenu.addItem(getResources(), R.string.attachment_contact, R.drawable.ic_launcher_contacts, ATTACHMENT_ACTION_CONTACT);
	        attachmentMenu.setOnClickListener(this);
	    }
	    attachmentMenu.createMenu(getString(R.string.menu_attachment)).show();
	}

	/** Starts activity for an image attachment. */
	private void selectImageAttachment() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT)
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

            mCurrentPhoto = MediaStorage.getTempImage(getActivity());
    	    Intent take = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    	    take.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mCurrentPhoto));
       	    chooser = Intent.createChooser(i, getString(R.string.chooser_send_picture));
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { take });
        }
        catch (UnsupportedOperationException ue) {
            Log.d(TAG, "no camera app or no camera present", ue);
        }
        catch (IOException e) {
            Log.e(TAG, "error creating temp file", e);
            Toast.makeText(getActivity(), R.string.chooser_error_no_camera,
                Toast.LENGTH_LONG).show();
        }

        if (chooser == null) chooser = i;
	    startActivityForResult(chooser, SELECT_ATTACHMENT_OPENABLE);
	}

	/** Starts activity for a vCard attachment from a contact. */
	private void selectContactAttachment() {
        Intent i = new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI);
        startActivityForResult(i, SELECT_ATTACHMENT_CONTACT);
	}

	private void showSmileysPopup(View anchor) {
        if (mSmileyPopup == null)
            mSmileyPopup = MessageUtils.smileysPopup(getActivity(), mSmileySelectListener);
        mSmileyPopup.show(anchor);
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
						mTextEntry.setText("");
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
	    setPrivacy(PRIVACY_BLOCK);
	}

    private void unblockUser() {
        setPrivacy(PRIVACY_UNBLOCK);
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
						getActivity(), msg, userPhone != null ? userPhone : userId);
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

		} catch (SQLiteException e) {
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

	private void onKeyboardStateChanged(boolean isKeyboardOpen) {
		if (isKeyboardOpen) {
			mTextEntry.setFocusableInTouchMode(true);
			mTextEntry.setHint(R.string.hint_type_to_compose);
		}
		else {
			mTextEntry.setFocusableInTouchMode(false);
			mTextEntry.setHint(R.string.hint_open_kbd_to_compose);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle out) {
		super.onSaveInstanceState(out);
		out.putParcelable(Uri.class.getName(), Threads.getUri(userId));
	}

	private void processArguments(Bundle savedInstanceState) {
		Bundle args = null;
		if (savedInstanceState != null) {
			Uri uri = savedInstanceState.getParcelable(Uri.class.getName());
			// threadId = ContentUris.parseId(uri);
			args = new Bundle();
			args.putString("action", ComposeMessage.ACTION_VIEW_USERID);
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
				ContentResolver cres = getActivity().getContentResolver();

				/*
				 * FIXME this will retrieve name directly from contacts,
				 * resulting in a possible discrepancy with users database
				 */
				Cursor c = cres.query(uri, new String[] {
						Syncer.DATA_COLUMN_DISPLAY_NAME,
						Syncer.DATA_COLUMN_PHONE }, null, null, null);
				if (c.moveToFirst()) {
					userName = c.getString(0);
					userPhone = c.getString(1);

					// FIXME should it be retrieved from RawContacts.SYNC3 ??
					userId = MessageUtils.sha1(userPhone);

					Cursor cp = cres.query(Messages.CONTENT_URI,
							new String[] { Messages.THREAD_ID }, Messages.PEER
									+ " = ?", new String[] { userId }, null);
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
				mConversation = Conversation.loadFromUserId(getActivity(),
						userId);

				if (mConversation == null) {
					mConversation = Conversation.createNew(getActivity());
                    mConversation.setNumberHint(args.getString("number"));
					mConversation.setRecipient(userId);
				}
				// this way avoid doing the users database query twice
				else {
				    if (mConversation.getContact() == null) {
    				    mConversation.setNumberHint(args.getString("number"));
    				    mConversation.setRecipient(userId);
				    }
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
			//if (userPhone != null) title += " <" + userPhone + ">";
			setActivityTitle(title, "", null);
		}

		// update conversation stuff
		if (mConversation != null)
			onConversationCreated();

		// non existant thread - check for not synced contact
		if (threadId <= 0 && mConversation != null) {
			Contact contact = mConversation.getContact();
			if (userPhone != null && contact != null ? !contact.isRegistered() : true) {
				// ask user to send invitation
				DialogInterface.OnClickListener noListener = new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// FIXME is this specific to sms app?
						Intent i = new Intent(Intent.ACTION_SENDTO,
								Uri.parse("smsto:" + userPhone));
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
					highlight, getListView());
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
			mConversation.setRecipient(userId);
			onConversationCreated();
		}
	}

	/** Called when the {@link Conversation} object has been created. */
	private void onConversationCreated() {
        // subscribe to presence notifications
        subscribePresence();

        mTextEntry.removeTextChangedListener(mChatStateListener);

		// restore draft (if any and only if user hasn't inserted text)
		if (mTextEntry.getText().length() == 0) {
			String draft = mConversation.getDraft();
			if (draft != null) {
				mTextEntry.setText(draft);

				// move cursor to end
				mTextEntry.setSelection(mTextEntry.getText().length());
			}
		}

        mTextEntry.addTextChangedListener(mChatStateListener);

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
	                        action = PRIVACY_BLOCK;

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
	                        showIdentityDialog();
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
                new String[] { userId });

        // setup broadcast receiver for block/unblock reply
        if (action == PRIVACY_BLOCK || action == PRIVACY_UNBLOCK) {
        	if (mPrivacyListener == null) {
        		mPrivacyListener = new BroadcastReceiver() {
					public void onReceive(Context context, Intent intent) {
						String from = intent.getStringExtra(MessageCenterService.EXTRA_FROM_USERID);

						if (userId.equals(from)) {
							// this will trigger a Contact reload
							mConversation.setRecipient(userId);
							// this will update block/unblock menu items
							updateUI();
							// request presence subscription if unblocking
							if (MessageCenterService.ACTION_UNBLOCKED.equals(intent.getAction()))
								presenceSubscribe();
							else
								Toast.makeText(getActivity(),
									R.string.msg_user_blocked,
									Toast.LENGTH_LONG).show();

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
        MessageCenterService.replySubscription(ctx, userId, action);
	}

	private void showIdentityDialog() {
        String fingerprint;
        String uid;

        PGPPublicKeyRing publicKey = UsersProvider.getPublicKey(getActivity(), userId);
        if (publicKey != null) {
            PGPPublicKey pk = PGP.getMasterKey(publicKey);
            fingerprint = PGP.getFingerprint(pk);
            uid = PGP.getUserId(pk, null);    // TODO server!!!
        }
        else {
            // FIXME using another string
            fingerprint = uid = getString(R.string.peer_unknown);
        }

        String text;

        Contact c = mConversation.getContact();
        if (c != null)
            text = getString(R.string.text_invitation_known,
                c.getName(),
                c.getNumber(),
                uid, fingerprint);
        else
            text = getString(R.string.text_invitation_unknown,
                uid, fingerprint);

        /*
         * TODO include an "Open" button on the dialog to ignore the request
         * and go on with the compose window.
         */
        new AlertDialog.Builder(getActivity())
            .setPositiveButton(android.R.string.ok, null)
            .setTitle(R.string.title_invitation)
            .setMessage(text)
            .show();

	}

    /*
	private final class UserPresenceBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MessageCenterServiceLegacy.ACTION_USER_PRESENCE.equals(action)) {
                int event = intent.getIntExtra("org.kontalk.presence.event", 0);
                CharSequence text = null;

                if (event == UserEvent.EVENT_OFFLINE_VALUE) {
                    text = buildLastSeenText(getResources().getString(R.string.seen_moment_ago_label));
                }
                else if (event == UserEvent.EVENT_ONLINE_VALUE) {
                    text = getResources().getString(R.string.seen_online_label);
                }
                else if (event == UserEvent.EVENT_STATUS_CHANGED_VALUE) {
                    // update users table
                    ContentValues values = new ContentValues(1);
                    values.put(Users.STATUS, intent.getStringExtra("org.kontalk.presence.status"));
                    context.getContentResolver().update(
                        Users.CONTENT_URI, values,
                        Users.HASH + "=?", new String[] { userId });
                    // time to invalidate cache
                    // TODO this should be done by cursor notification
                    Contact.invalidate(userId);
                }

                if (text != null) {
                    try {
                        setStatusText(text);
                    }
                    catch (Exception e) {
                        // something could happen in the mean time - e.g. fragment destruction
                    }
                }
            }

            else if (MessageCenterServiceLegacy.ACTION_CONNECTED.equals(action)) {
                // request user lookup
                PresenceServiceConnection conn = new PresenceServiceConnection(userId, true);
                getActivity().bindService(
                        new Intent(getActivity().getApplicationContext(),
                                MessageCenterServiceLegacy.class), conn,
                        Context.BIND_AUTO_CREATE);
            }
        }
	}
    */

	private void subscribePresence() {
        if (mPresenceReceiver == null) {
            mPresenceReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();

                    if (MessageCenterService.ACTION_PRESENCE.equals(action)) {

                        // we handle only (un)available presence stanzas
                        String type = intent.getStringExtra(MessageCenterService.EXTRA_TYPE);

                        if (Presence.Type.available.name().equals(type) || Presence.Type.unavailable.name().equals(type)) {

                            CharSequence statusText = null;

                            String groupId = intent.getStringExtra(MessageCenterService.EXTRA_GROUP_ID);
                            String from = intent.getStringExtra(MessageCenterService.EXTRA_FROM_USERID);

                            // we are receiving a presence from our peer, upgrade available resources
                            if (from != null && from.substring(0, CompositeMessage.USERID_LENGTH).equals(userId)) {
                                // our presence!!!

                                if (Presence.Type.available.toString().equals(type)) {
                                    mAvailableResources.add(from);
                                    mCurrentStatus = getString(R.string.seen_online_label);
                                    if (!mIsTyping)
                                        setStatusText(mCurrentStatus);

                                    // abort presence probe if non-group stanza
                                    if (groupId == null) mPresenceId = null;
                                }
                                else if (Presence.Type.unavailable.toString().equals(type)) {
                                    mAvailableResources.remove(from);
                                    /*
                                     * All available resources have gone. If we are
                                     * not waiting for presence probe response, mark
                                     * the user as offline immediately and use the
                                     * timestamp provided with the stanza.
                                     */
                                    /*
                                     * FIXME this part has a serious bug.
                                     * Client might receive a certain set of
                                     * presence stanzas (e.g. while syncer is
                                     * running) which will empty mAvailableResources,
                                     * thus taking the latest stanza (this one) as
                                     * reference for last presence indication.
                                     * In fact, the most important presence is
                                     * always the most available or the most recent
                                     * one.
                                     * Anyway, this method is not reliable either
                                     * because of presence information not being
                                     * accounted for from the beginning. Therefore,
                                     * we don't know when a presence informs us
                                     * about a user being unavailable in that moment
                                     * or because a probe has been requested.
                                     */
                                    if (mAvailableResources.size() == 0 && mPresenceId == null) {
                                        // an offline user can't be typing
                                        mIsTyping = false;

                                        // user offline
                                        long stamp = intent.getLongExtra(MessageCenterService.EXTRA_STAMP, -1);
                                        if (stamp >= 0) {
                                            statusText = MessageUtils.formatRelativeTimeSpan(context, stamp);
                                        }
                                        else {
                                            statusText = getString(R.string.seen_moment_ago_label);
                                        }
                                    }
                                }
                            }

                            // we have a presence group
                            if (mPresenceId != null && mPresenceId.equals(groupId)) {
                                if (mMostAvailable == null)
                                    mMostAvailable = new PresenceData();

                                boolean take = false;

                                boolean available = (type == null || Presence.Type.available.toString().equals(type));
                                long stamp = intent.getLongExtra(MessageCenterService.EXTRA_STAMP, -1);
                                int priority = intent.getIntExtra(MessageCenterService.EXTRA_PRIORITY, 0);

                                if (available) {
                                    // take if higher priority
                                    if (priority >= mMostAvailable.priority)
                                        take = true;
                                }
                                else {
                                    // take if most recent
                                    long old = mMostAvailable.stamp != null ? mMostAvailable.stamp.getTime() : -1;
                                    if (stamp >= old)
                                        take = true;
                                }

                                if (take) {
                                    // available stanza - null stamp
                                    if (available) {
                                        mMostAvailable.stamp = null;
                                    }
                                    // unavailable stanza - update stamp
                                    else {
                                        if (mMostAvailable.stamp == null)
                                            mMostAvailable.stamp = new Date(stamp);
                                        else
                                            mMostAvailable.stamp.setTime(stamp);
                                    }

                                    mMostAvailable.status = intent.getStringExtra(MessageCenterService.EXTRA_STATUS);
                                    mMostAvailable.priority = priority;
                                }

                                int count = intent.getIntExtra(MessageCenterService.EXTRA_GROUP_COUNT, 0);
                                if (count <= 1 || mPresenceId == null) {
                                    // we got all presence stanzas
                                    Log.v(TAG, "got all presence stanzas or available stanza found (stamp=" + mMostAvailable.stamp +
                                        ", status=" + mMostAvailable.status + ")");

                                    // stop receiving presence probes
                                    mPresenceId = null;

                                    /*
                                     * TODO if we receive a presence unavailable stanza
                                     * we shall consider it only if there is no other
                                     * available resource. So we shall keep a reference
                                     * to all available resources and sync them
                                     * whenever a presence stanza is received.
                                     */
                                    if (mAvailableResources.size() == 0) {
                                        if (mMostAvailable.stamp != null) {
                                            statusText = MessageUtils.formatRelativeTimeSpan(context,
                                                mMostAvailable.stamp.getTime());
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

                        // subscription accepted, send presence probe
                        else if (Presence.Type.subscribed.name().equals(type)) {
                            mPresenceId = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
                        }
                    }

                    else if (MessageCenterService.ACTION_CONNECTED.equals(action)) {
                        // reset compose sent flag
                        mComposeSent = false;

                        // send subscription request
                        presenceSubscribe();
                    }

                    else if (MessageCenterService.ACTION_MESSAGE.equals(action)) {
                        String from = intent.getStringExtra(MessageCenterService.EXTRA_FROM_USERID);
                        String chatState = intent.getStringExtra("org.kontalk.message.chatState");

                        // we are receiving a composing notification from our peer
                        if (from != null && from.substring(0, CompositeMessage.USERID_LENGTH).equals(userId)) {
                            if (chatState != null && ChatState.composing.toString().equals(chatState)) {
                                mIsTyping = true;
                                setStatusText(getString(R.string.seen_typing_label));
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
	        filter.addAction(MessageCenterService.ACTION_MESSAGE);

            mLocalBroadcastManager.registerReceiver(mPresenceReceiver, filter);

            // request connection status
            MessageCenterService.requestConnectionStatus(getActivity());
	    }
	}

	/** Sends a subscription request for the current peer. */
	private void presenceSubscribe() {
		// all of this shall be done only if there isn't a request from the other contact
		if (mConversation.getRequestStatus() != Threads.REQUEST_WAITING) {

		    Contact c = mConversation.getContact();

			// pre-approve our presence if we don't have contact's key
			if (c == null || c.getPublicKeyRing() == null) {
		        Intent i = new Intent(getActivity(), MessageCenterService.class);
		        i.setAction(MessageCenterService.ACTION_PRESENCE);
		        i.putExtra(MessageCenterService.EXTRA_TO_USERID, userId);
		        i.putExtra(MessageCenterService.EXTRA_TYPE, Presence.Type.subscribed.name());
		        getActivity().startService(i);
			}

	        // send subscription request
	        Intent i = new Intent(getActivity(), MessageCenterService.class);
	        i.setAction(MessageCenterService.ACTION_PRESENCE);
	        i.putExtra(MessageCenterService.EXTRA_TO_USERID, userId);
	        i.putExtra(MessageCenterService.EXTRA_TYPE, Presence.Type.subscribe.name());
	        getActivity().startService(i);

		}
	}

	private void unsubcribePresence() {
        if (mPresenceReceiver != null) {
            mLocalBroadcastManager.unregisterReceiver(mPresenceReceiver);
	        mPresenceReceiver = null;
	    }

        // send unsubscription request
        Intent i = new Intent(getActivity(), MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_PRESENCE);
        i.putExtra(MessageCenterService.EXTRA_TO_USERID, userId);
        i.putExtra(MessageCenterService.EXTRA_TYPE, "unsubscribe");
        getActivity().startService(i);
	}

	/*
	@Override
	public boolean tx(ClientConnection connection, String txId, MessageLite pack) {
	    if (pack instanceof UserLookupResponse) {
	        UserLookupResponse _pack = (UserLookupResponse) pack;
	        if (_pack.getEntryCount() > 0) {
	            UserLookupResponse.Entry res = _pack.getEntry(0);
                CharSequence text = null;
                try {
                    Activity context = getActivity();
                    if (context != null) {
                        if (res.hasTimediff()) {
                            long diff = res.getTimediff();
                            if (diff == 0) {
                                text = getResources().getString(R.string.seen_online_label);
                            }
                            else if (diff <= 10) {
                                text = buildLastSeenText(getResources().getString(R.string.seen_moment_ago_label));
                            }
                        }

                        String afterText = null;

                        // update UsersProvider if necessary
                        ContentValues values = new ContentValues(2);
                        if (res.hasTimestamp())
                            values.put(Users.LAST_SEEN, res.getTimestamp());

                        if (res.hasStatus()) {
                            afterText = res.getStatus();
                            if (!TextUtils.isEmpty(afterText)) {
                                Contact c = getContact();
                                if (c != null)
                                    afterText = Preferences
                                        .decryptUserdata(getActivity(), afterText, c.getNumber());
                            }

                            values.put(Users.STATUS, afterText);
                        }
                        else {
                            values.putNull(Users.STATUS);
                        }

                        context.getContentResolver().update(
                            Users.CONTENT_URI, values,
                            Users.HASH + "=?", new String[] { userId });
                        // time to invalidate cache
                        // TODO this should be done by cursor notification
                        Contact.invalidate(userId);

                        if (text == null && res.hasTimestamp()) {
                            long time = res.getTimestamp();
                            if (time > 0) {
                                text = buildLastSeenText(MessageUtils.formatRelativeTimeSpan(context, time * 1000));
                            }
                        }

                        if (text != null) {
                            final CharSequence banner = text;
                            // show last seen banner
                            context.runOnUiThread(new Runnable() {
                                public void run() {
                                    try {
                                        setStatusText(banner);
                                    }
                                    catch (Exception e) {
                                        // something could happen in the meanwhile e.g. fragment destruction
                                    }
                                }
                            });
                        }
                    }
                }
                catch (Exception e) {
                    // what here?
                    Log.e(TAG, "user lookup response error!", e);
                }
	        }
	    }

	    return false;
	}
	*/

	private void setStatusText(CharSequence text) {
        setActivityTitle(null, text, null);
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
			Conversation conv = Conversation.loadFromUserId(mContext, userId);

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
	public void onStart() {
	    super.onStart();
        // hold message center
	    MessageCenterService.hold(getActivity());
	}

	@Override
	public void onResume() {
		super.onResume();

		if (Authenticator.getDefaultAccount(getActivity()) == null) {
			NumberValidation.startValidation(getActivity());
			getActivity().finish();
			return;
		}

		// cursor was previously destroyed -- reload everything
		// mConversation = null;
		processStart(true);
		if (userId != null) {
			// TODO use some method to generate the JID
			EndpointServer server = Preferences.getEndpointServer(getActivity());
			String jid = userId + '@' + server.getNetwork();

            // set notifications on pause
            MessagingNotification.setPaused(jid);

            // clear chat invitation (if any)
            // TODO use jid here
            MessagingNotification.clearChatInvitation(getActivity(), userId);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		CharSequence text = mTextEntry.getText();
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
				values.put(Messages.PEER, userId);
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
		        MessageCenterService.sendChatState(getActivity(), userId, ChatState.inactive);
    		mComposeSent = false;
		}
        // unsubcribe presence notifications
        unsubcribePresence();
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

		// release message center
		MessageCenterService.release(getActivity());
	}

	@Override
	public void onDestroy() {
	    super.onDestroy();
	    if (mTextEntry != null) {
	        mTextEntry.removeTextChangedListener(mChatStateListener);
	        mTextEntry.setText("");
	    }
	}

	public final boolean isFinishing() {
		return (getActivity() == null || (getActivity() != null && getActivity()
				.isFinishing())) || isRemoving();
	}

    private void updateUI() {
        Contact contact = (mConversation != null) ? mConversation
                .getContact() : null;

        boolean contactEnabled = contact != null && contact.getId() > 0;
        boolean threadEnabled = (threadId > 0);

        if (mCallMenu != null) {
            // FIXME what about VoIP?
            if (!getActivity().getPackageManager().hasSystemFeature(
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

        if (mBlockMenu != null && contact != null) {
            // block/unblock
            boolean blocked = contact.isBlocked();

            mBlockMenu.setVisible(!blocked);
            mUnblockMenu.setVisible(blocked);
        }
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
    							mTextEntry.getText().length() == 0))) {

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
	    return userId;
	}

	public void setTextEntry(CharSequence text) {
		mTextEntry.setText(text);
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

}
