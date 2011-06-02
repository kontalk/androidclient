package org.nuntius.ui;

import org.nuntius.R;
import org.nuntius.client.EndpointServer;
import org.nuntius.data.Conversation;
import org.nuntius.service.MessageCenterService;

import android.app.ListActivity;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.ListView;


public class ConversationList extends ListActivity {

    private static final String TAG = ConversationList.class.getSimpleName();

    private static final int THREAD_LIST_QUERY_TOKEN = 8720;

    private ThreadListQueryHandler mQueryHandler;
    private ConversationListAdapter mListAdapter;

    private final ConversationListAdapter.OnContentChangedListener mContentChangedListener =
        new ConversationListAdapter.OnContentChangedListener() {
        public void onContentChanged(ConversationListAdapter adapter) {
            startQuery();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.conversation_list_screen);

        mQueryHandler = new ThreadListQueryHandler(getContentResolver());

        ListView listView = getListView();
        LayoutInflater inflater = LayoutInflater.from(this);
        ConversationListItem headerView = (ConversationListItem)
                inflater.inflate(R.layout.conversation_list_item, listView, false);
        headerView.bind(getString(R.string.new_message),
                getString(R.string.create_new_message));
        listView.addHeaderView(headerView, null, true);

        mListAdapter = new ConversationListAdapter(this, null);
        mListAdapter.setOnContentChangedListener(mContentChangedListener);
        setListAdapter(mListAdapter);

        /* TEST some numbers
        try {
            ContentValues values = new ContentValues();
            values.put(Users.HASH, "25242a976293f26d6a0abf686b1a96ded7d142a3");
            values.put(Users.NUMBER, "+393396241840");
            getContentResolver().insert(Users.CONTENT_URI, values);
        }
        catch (Throwable e) {
            // ignore
        }
        */
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.conversation_list_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_compose:
                // TODO createNewMessage();
                break;
            case R.id.menu_search:
                // TODO search
                onSearchRequested();
                break;
            case R.id.menu_delete_all:
                // TODO deleteAll();
                break;
            case R.id.menu_settings: {
                Intent intent = new Intent(this, MessagingPreferences.class);
                startActivityIfNeeded(intent, -1);
                break;
            }
            default:
                return true;
        }
        return false;
    }

    private void startQuery() {
        try {
            setTitle(getString(R.string.refreshing));
            setProgressBarIndeterminateVisibility(true);

            Conversation.startQuery(mQueryHandler, THREAD_LIST_QUERY_TOKEN);
        } catch (SQLiteException e) {
            Log.e(TAG, "query error", e);
        }
    }

    /* generated from +393271582382
    private static final String testToken =
        "owGbwMvMwCGYeiJtndUThX+Mp6OSmHXVLH1v/7yaam6cmmiclGpknGJg" +
        "YmJpammeaGGUbGaUmmJpYZhmamGSaJpsaGGYVGNsbOJi4GZm7GZsZm7kaGD" +
        "qZmloZmLhamFm6mxhZuboauzoamJk4ObaEcfCIMjBwMbKBDKegYtTAGbtrd" +
        "UM/13clz3N8TknL2M/mXkXz9NXm1YyubvEqvfHsxjdXHVK2YiRYWtq+vYTn" +
        "YbJ/js6NMQOdj1tDbpXU8Dy4fn1jNvM8fNFHQA=";
     */

    @Override
    protected void onStart() {
        super.onStart();
        startQuery();
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if ("pref_network_uri".equals(key)) {
                    // TODO just restart service with new server?
                }
            }
        });

        // check if token is present first -- need to authenticate if not
        String token = prefs.getString("pref_auth_token", null);
        if (token == null) {
            // start number validation activity
            startActivity(new Intent(this, NumberValidation.class));
            // we are not needed anymore (for now)
            finish();
            return;
        }

        // we have a token, start the message center
        Log.i(TAG, "starting service");
        Intent intent = new Intent(this, MessageCenterService.class);

        // get the URI from the preferences
        String uri = prefs.getString("pref_network_uri", "http://10.0.2.2/serverimpl1");
        intent.putExtra(EndpointServer.class.getName(), uri);
        intent.putExtra(EndpointServer.HEADER_AUTH_TOKEN, token);

        startService(intent);

        // check if contacts list has already been checked
        if (!MessagingPreferences.getContactsChecked(this)) {
            // start the contacts list checker thread

        }
    }

    /**
     * Called when a new intent is sent to the activity (if already started).
     */
    @Override
    protected void onNewIntent(Intent intent) {
        startQuery();
    }

    /**
     * Prevents the list adapter from using the cursor (which is being destroyed).
     */
    @Override
    protected void onStop() {
        super.onStop();
        mListAdapter.changeCursor(null);
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
            switch (token) {
            case THREAD_LIST_QUERY_TOKEN:
                mListAdapter.changeCursor(cursor);
                setTitle(getString(R.string.app_name));
                setProgressBarIndeterminateVisibility(false);
                break;

            default:
                Log.e(TAG, "onQueryComplete called with unknown token " + token);
            }
        }
    }
}
