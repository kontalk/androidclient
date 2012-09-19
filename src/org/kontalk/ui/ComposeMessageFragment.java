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

import static android.content.res.Configuration.KEYBOARDHIDDEN_NO;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.ClientConnection;
import org.kontalk.client.MessageSender;
import org.kontalk.client.Protocol.UserEventMask;
import org.kontalk.client.Protocol.UserLookupResponse;
import org.kontalk.client.Protocol.UserPresence.UserEvent;
import org.kontalk.client.TxListener;
import org.kontalk.crypto.Coder;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.message.AbstractMessage;
import org.kontalk.message.ImageMessage;
import org.kontalk.message.PlainTextMessage;
import org.kontalk.message.VCardMessage;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Conversations;
import org.kontalk.provider.MyUsers.Users;
import org.kontalk.provider.UsersProvider;
import org.kontalk.service.ClientThread;
import org.kontalk.service.DownloadService;
import org.kontalk.service.MessageCenterService;
import org.kontalk.service.MessageCenterService.MessageCenterInterface;
import org.kontalk.service.RequestJob;
import org.kontalk.service.RequestListener;
import org.kontalk.service.UserLookupJob;
import org.kontalk.sync.Syncer;
import org.kontalk.ui.IconContextMenu.IconContextMenuOnClickListener;
import org.kontalk.util.Emoji;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.MessageUtils.SmileyImageSpan;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.ServiceConnection;
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
import android.os.IBinder;
import android.os.PatternMatcher;
import android.provider.ContactsContract.Contacts;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.protobuf.MessageLite;


/**
 * The composer fragment.
 * @author Daniele Ricci
 */
public class ComposeMessageFragment extends SherlockListFragment implements
		View.OnLongClickListener, RequestListener, TxListener, IconContextMenuOnClickListener {
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
    private MenuItem mDeleteThreadMenu;
    private MenuItem mViewContactMenu;
    private MenuItem mCallMenu;

	private boolean mIsKeyboardOpen;
	private boolean mIsLandscape;

	/** The thread id. */
	private long threadId = -1;
	private Conversation mConversation;
	private Bundle mArguments;

	/** The user we are talking to. */
	private String userId;
	private String userName;
	private String userPhone;

	private PeerObserver mPeerObserver;
    private File mCurrentPhoto;

	private LocalBroadcastManager mLocalBroadcastManager;
    private UserPresenceBroadcastReceiver mPresenceReceiver;

    private Dialog mSmileyDialog;

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
		Drawable bg = MessagingPreferences.getConversationBackground(getActivity());
		if (bg != null) {
		    list.setCacheColorHint(Color.TRANSPARENT);
		    list.setBackgroundDrawable(bg);
		}

		mTextEntry = (EditText) getView().findViewById(R.id.text_editor);
		mTextEntry.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				mSendButton.setEnabled(s.length() > 0);
			}
		});

		mSendButton = getView().findViewById(R.id.send_button);
		mSendButton.setEnabled(mTextEntry.length() > 0);
		mSendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sendTextMessage(null, true);
			}
		});

		Configuration config = getResources().getConfiguration();
		mIsKeyboardOpen = config.keyboardHidden == KEYBOARDHIDDEN_NO;
		mIsLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
		onKeyboardStateChanged(mIsKeyboardOpen);

		processArguments(savedInstanceState);

		mLocalBroadcastManager = LocalBroadcastManager.getInstance(getActivity());
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		mIsKeyboardOpen = newConfig.keyboardHidden == KEYBOARDHIDDEN_NO;
		boolean isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
		if (mIsLandscape != isLandscape) {
			mIsLandscape = isLandscape;
		}
		onKeyboardStateChanged(mIsKeyboardOpen);
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
				startQuery(true);
		}
	};

	/** Used by the service binder to receive responses from the request worker. */
	//private MessageRequestListener mMessageSenderListener;

	/** Used for binding to the message center to send messages. */
	private class ComposerServiceConnection implements ServiceConnection {
		public final MessageSender job;
		private MessageCenterService service;

		public ComposerServiceConnection(String userId, byte[] text,
				String mime, Uri msgUri, String encryptKey) {
			job = new MessageSender(userId, text, mime, msgUri, encryptKey, false);
			// listener will be set by message center
		}

		public ComposerServiceConnection(String userId, Uri fileUri,
				String mime, Uri msgUri, String encryptKey, boolean media) {
			job = new MessageSender(userId, fileUri, mime, msgUri, encryptKey);
            // listener will be set by message center
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
            try {
                getActivity().unbindService(this);
            }
            catch (Exception e) {
                // ignore exception on exit
            }
			service = null;
		}
	}

    /** Used for binding to the message center to listen for user presence. */
    private class PresenceServiceConnection implements ServiceConnection {
        private final String userId;
        private final boolean lookupOnly;
        private MessageCenterService service;

        public PresenceServiceConnection(String userId, boolean lookupOnly) {
            this.userId = userId;
            this.lookupOnly = lookupOnly;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder ibinder) {
            MessageCenterInterface binder = (MessageCenterInterface) ibinder;
            service = binder.getService();
            if (!lookupOnly)
                service.subscribePresence(this.userId, UserEventMask.USER_EVENT_MASK_ALL_VALUE);

            UserLookupJob job = service.lookupUser(userId);
            job.setListener(ComposeMessageFragment.this);

            try {
                getActivity().unbindService(this);
            }
            catch (Exception e) {
                // ignore exception on exit
            }
            service = null;
        }
    }

    /** Used for binding to the message center to unlisten for user presence. */
    private class PresenceServiceDisconnection implements ServiceConnection {
        public final String userId;
        private MessageCenterService service;

        public PresenceServiceDisconnection(String userId) {
            this.userId = userId;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder ibinder) {
            MessageCenterInterface binder = (MessageCenterInterface) ibinder;
            service = binder.getService();
            service.unsubscribePresence(this.userId);
            try {
                getActivity().unbindService(this);
            }
            catch (Exception e) {
                // ignore exception on exit
            }
            service = null;
        }
    }

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setHasOptionsMenu(true);
		mQueryHandler = new MessageListQueryHandler();

		// list adapter creation is post-poned
	}

	/** Sends out a binary message. */
	public void sendBinaryMessage(Uri uri, String mime, boolean media,
			Class<? extends AbstractMessage<?>> klass) {
		Log.v(TAG, "sending binary content: " + uri);
		Uri newMsg = null;

		try {
		    // TODO convert to thread (?)

			String msgId = "draft" + (new Random().nextInt());
			String content = AbstractMessage.getSampleTextContent(klass, mime);

			File previewFile = null;
			// generate thumbnail
			if (media) {
				String filename = ImageMessage.buildMediaFilename(msgId, mime);
				previewFile = MediaStorage.cacheThumbnail(getActivity(), uri,
						filename);
			}

			// save to local storage
            ContentValues values = new ContentValues();
			// must supply a message ID...
			values.put(Messages.MESSAGE_ID, msgId);
			values.put(Messages.PEER, userId);
			values.put(Messages.MIME, mime);
			values.put(Messages.CONTENT, content.getBytes());
			values.put(Messages.UNREAD, false);
			values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
			values.put(Messages.TIMESTAMP, System.currentTimeMillis());
			values.put(Messages.STATUS, Messages.STATUS_SENDING);
			values.put(Messages.LOCAL_URI, uri.toString());
            values.put(Messages.LENGTH, MediaStorage.getLength(getActivity(), uri));
			if (previewFile != null)
				values.put(Messages.PREVIEW_PATH, previewFile.getAbsolutePath());
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
					startQuery(true);
				}
				else {
					Log.v(TAG, "no data - cannot start query for this composer");
				}
				c.close();
			}

			// send the message!
			// FIXME do not encrypt binary messages for now
			ComposerServiceConnection conn = new ComposerServiceConnection(
					userId, uri, mime, newMsg, null, media);
			if (!getActivity().bindService(
					new Intent(getActivity().getApplicationContext(),
							MessageCenterService.class), conn,
					Context.BIND_AUTO_CREATE)) {
				// cannot bind :(
				// mMessageSenderListener.error(conn.job, new
				// IllegalArgumentException("unable to bind to message center"));
			}
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
                // get encryption key if needed
                String key = null;
                if (MessagingPreferences.getEncryptionEnabled(getActivity())) {
                    key = MessagingPreferences.getDefaultPassphrase(getActivity());
                    // no global passphrase defined -- use recipient phone number
                    if (key == null || key.length() == 0)
                        key = Contact.numberByUserId(getActivity(), userId);
                }

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
                values.put(Messages.MIME, PlainTextMessage.MIME_TYPE);
                values.put(Messages.CONTENT, bytes);
                values.put(Messages.UNREAD, false);
                values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
                values.put(Messages.TIMESTAMP, System.currentTimeMillis());
                values.put(Messages.STATUS, Messages.STATUS_SENDING);
                values.put(Messages.ENCRYPT_KEY, key);
                values.put(Messages.LENGTH, bytes.length);
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
                            // re-run query on UI thread - actually will be run
                            // on another thread, this is just for setProgressBarIndeterminate
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    startQuery(true);
                                }
                            });
                        }
                        else {
                            Log.v(TAG,
                                    "no data - cannot start query for this composer");
                        }
                        c.close();
                    }

                    // send the message!
                    ComposerServiceConnection conn = new ComposerServiceConnection(
                            userId, mText.getBytes(), PlainTextMessage.MIME_TYPE,
                            newMsg, key);
                    if (!getActivity().bindService(
                            new Intent(getActivity().getApplicationContext(),
                                    MessageCenterService.class), conn,
                            Context.BIND_AUTO_CREATE)) {
                        // cannot bind :(
                        // mMessageSenderListener.error(conn.job, new
                        // IllegalArgumentException("unable to bind to service"));
                    }
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
		MenuItem item = menu.findItem(R.id.menu_attachment2);
		if (item != null) item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        item = menu.findItem(R.id.menu_smiley);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

		mDeleteThreadMenu = menu.findItem(R.id.delete_thread);
		mViewContactMenu = menu.findItem(R.id.view_contact);
		mCallMenu = menu.findItem(R.id.call_contact);
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
            case R.id.menu_attachment2:
    		    selectAttachment();
    			return true;

            case R.id.delete_thread:
    			if (threadId > 0)
    				deleteThread();

    			return true;

    		case R.id.goto_conversation_list:
                getActivity().finish();
    			startActivity(new Intent(getActivity(), ConversationList.class));
    			return true;

    		case R.id.menu_smiley:
    		    AdapterView.OnItemClickListener listener = new AdapterView.OnItemClickListener() {
    		        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    		            Editable text = mTextEntry.getText();
    		            int startPos = mTextEntry.getSelectionStart();
    		            int endPos = mTextEntry.getSelectionEnd();
    		            // +1 because of the 1-based array
    		            position++;

    		            if (startPos < 0) startPos = text.length();
    		            if (endPos < 0) endPos = startPos;
                        int startMin = Math.min(startPos, endPos);

                        // add unicode emoji
    		            text.replace(startMin, Math.max(startPos, endPos),
    		                String.valueOf((char) (0xe000 + position)), 0, 1);

    		            // set emoji image span
    		            SmileyImageSpan span = new SmileyImageSpan(getActivity(),
    		                Emoji.getSmileyResourceId(position), SmileyImageSpan.SIZE_EDITABLE);
                        // resize image
    		            text.setSpan(span, startMin, startMin + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    		            mSmileyDialog.dismiss();
    		        }
                };
    		    mSmileyDialog = MessageUtils.smileysDialog(getActivity(), listener);
    		    mSmileyDialog.show();
    		    return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onListItemClick(ListView listView, View view, int position, long id) {
	    MessageListItem item = (MessageListItem) view;
	    final AbstractMessage<?> msg = item.getMessage();
	    if (msg.getFetchUrl() != null || msg.getLocalUri() != null) {
	        // outgoing message or already fetched
	        if (msg.getLocalUri() != null) {
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

                if (!DownloadService.isQueued(msg.getFetchUrl())) {
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

	private void startDownload(AbstractMessage<?> msg) {
	    String fetchUrl = msg.getFetchUrl();
	    if (fetchUrl != null) {
            Intent i = new Intent(getActivity(), DownloadService.class);
            i.setAction(DownloadService.ACTION_DOWNLOAD_URL);
            i.putExtra(AbstractMessage.MSG_ID, msg.getId());
            i.setData(Uri.parse(fetchUrl));
            getActivity().startService(i);
	    }
	    else {
	        // corrupted message :(
	        Toast.makeText(getActivity(), R.string.err_attachment_corrupted,
	            Toast.LENGTH_LONG).show();
	    }
	}

	private void stopDownload(AbstractMessage<?> msg) {
        String fetchUrl = msg.getFetchUrl();
        if (fetchUrl != null) {
            Intent i = new Intent(getActivity(), DownloadService.class);
            i.setAction(DownloadService.ACTION_DOWNLOAD_ABORT);
            i.setData(Uri.parse(fetchUrl));
            getActivity().startService(i);
        }
	}

	private void openFile(AbstractMessage<?> msg) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(msg.getLocalUri(), msg.getMime());
        startActivity(i);
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
	    attachmentMenu.createMenu("Attach").show();
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

	private void decryptMessage(AbstractMessage<?> msg) {
		Account acc = Authenticator.getDefaultAccount(getActivity());
		Coder coder = MessagingPreferences.getDecryptCoder(getActivity(),
				acc.name);
		try {
			// decrypt the message
			msg.decrypt(coder);
			// update database
			final byte[] content = msg.getBinaryContent();
			ContentValues values = new ContentValues();
			values.put(Messages.CONTENT, content);
			values.put(Messages.ENCRYPTED, false);
			values.put(Messages.LENGTH, content.length);
			getActivity().getContentResolver().update(
					Messages.getUri(msg.getId()), values, null, null);
		} catch (GeneralSecurityException e) {
			Log.e(TAG, "unable to decrypt message", e);
			Toast.makeText(getActivity(), "Decryption failed!",
					Toast.LENGTH_LONG).show();
		}
	}

	private static final int MENU_SHARE = 1;
	private static final int MENU_COPY_TEXT = 2;
	private static final int MENU_DECRYPT = 3;
	private static final int MENU_OPEN = 4;
	private static final int MENU_DOWNLOAD = 5;
	private static final int MENU_CANCEL_DOWNLOAD = 6;
	private static final int MENU_DETAILS = 7;
	private static final int MENU_DELETE = 8;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		MessageListItem vitem = (MessageListItem) info.targetView;
		AbstractMessage<?> msg = vitem.getMessage();

		menu.setHeaderTitle(R.string.title_message_options);

		// message forwarding can be used only for unencrypted messages
		if (!msg.isEncrypted()) {
			// sharing media messages has no purpose if media file hasn't been
			// retrieved yet
			if (msg instanceof PlainTextMessage ? true : msg.getLocalUri() != null)
				menu.add(CONTEXT_MENU_GROUP_ID, MENU_SHARE, MENU_SHARE, R.string.share);
		}

		if (msg.getFetchUrl() != null || msg.getLocalUri() != null) {
		    // message has a local uri - add open file entry
		    if (msg.getLocalUri() != null) {
		        int resId;
		        if (msg instanceof ImageMessage)
		            resId = R.string.view_image;
		        else
		            resId = R.string.open_file;

                menu.add(CONTEXT_MENU_GROUP_ID, MENU_OPEN, MENU_OPEN, resId);
		    }

		    // message has a fetch url - add download control entry
		    if (msg.getFetchUrl() != null) {
		        int id, string;
                if (!DownloadService.isQueued(msg.getFetchUrl())) {
                    // already fetched
                    if (msg.getLocalUri() != null)
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

		else {
			if (msg.isEncrypted())
				menu.add(CONTEXT_MENU_GROUP_ID, MENU_DECRYPT, MENU_DECRYPT,
						R.string.decrypt_message);
			else
				menu.add(CONTEXT_MENU_GROUP_ID, MENU_COPY_TEXT, MENU_COPY_TEXT,
						R.string.copy_message_text);
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
		AbstractMessage<?> msg = v.getMessage();

		switch (item.getItemId()) {
			case MENU_SHARE: {
				Intent i;
				if (msg instanceof PlainTextMessage)
					i = ComposeMessage.sendTextMessage(msg.getTextContent());
				else
					i = ComposeMessage.sendMediaMessage(msg.getLocalUri(),
							msg.getMime());
				startActivity(i);

				return true;
			}

			case MENU_COPY_TEXT: {
				ClipboardManager cpm = (ClipboardManager) getActivity()
						.getSystemService(Context.CLIPBOARD_SERVICE);
				cpm.setText(msg.getTextContent());

				Toast.makeText(getActivity(), R.string.message_text_copied,
						Toast.LENGTH_SHORT).show();
				return true;
			}

			case MENU_DECRYPT: {
				decryptMessage(msg);
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

	private void startQuery(boolean reloadConversation) {
		try {
			getActivity().setProgressBarIndeterminateVisibility(true);

			AbstractMessage.startQuery(mQueryHandler, MESSAGE_LIST_QUERY_TOKEN,
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
			    Uri uri;
			    String mime = null;

			    // returning from camera
			    if (data == null) {
			        uri = Uri.fromFile(mCurrentPhoto);
			        // notify media scanner
		            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		            mediaScanIntent.setData(uri);
		            getActivity().sendBroadcast(mediaScanIntent);
		            mCurrentPhoto = null;
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

					if (ImageMessage.supportsMimeType(mime))
					    sendBinaryMessage(uri, mime, true, ImageMessage.class);
					else if (VCardMessage.supportsMimeType(mime))
					    sendBinaryMessage(uri, mime, false, VCardMessage.class);
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
		                    sendBinaryMessage(vcardUri, VCardMessage.MIME_TYPES[0], false, VCardMessage.class);
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
		out.putParcelable(Uri.class.getName(),
				ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId));
	}

	private void processArguments(Bundle savedInstanceState) {
		Bundle args = null;
		if (savedInstanceState != null) {
			Uri uri = savedInstanceState.getParcelable(Uri.class.getName());
			// threadId = ContentUris.parseId(uri);
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
					try {
						userId = MessageUtils.sha1(userPhone);
					} catch (NoSuchAlgorithmException e) {
						// fatal error - shouldn't happen
						Log.e(TAG, "sha1 digest failed", e);
						throw new RuntimeException(e);
					}

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

	public void setActivityTitle(String title, String status, Contact contact) {
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

	private void processStart() {
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
			if (draft != null) {
				mTextEntry.setText(draft);
				mTextEntry.setSelection(mTextEntry.getText().length());
			}
		}

		if (mConversation.getThreadId() > 0) {
			// mark all messages as read
			mConversation.markAsRead();
		}
		else {
			// new conversation -- observe peer Uri
			registerPeerObserver();
		}

		// update contact icon
		setActivityTitle(null, null, mConversation.getContact());

		updateUI();
	}

	private final class UserPresenceBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MessageCenterService.ACTION_USER_PRESENCE.equals(action)) {
                int event = intent.getIntExtra("org.kontalk.presence.event", 0);
                String text = null;

                if (event == UserEvent.EVENT_OFFLINE_VALUE) {
                    text = getResources().getString(R.string.last_seen_label) +
                            getResources().getString(R.string.seen_moment_ago_label);
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

            else if (MessageCenterService.ACTION_CONNECTED.equals(action)) {
                // request user lookup
                PresenceServiceConnection conn = new PresenceServiceConnection(userId, true);
                getActivity().bindService(
                        new Intent(getActivity().getApplicationContext(),
                                MessageCenterService.class), conn,
                        Context.BIND_AUTO_CREATE);
            }
        }
	}

	private void subscribePresence() {
        if (mPresenceReceiver == null) {
    	    PresenceServiceConnection conn = new PresenceServiceConnection(userId, false);
            getActivity().bindService(
                    new Intent(getActivity().getApplicationContext(),
                            MessageCenterService.class), conn,
                    Context.BIND_AUTO_CREATE);

            mPresenceReceiver = new UserPresenceBroadcastReceiver();

    	    try {
    	        // filter for user presence
                IntentFilter filter = new IntentFilter(MessageCenterService.ACTION_USER_PRESENCE, "internal/presence");
                filter.addDataScheme("user");
                filter.addDataAuthority(UsersProvider.AUTHORITY, null);
                filter.addDataPath("/" + userId, PatternMatcher.PATTERN_PREFIX);
                mLocalBroadcastManager.registerReceiver(mPresenceReceiver, filter);
                // filter for message center reconnection
                filter = new IntentFilter(MessageCenterService.ACTION_CONNECTED);
                mLocalBroadcastManager.registerReceiver(mPresenceReceiver, filter);
    	    }
            catch (MalformedMimeTypeException e) {
                Log.e(TAG, "malformed mime type", e);
            }
	    }
	}

	private void unsubcribePresence() {
        if (mPresenceReceiver != null) {
            PresenceServiceDisconnection conn = new PresenceServiceDisconnection(userId);
            getActivity().bindService(
                    new Intent(getActivity().getApplicationContext(),
                            MessageCenterService.class), conn,
                    Context.BIND_AUTO_CREATE);
            mLocalBroadcastManager.unregisterReceiver(mPresenceReceiver);
	        mPresenceReceiver = null;
	    }
	}

	@Override
	public boolean tx(ClientConnection connection, String txId, MessageLite pack) {
	    if (pack instanceof UserLookupResponse) {
	        UserLookupResponse _pack = (UserLookupResponse) pack;
	        if (_pack.getEntryCount() > 0) {
	            UserLookupResponse.Entry res = _pack.getEntry(0);
                String text = null;
                try {
                    Activity context = getActivity();
                    if (context != null) {
                        if (res.hasTimediff()) {
                            long diff = res.getTimediff();
                            if (diff == 0) {
                                text = getResources().getString(R.string.seen_online_label);
                            }
                            else if (diff <= 10) {
                                text = getResources().getString(R.string.last_seen_label) +
                                        getResources().getString(R.string.seen_moment_ago_label);
                            }
                        }

                        String afterText = null;

                        // update UsersProvider if necessary
                        ContentValues values = new ContentValues(2);
                        if (res.hasTimestamp())
                            values.put(Users.LAST_SEEN, res.getTimestamp());

                        if (res.hasStatus()) {
                            afterText = res.getStatus();
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
                                text = getResources().getString(R.string.last_seen_label) +
                                        MessageUtils.formatRelativeTimeSpan(context, time * 1000);
                            }
                        }

                        if (text != null) {
                            final String banner = text;
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

	private void setStatusText(String text) {
        setActivityTitle(null, text, null);
	}

	@Override
	public void starting(ClientThread client, RequestJob job) {
	    // not used
	}

    @Override
    public void uploadProgress(ClientThread client, RequestJob job, long bytes) {
        // not used
    }

    @Override
    public void downloadProgress(ClientThread client, RequestJob job, long bytes) {
        // not used
    }

    @Override
    public void done(ClientThread client, RequestJob job, String txId) {
        client.setTxListener(txId, this);
    }

    @Override
    public boolean error(ClientThread client, RequestJob job, Throwable exc) {
        // TODO
        return false;
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
			processStart();
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
        MessageCenterService.holdMessageCenter(getActivity());
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
		processStart();
		if (userId != null) {
            // set notifications on pause
            MessagingNotification.setPaused(userId);
            // subscribe to presence notifications
            subscribePresence();
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
			ContentValues values = new ContentValues(1);
			values.put(Threads.DRAFT, (len > 0) ? text.toString() : null);
			getActivity().getContentResolver().update(
					ContentUris.withAppendedId(Threads.CONTENT_URI, threadId),
					values, null, null);
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
				values.put(Messages.MIME, PlainTextMessage.MIME_TYPE);
				values.put(Messages.CONTENT, new byte[0]);
				values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
				values.put(Messages.TIMESTAMP, System.currentTimeMillis());
				values.put(Threads.DRAFT, text.toString());
				getActivity().getContentResolver().insert(Messages.CONTENT_URI,
						values);
			}
		}

		if (len > 0) {
			Toast.makeText(getActivity(), R.string.msg_draft_saved,
					Toast.LENGTH_LONG).show();
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
		MessageCenterService.releaseMessageCenter(getActivity());
	}

	public final boolean isFinishing() {
		return (getActivity() == null || (getActivity() != null && getActivity()
				.isFinishing())) || isRemoving();
	}

    private void updateUI() {
        boolean contactEnabled = (mConversation != null) ? mConversation
            .getContact() != null : false;
        boolean threadEnabled = (threadId > 0);

        if (mCallMenu != null) {
            // FIXME what about VoIP?
            if (!getActivity().getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY)) {
                mCallMenu.setVisible(false);
            }
            else {
                mCallMenu.setVisible(true);
                mCallMenu.setEnabled(contactEnabled);
            }
            mViewContactMenu.setEnabled(contactEnabled);
            mDeleteThreadMenu.setEnabled(threadEnabled);
        }
    }

	/** The conversation list query handler. */
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
    						&& (mConversation == null
    								|| mConversation.getDraft() == null || mTextEntry
    								.getText().length() == 0)) {
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
    								long curId = cursor.getLong(AbstractMessage.COLUMN_ID);
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
            activity.getListFragment().endConversation(ComposeMessageFragment.this);
        }
	}

}
