package org.kontalk.ui;

import org.kontalk.R;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Window;


/**
 * The conversations list activity.
 * @author Daniele Ricci
 * @version 1.0
 */
public class ConversationList extends FragmentActivity {

    //private static final String TAG = ConversationList.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.conversation_list_screen);
    }

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.conversation_list_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean visible = (mListAdapter.getCount() > 0);
        MenuItem item;
        item = menu.findItem(R.id.menu_search);
        item.setEnabled(visible);
        item.setVisible(visible);
        item = menu.findItem(R.id.menu_delete_all);
        item.setEnabled(visible);
        item.setVisible(visible);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_compose:
                chooseContact();
                return true;

            case R.id.menu_search:
                // TODO search
                onSearchRequested();
                return true;

            case R.id.menu_delete_all:
                deleteAll();
                return true;

            case R.id.menu_settings: {
                Intent intent = new Intent(this, MessagingPreferences.class);
                startActivityIfNeeded(intent, -1);
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
                openConversation(conv);
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
    */

    /** Called when a new intent is sent to the activity (if already started). */
    @Override
    protected void onNewIntent(Intent intent) {
        ConversationListFragment fragment = (ConversationListFragment)
            getSupportFragmentManager().
            findFragmentById(R.id.fragment_conversation_list);
        fragment.startQuery();
    }

}
