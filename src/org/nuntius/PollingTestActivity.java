package org.nuntius;

import org.nuntius.R;
import org.nuntius.provider.Messages;
import org.nuntius.client.EndpointServer;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

public class PollingTestActivity extends Activity {

    private static final String testToken =
        "owGbwMvMwCGYeiJtndUThX+Mp6OSmHXVLH1v/7yaam6cmmiclGpknGJg" +
        "YmJpammeaGGUbGaUmmJpYZhmamGSaJpsaGGYVGNsbOJi4GZm7GZsZm7kaGD" +
        "qZmloZmLhamFm6mxhZuboauzoamJk4ObaEcfCIMjBwMbKBDKegYtTAGbtrd" +
        "UM/13clz3N8TknL2M/mXkXz9NXm1YyubvEqvfHsxjdXHVK2YiRYWtq+vYTn" +
        "YbJ/js6NMQOdj1tDbpXU8Dy4fn1jNvM8fNFHQA=";

    private ScrollView getScrollView() {
        return (ScrollView) findViewById(R.id.scroll_main);
    }

    private TextView getTextView() {
        return (TextView) findViewById(R.id.incoming_text);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.polling_test);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getTextView().append("starting service...\n");

        Intent intent = new Intent(this, MessageCenterService.class);
        intent.putExtra(EndpointServer.class.getName(), "http://10.0.2.2/serverimpl1");
        intent.putExtra(EndpointServer.HEADER_AUTH_TOKEN, testToken);
        startService(intent);

        // An array specifying which columns to return.
        String columns[] = new String[] { Messages.Message.MESSAGE_ID, Messages.Message.CONTENT };
        Uri myUri = Messages.CONTENT_URI;
        Cursor cur = managedQuery(myUri, columns, // Which columns to return
                null, // WHERE clause; which rows to return(all rows)
                null, // WHERE clause selection arguments (none)
                null // Order-by clause (ascending by name)
        );
        if (cur.moveToFirst()) {
            String id = null;
            String userName = null;
            do {
                // Get the field values
                id = cur.getString(cur.getColumnIndex(Messages.Message._ID));
                userName = cur.getString(cur.getColumnIndex(Messages.Message.CONTENT));
                getTextView().append("id: " + id + "\n" + userName + "\n");
            } while (cur.moveToNext());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //getTextView().append("stopping service...\n");
        //stopService(new Intent(this, MessageCenterService.class));
    }
}
