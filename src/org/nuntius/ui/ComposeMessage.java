package org.nuntius.ui;

import org.nuntius.R;
import org.nuntius.provider.MyMessages.Messages;

import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.SimpleCursorAdapter;


/**
 * Conversation writing activity.
 * @author Daniele Ricci
 * @version 1.0
 */
public class ComposeMessage extends ListActivity {

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.compose_message);

        Cursor cursor = getContentResolver().query(Messages.CONTENT_URI,
                new String[] { Messages._ID, Messages.PEER, Messages.CONTENT, Messages.TIMESTAMP }, null, null, null);
        startManagingCursor(cursor);

        // the desired columns to be bound
        String[] columns = new String[] { Messages.CONTENT };
        // the XML defined views which the data will be bound to
        int[] to = new int[] { R.id.text_view };

        // create the adapter using the cursor pointing to the desired data as well as the layout information
        SimpleCursorAdapter mAdapter = new SimpleCursorAdapter(this, R.layout.message_list_item, cursor, columns, to);

        // set this adapter as your ListActivity's adapter
        setListAdapter(mAdapter);
    }
}
