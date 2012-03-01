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
import java.util.Random;
import java.util.regex.Pattern;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.MessageSender;
import org.kontalk.client.Protocol;
import org.kontalk.client.RequestClient;
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
import org.kontalk.service.DownloadService;
import org.kontalk.service.MessageCenterService;
import org.kontalk.service.MessageCenterService.MessageCenterInterface;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.ListFragment;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The composer fragment.
 *
 * @author Daniele Ricci
 */
public class ComposeMessageFragment extends ListFragment implements
		View.OnTouchListener, View.OnLongClickListener {
	private static final String TAG = ComposeMessageFragment.class
			.getSimpleName();

	private static final int MESSAGE_LIST_QUERY_TOKEN = 8720;
	private static final int CONVERSATION_QUERY_TOKEN = 8721;

	private static final int SELECT_ATTACHMENT = 1;

	private MessageListQueryHandler mQueryHandler;
	private MessageListAdapter mListAdapter;
	private EditText mTextEntry;
	private View mSendButton;

	private TextView mLastSeenBanner;

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
	private Handler mHandler;
	private int mTouchSlop;

	/** Returns a new fragment instance from a picked contact. */
	public static ComposeMessageFragment fromContactPicker(Context context,
			Uri rawContactUri) {
		String userId = Contact.getUserId(context, rawContactUri);
		if (userId != null) {
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

		return null;
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

		mHandler = new Handler();
		registerForContextMenu(getListView());

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
				sendTextMessage();
			}
		});

		mLastSeenBanner = (TextView) getView()
				.findViewById(R.id.last_seen_text);
		View v = (View) mLastSeenBanner.getParent();
		v.setOnTouchListener(this);
		v.setOnLongClickListener(this);

		mTouchSlop = ViewConfiguration.get(getActivity()).getScaledTouchSlop();

		Configuration config = getResources().getConfiguration();
		mIsKeyboardOpen = config.keyboardHidden == KEYBOARDHIDDEN_NO;
		mIsLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
		onKeyboardStateChanged(mIsKeyboardOpen);

		processArguments(savedInstanceState);
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
	// private MessageRequestListener mMessageSenderListener;

	/** Used for binding to the message center to send messages. */
	private class ComposerServiceConnection implements ServiceConnection {
		public final MessageSender job;
		private MessageCenterService service;

		public ComposerServiceConnection(String userId, byte[] text,
				String mime, Uri msgUri, String encryptKey) {
			job = new MessageSender(userId, text, mime, msgUri, encryptKey);
			// job.setListener(mMessageSenderListener);
		}

		public ComposerServiceConnection(String userId, Uri fileUri,
				String mime, Uri msgUri, String encryptKey) {
			job = new MessageSender(userId, fileUri, mime, msgUri, encryptKey);
			// job.setListener(mMessageSenderListener);
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
			getActivity().unbindService(this);
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
			if (previewFile != null)
				values.put(Messages.PREVIEW_PATH, previewFile.getAbsolutePath());
			values.put(Messages.FETCHED, true);
			newMsg = getActivity().getContentResolver().insert(
					Messages.CONTENT_URI, values);
		} catch (Exception e) {
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
			// FIXME do not encrypt images for now
			ComposerServiceConnection conn = new ComposerServiceConnection(
					userId, uri, mime, newMsg, null);
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
				@Override
				public void run() {
					Toast.makeText(getActivity(),
							"Unable to store message to outbox.",
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

                // save to local storage
                ContentValues values = new ContentValues();
                // must supply a message ID...
                values.put(Messages.MESSAGE_ID, "draft" + (new Random().nextInt()));
                values.put(Messages.PEER, userId);
                values.put(Messages.MIME, PlainTextMessage.MIME_TYPE);
                values.put(Messages.CONTENT, mText.getBytes());
                values.put(Messages.UNREAD, false);
                values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
                values.put(Messages.TIMESTAMP, System.currentTimeMillis());
                values.put(Messages.STATUS, Messages.STATUS_SENDING);
                values.put(Messages.ENCRYPT_KEY, key);
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
	public void sendTextMessage() {
		String text = mTextEntry.getText().toString();
		if (!TextUtils.isEmpty(text)) {
			Log.v(TAG, "sending message...");

			/*
			 * TODO show an animation to warn the user that the message
			 * is being sent (actually stored).
			 */

			// start thread
			new TextMessageThread(text).start();

            // empty text
            mTextEntry.setText("");

            // hide softkeyboard
            InputMethodManager imm = (InputMethodManager) getActivity()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mTextEntry.getWindowToken(),
                    InputMethodManager.HIDE_IMPLICIT_ONLY);
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.compose_message_menu, menu);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		boolean contactEnabled = (mConversation != null) ? mConversation
				.getContact() != null : false;
		boolean threadEnabled = (threadId > 0);
		MenuItem i;

		i = menu.findItem(R.id.call_contact);
		if (!getActivity().getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_TELEPHONY)) {
			i.setVisible(false);
		}
		else {
			i.setVisible(true);
			i.setEnabled(contactEnabled);
		}
		i = menu.findItem(R.id.view_contact);
		i.setEnabled(contactEnabled);
		i = menu.findItem(R.id.delete_thread);
		i.setEnabled(threadEnabled);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.call_contact:
			startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:"
					+ userPhone)));
			return true;

		case R.id.view_contact:
			if (mConversation != null) {
				Contact contact = mConversation.getContact();
				if (contact != null)
					startActivity(new Intent(Intent.ACTION_VIEW,
							contact.getUri()));
			}
			return true;

		case R.id.menu_attachment:
			selectAttachment();
			return true;

		case R.id.delete_thread:
			if (threadId > 0)
				deleteThread();

			return true;

		case R.id.goto_conversation_list:
			startActivity(new Intent(getActivity(), ConversationList.class));
			getActivity().finish();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void selectAttachment() {
		Intent i = new Intent(Intent.ACTION_GET_CONTENT);
		i.addCategory(Intent.CATEGORY_OPENABLE);
		i.setType("image/*");
		startActivityForResult(i, SELECT_ATTACHMENT);
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
			ContentValues values = new ContentValues();
			values.put(Messages.CONTENT, msg.getBinaryContent());
			values.put(Messages.ENCRYPTED, false);
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
	private static final int MENU_DETAILS = 6;
	private static final int MENU_DELETE = 7;

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
			if (msg instanceof PlainTextMessage ? true
					: msg.getLocalUri() != null)
				menu.add(Menu.NONE, MENU_SHARE, MENU_SHARE, R.string.share);
		}

		if (msg instanceof ImageMessage) {
			// we are able to view image if either we fetched the image or we
			// sent that
			int string;
			// outgoing or already fetched
			if (msg.isFetched() || msg.getDirection() == Messages.DIRECTION_OUT)
				menu.add(Menu.NONE, MENU_OPEN, MENU_OPEN,
						R.string.view_image);

			// incoming
			if (msg.getDirection() == Messages.DIRECTION_IN) {
				// already fetched
				if (msg.isFetched())
					string = R.string.download_again;
				else
					string = R.string.download_file;
				menu.add(Menu.NONE, MENU_DOWNLOAD, MENU_DOWNLOAD, string);
			}
		}
		else if (msg instanceof VCardMessage) {
			// TODO handle encrypted vCards
			int string;
			// outgoing or already fetched
			if (msg.isFetched() || msg.getDirection() == Messages.DIRECTION_OUT)
				menu.add(Menu.NONE, MENU_OPEN, MENU_OPEN,
						R.string.open_file);

			// incoming
			if (msg.getDirection() == Messages.DIRECTION_IN) {
				// already fetched
				if (msg.isFetched())
					string = R.string.download_again;
				else
					string = R.string.download_file;
				menu.add(Menu.NONE, MENU_DOWNLOAD, MENU_DOWNLOAD, string);
			}
		}
		else {
			if (msg.isEncrypted())
				menu.add(Menu.NONE, MENU_DECRYPT, MENU_DECRYPT,
						R.string.decrypt_message);
			else
				menu.add(Menu.NONE, MENU_COPY_TEXT, MENU_COPY_TEXT,
						R.string.copy_message_text);
		}

		menu.add(Menu.NONE, MENU_DETAILS, MENU_DETAILS,
				R.string.menu_message_details);
		menu.add(Menu.NONE, MENU_DELETE, MENU_DELETE, R.string.delete_message);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		MessageListItem v = (MessageListItem) info.targetView;
		AbstractMessage<?> msg = v.getMessage();

		switch (item.getItemId()) {
			case MENU_SHARE: {
				Log.d(TAG, "sharing message: " + msg.getId());
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
				Log.d(TAG, "copying message text: " + msg.getId());
				ClipboardManager cpm = (ClipboardManager) getActivity()
						.getSystemService(Context.CLIPBOARD_SERVICE);
				cpm.setText(msg.getTextContent());

				Toast.makeText(getActivity(), R.string.message_text_copied,
						Toast.LENGTH_SHORT).show();
				return true;
			}

			case MENU_DECRYPT: {
				Log.d(TAG, "decrypting message: " + msg.getId());
				decryptMessage(msg);
				return true;
			}

			case MENU_DOWNLOAD: {
				Log.d(TAG, "downloading attachment");
				Intent i = new Intent(getActivity(), DownloadService.class);
				i.setAction(DownloadService.ACTION_DOWNLOAD_URL);
				i.putExtra(AbstractMessage.MSG_ID, msg.getId());
				i.setData(Uri.parse(msg.getFetchUrl()));
				getActivity().startService(i);
				return true;
			}

			case MENU_DETAILS: {
				Log.d(TAG, "opening message details");
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
				Log.d(TAG, "deleting message: " + msg.getDatabaseId());
				deleteMessage(msg.getDatabaseId());
				return true;
			}

			case MENU_OPEN: {
				Log.d(TAG, "opening file");
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setDataAndType(msg.getLocalUri(), msg.getMime());
				startActivity(i);
				return true;
			}
		}

		return super.onContextItemSelected(item);
	}

	private void startQuery(boolean reloadConversation) {
		try {
			getActivity().setProgressBarIndeterminateVisibility(true);

			Log.i(TAG, "starting query for thread " + threadId);
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
		if (resultCode == Activity.RESULT_OK) {
			if (requestCode == SELECT_ATTACHMENT) {
				Uri uri = data.getData();
				String mime = data.getType();
				if (uri != null) {
					if (mime == null || mime.startsWith("*/")
							|| mime.endsWith("/*")) {
						Log.d(TAG, "looking up mime type for uri " + uri);
						mime = getActivity().getContentResolver().getType(uri);
						Log.d(TAG, "using detected mime type " + mime);
					}

					sendBinaryMessage(uri, mime, true, ImageMessage.class);
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
			Log.w(TAG, "restoring from saved instance");
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
				Log.w(TAG, "intent uri: " + uri);
				ContentResolver cres = getActivity().getContentResolver();

				Cursor c = cres.query(uri, new String[] {
						SyncAdapter.DATA_COLUMN_DISPLAY_NAME,
						SyncAdapter.DATA_COLUMN_PHONE }, null, null, null);
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
			if (userPhone != null)
				title += " <" + userPhone + ">";
			getActivity().setTitle(title);
		}

		// update conversation stuff
		if (mConversation != null)
			onConversationCreated();

		// non existant thread - check for not synced contact
		if (threadId <= 0 && mConversation != null) {
			Contact contact = mConversation.getContact();
			if (userPhone != null && contact != null ? contact
					.getRawContactId(getActivity()) <= 0 : true) {
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

				AlertDialog.Builder build = new AlertDialog.Builder(
						getActivity());
				build.setTitle(R.string.title_user_not_found)
						.setMessage(R.string.message_user_not_found)
						// nothing happens if user chooses to contact the user
						// anyway
						.setPositiveButton(R.string.yes_user_not_found, null)
						.setNegativeButton(R.string.no_user_not_found,
								noListener).create().show();

			}
		}

		if (userId != null
				&& MessagingPreferences.getLastSeenEnabled(getActivity())) {
			// FIXME this should be handled better and of course honour activity
			// pause/resume/saveState/restoreState/display rotation.
			new Thread(new Runnable() {
				@Override
				public void run() {
					String text = null;
					String text2 = null;
					try {
						try {
							Context context = getActivity();
							RequestClient client = new RequestClient(context,
									MessagingPreferences
											.getEndpointServer(context),
									Authenticator
											.getDefaultAccountToken(context));

							final Protocol.LookupResponse data = client
									.lookup(userId);
							if (data != null && data.getEntryCount() > 0) {
								final Protocol.LookupResponseEntry res = data
										.getEntry(0);
								if (res.hasTimestamp()) {
									long time = res.getTimestamp();
									if (time > 0)
										text = getResources().getString(R.string.last_seen_label) +
												MessageUtils.formatRelativeTimeSpan(context, time * 1000);
									if (res.hasStatus()) {
										text2 = res.getStatus();
									}
								}
							}
						} catch (IOException e) {
							Log.e(TAG, "unable to lookup user " + userId, e);
							// TODO really silent error??
							// text = "(error)";
						}

						if (text != null) {
							final String bannerText = text;
							// show last seen banner
							Activity context = getActivity();
							if (context != null) {
								context.runOnUiThread(new Runnable() {
									@Override
									public void run() {
										try {
											mLastSeenBanner.setGravity(Gravity.CENTER);
											mLastSeenBanner.setText(bannerText);
											mLastSeenBanner.setVisibility(View.VISIBLE);
											mLastSeenBanner.startAnimation(AnimationUtils
											        .loadAnimation(getActivity(), R.anim.header_appear));
										}
										catch (Exception e) {
											// something could happen in the meanwhile e.g. fragment destruction
										}
									}
								});
								// display status message after 5 seconds
								if (text2 != null) {
									final String bannerText2 = text2;
									mHandler.postDelayed(new Runnable() {
										@Override
										public void run() {
											try {
												// restore gravity for all the moving stuff to work
												mLastSeenBanner.setGravity(Gravity.NO_GRAVITY);
												mLastSeenBanner.setText(bannerText2);
											}
											catch (Exception e) {
	                                            // something could happen in the meanwhile e.g. fragment destruction
											}
										}
									}, 5000);
								}
							}
						}
					} catch (Exception e) {
					}
				}
			}).start();
		}
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
					highlight);
			mListAdapter.setOnContentChangedListener(mContentChangedListener);
			setListAdapter(mListAdapter);
		}

		Log.i(TAG, "starting query with threadId " + threadId);
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

		// set notifications on pause
		MessagingNotification.setPaused(userId);

		if (mConversation.getThreadId() > 0) {
			// mark all messages as read
			mConversation.markAsRead();
		}
		else {
			// new conversation -- observe peer Uri
			registerPeerObserver();
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
				values.put(Messages.CONTENT, "");
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
	}

	@Override
	public void onStop() {
		super.onStop();
		unregisterPeerObserver();
		if (mListAdapter != null)
			mListAdapter.changeCursor(null);
		// release message center
		MessageCenterService.releaseMessageCenter(getActivity());
	}

	public final boolean isFinishing() {
		return (getActivity() == null || (getActivity() != null && getActivity()
				.isFinishing())) || isRemoving();
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
					Log.w(TAG, "no data to view - exit");
					getActivity().finish();
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
								long curId = cursor.getLong(cursor
										.getColumnIndex(Messages._ID));
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
				}

				break;

			case CONVERSATION_QUERY_TOKEN:
				Log.i(TAG, "conversation query completed, marking as read");
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

	public long getThreadId() {
		return threadId;
	}

	public void setTextEntry(CharSequence text) {
		mTextEntry.setText(text);
	}

	/* header view - thanks to Android Mail and Android Music player :) */

	private int mInitialX = -1;
	private int mLastX = -1;
	private int mTextWidth = 0;
	private int mViewWidth = 0;
	private boolean mDraggingLabel = false;

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		int action = event.getAction();
		TextView tv = (TextView) v.findViewById(R.id.last_seen_text);
		if (tv == null) {
			return false;
		}
		if (action == MotionEvent.ACTION_DOWN) {
			mInitialX = mLastX = (int) event.getX();
			mDraggingLabel = false;
		}
		else if (action == MotionEvent.ACTION_UP
				|| action == MotionEvent.ACTION_CANCEL) {
			if (mDraggingLabel) {
				Message msg = mLabelScroller.obtainMessage(0, tv);
				mLabelScroller.sendMessageDelayed(msg, 2000);
			}
		}
		else if (action == MotionEvent.ACTION_MOVE) {
			if (mDraggingLabel) {
				int scrollx = tv.getScrollX();
				int x = (int) event.getX();
				int delta = mLastX - x;
				if (delta != 0) {
					mLastX = x;
					scrollx += delta;
					if (scrollx > mTextWidth) {
						// scrolled the text completely off the view to the left
						scrollx -= mTextWidth;
						scrollx -= mViewWidth;
					}
					if (scrollx < -mViewWidth) {
						// scrolled the text completely off the view to the
						// right
						scrollx += mViewWidth;
						scrollx += mTextWidth;
					}
					tv.scrollTo(scrollx, 0);
				}
				return true;
			}
			int delta = mInitialX - (int) event.getX();
			if (Math.abs(delta) > mTouchSlop) {
				// start moving
				mLabelScroller.removeMessages(0, tv);

				// Only turn ellipsizing off when it's not already off, because
				// it
				// causes the scroll position to be reset to 0.
				if (tv.getEllipsize() != null) {
					tv.setEllipsize(null);
				}
				Layout ll = tv.getLayout();
				// layout might be null if the text just changed, or ellipsizing
				// was just turned off
				if (ll == null) {
					return false;
				}
				// get the non-ellipsized line width, to determine whether
				// scrolling
				// should even be allowed
				mTextWidth = (int) tv.getLayout().getLineWidth(0);
				mViewWidth = tv.getWidth();
				if (mViewWidth > mTextWidth) {
					tv.setEllipsize(TruncateAt.END);
					v.cancelLongPress();
					return false;
				}
				mDraggingLabel = true;
				tv.setHorizontalFadingEdgeEnabled(true);
				v.cancelLongPress();
				return true;
			}
		}
		return false;
	}

	Handler mLabelScroller = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			TextView tv = (TextView) msg.obj;
			int x = tv.getScrollX();
			x = x * 3 / 4;
			tv.scrollTo(x, 0);
			if (x == 0) {
				tv.setEllipsize(TruncateAt.END);
			}
			else {
				Message newmsg = obtainMessage(0, tv);
				mLabelScroller.sendMessageDelayed(newmsg, 15);
			}
		}
	};

	@Override
	public boolean onLongClick(View v) {
		// this seems to be necessary...
		return false;
	}
}
