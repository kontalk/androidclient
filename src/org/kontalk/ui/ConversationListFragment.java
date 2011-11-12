package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.service.MessageCenterService;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;


public class ConversationListFragment extends ListFragment {

    private static final String TAG = ConversationListFragment.class.getSimpleName();

    private static final int THREAD_LIST_QUERY_TOKEN = 8720;

    private static final int REQUEST_AUTHENTICATE = 7720;
    private static final int REQUEST_CONTACT_PICKER = 7721;

    private ThreadListQueryHandler mQueryHandler;
    private ConversationListAdapter mListAdapter;
    private boolean mDualPane;

    private final ConversationListAdapter.OnContentChangedListener mContentChangedListener =
        new ConversationListAdapter.OnContentChangedListener() {
        public void onContentChanged(ConversationListAdapter adapter) {
            startQuery();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.conversation_list, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mQueryHandler = new ThreadListQueryHandler(getActivity().getContentResolver());
        mListAdapter = new ConversationListAdapter(getActivity(), null);
        mListAdapter.setOnContentChangedListener(mContentChangedListener);

        ListView list = getListView();

        // Check to see if we have a frame in which to embed the details
        // fragment directly in the containing UI.
        View detailsFrame = getActivity().findViewById(R.id.fragment_compose_message);
        mDualPane = detailsFrame != null
                && detailsFrame.getVisibility() == View.VISIBLE;

        // add Compose message entry only if are in dual pane mode
        if (!mDualPane) {
            LayoutInflater inflater = getLayoutInflater(savedInstanceState);
            ConversationListItem headerView = (ConversationListItem)
                    inflater.inflate(R.layout.conversation_list_item, list, false);
            headerView.bind(getString(R.string.new_message),
                    getString(R.string.create_new_message));
            list.addHeaderView(headerView, null, true);
        }
        else {
            // TODO restore state
            list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        	list.setItemsCanFocus(true);
        }

        setListAdapter(mListAdapter);
        registerForContextMenu(list);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // TODO save state
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.conversation_list_menu, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        boolean visible = (mListAdapter != null && mListAdapter.getCount() > 0);
        MenuItem item;
        item = menu.findItem(R.id.menu_search);
        item.setEnabled(visible);
        item.setVisible(visible);
        item = menu.findItem(R.id.menu_delete_all);
        item.setEnabled(visible);
        item.setVisible(visible);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_compose:
                chooseContact();
                return true;

            case R.id.menu_search:
                // TODO search
                getActivity().onSearchRequested();
                return true;

            case R.id.menu_delete_all:
                deleteAll();
                return true;

            case R.id.menu_settings: {
                Intent intent = new Intent(getActivity(), MessagingPreferences.class);
                getActivity().startActivityIfNeeded(intent, -1);
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    private static final int MENU_OPEN_THREAD = 1;
    private static final int MENU_VIEW_CONTACT = 2;
    private static final int MENU_DELETE_THREAD = 3;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        ConversationListItem vitem = (ConversationListItem) info.targetView;
        Conversation conv = vitem.getConversation();
        if (conv != null) {
            Contact contact = conv.getContact();
            String title;
            if (contact != null)
                title = contact.getName() != null ? contact.getName() : contact.getNumber();
            else
                title = conv.getRecipient();

            menu.setHeaderTitle(title);
            menu.add(Menu.NONE, MENU_OPEN_THREAD, MENU_OPEN_THREAD, R.string.view_conversation);
            if (contact != null)
                menu.add(Menu.NONE, MENU_VIEW_CONTACT, MENU_VIEW_CONTACT, R.string.view_contact);
            menu.add(Menu.NONE, MENU_DELETE_THREAD, MENU_DELETE_THREAD, R.string.delete_thread);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        ConversationListItem vitem = (ConversationListItem) info.targetView;
        Conversation conv = vitem.getConversation();

        switch (item.getItemId()) {
            case MENU_OPEN_THREAD:
                openConversation(conv, info.position);
                return true;

            case MENU_VIEW_CONTACT:
                Contact contact = conv.getContact();
                if (contact != null)
                    startActivity(new Intent(Intent.ACTION_VIEW, contact.getUri()));
                return true;

            case MENU_DELETE_THREAD:
                Log.i(TAG, "deleting thread: " + conv.getThreadId());
                deleteThread(conv.getThreadId());
                return true;
        }

        return super.onContextItemSelected(item);
    }

    private void deleteThread(final long threadId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.confirm_delete_thread);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.confirm_will_delete_thread);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MessagesProvider.deleteThread(getActivity(), threadId);
                MessagingNotification.updateMessagesNotification(getActivity().getApplicationContext(), false);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    private void chooseContact() {
        // TODO one day it will be like this
        // Intent i = new Intent(Intent.ACTION_PICK, Users.CONTENT_URI);
        Intent i = new Intent(getActivity(), ContactsListActivity.class);
        startActivityForResult(i, REQUEST_CONTACT_PICKER);
    }

    private void deleteAll() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.confirm_delete_all);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.confirm_will_delete_all);
        builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                MessagesProvider.deleteDatabase(getActivity());
                MessagingNotification.updateMessagesNotification(getActivity().getApplicationContext(), false);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    public void startQuery() {
        try {
            if (!mDualPane)
                getActivity().setTitle(getString(R.string.refreshing));
            getActivity().setProgressBarIndeterminateVisibility(true);

            Conversation.startQuery(mQueryHandler, THREAD_LIST_QUERY_TOKEN);
        } catch (SQLiteException e) {
            Log.e(TAG, "query error", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (Authenticator.getDefaultAccount(getActivity()) == null) {
            startActivityForResult(new Intent(getActivity(), NumberValidation.class), REQUEST_AUTHENTICATE);
            // finish for now...
            return;
        }

        // check if contacts list has already been checked
        if (!MessagingPreferences.getContactsChecked(getActivity())) {
            // TODO start the contacts list checker thread
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        startQuery();
    }

    @Override
    public void onStop() {
        super.onStop();
        mListAdapter.changeCursor(null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // authentication
        if (requestCode == REQUEST_AUTHENTICATE) {
            // ok, start message center
            if (resultCode == Activity.RESULT_OK)
                MessageCenterService.startMessageCenter(getActivity());
            // failed - exit
            else
                getActivity().finish();
        }

        // contact chooser
        else if (requestCode == REQUEST_CONTACT_PICKER) {
            if (resultCode == Activity.RESULT_OK) {
                Uri rawContact = data.getData();
                if (rawContact != null) {
                    Log.i(TAG, "composing message for contact: " + rawContact);
                    Intent i = ComposeMessage.fromContactPicker(getActivity(), rawContact);
                    if (i != null)
                        startActivity(i);
                    else
                        Toast.makeText(getActivity(), R.string.contact_not_registered, Toast.LENGTH_LONG)
                            .show();
                }
            }
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        ConversationListItem cv = (ConversationListItem) v;
        Conversation conv = cv.getConversation();
        if (conv != null) {
            openConversation(conv, position);
        }

        // new composer
        else {
            chooseContact();
        }
    }

    private void openConversation(Conversation conv, int position) {
    	if (mDualPane) {
    		getListView().setItemChecked(position, true);

    		// get the old fragment
    		ComposeMessageFragment f = (ComposeMessageFragment) getActivity()
    				.getSupportFragmentManager().findFragmentById(R.id.fragment_compose_message);

            // check if we are replacing the same fragment
    		if (f == null || !f.getConversation().getRecipient().equals(conv.getRecipient())) {
    			f = ComposeMessageFragment.fromConversation(getActivity(), conv);
                // Execute a transaction, replacing any existing fragment
                // with this one inside the frame.
                FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_compose_message, f);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                ft.addToBackStack(null);
                ft.commit();
    		}
    	}
    	else {
	        Intent i = ComposeMessage.fromConversation(getActivity(), conv);
	        startActivity(i);
    	}
    }

    public final boolean isFinishing() {
        return (getActivity() == null ||
                (getActivity() != null && getActivity().isFinishing())) ||
                isRemoving();
    }

    /**
     * The conversation list query handler.
     */
    private final class ThreadListQueryHandler extends AsyncQueryHandler {
        public ThreadListQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (cursor == null || isFinishing()) {
                // close cursor - if any
                if (cursor != null) cursor.close();

                Log.e(TAG, "query aborted or error!");
                mListAdapter.changeCursor(null);
                return;
            }

            switch (token) {
            case THREAD_LIST_QUERY_TOKEN:
                mListAdapter.changeCursor(cursor);
                if (!mDualPane)
                    getActivity().setTitle(getString(R.string.app_name));
                getActivity().setProgressBarIndeterminateVisibility(false);
                break;

            default:
                Log.e(TAG, "onQueryComplete called with unknown token " + token);
            }
        }
    }

}
