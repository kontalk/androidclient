package org.nuntius;

import org.nuntius.client.EndpointServer;
import org.nuntius.provider.MyMessages.Threads;

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.SimpleCursorAdapter;

public class ThreadsActivity extends ListActivity {

    public static final String TAG = ThreadsActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.threads_view);

        String columns[] = new String[] { Threads._ID, Threads.CONTENT };
        Cursor cursor = managedQuery(Threads.CONTENT_URI, columns, null, null,
                Threads.DEFAULT_SORT_ORDER);

        // Used to map notes entries from the database to views
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.threadslist_item, cursor,
                new String[] { Threads.CONTENT }, new int[] { android.R.id.text1 });
        setListAdapter(adapter);

    }

    private static final String testToken =
        "owGbwMvMwCGYeiJtndUThX+Mp6OSmHXVLH1v/7yaam6cmmiclGpknGJg" +
        "YmJpammeaGGUbGaUmmJpYZhmamGSaJpsaGGYVGNsbOJi4GZm7GZsZm7kaGD" +
        "qZmloZmLhamFm6mxhZuboauzoamJk4ObaEcfCIMjBwMbKBDKegYtTAGbtrd" +
        "UM/13clz3N8TknL2M/mXkXz9NXm1YyubvEqvfHsxjdXHVK2YiRYWtq+vYTn" +
        "YbJ/js6NMQOdj1tDbpXU8Dy4fn1jNvM8fNFHQA=";

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "starting service");

        Intent intent = new Intent(this, MessageCenterService.class);
        intent.putExtra(EndpointServer.class.getName(), "http://10.0.2.2/serverimpl1");
        intent.putExtra(EndpointServer.HEADER_AUTH_TOKEN, testToken);
        startService(intent);
    }
}
