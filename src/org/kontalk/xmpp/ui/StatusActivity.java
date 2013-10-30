package org.kontalk.xmpp.ui;

import org.kontalk.xmpp.R;
import org.kontalk.xmpp.service.MessageCenterService;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;


/** Status message activity. */
public class StatusActivity extends ListActivity {
    private EditText mStatus;
    private CursorAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.status_screen);

        mStatus = (EditText) findViewById(android.R.id.input);
        // TODO retrieve current status from server
        mStatus.setText(MessagingPreferences.getStatusMessage(this));

        mAdapter = new SimpleCursorAdapter(this,
            android.R.layout.simple_list_item_1, null,
            new String[] { "status" }, new int[] { android.R.id.text1 });
        setListAdapter(mAdapter);

        // TODO getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    public static void start(Activity context) {
        Intent intent = new Intent(context, StatusActivity.class);
        context.startActivityIfNeeded(intent, -1);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // TODO async query
        Cursor c = MessagingPreferences.getRecentStatusMessages(this);
        mAdapter.changeCursor(c);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAdapter != null)
            mAdapter.changeCursor(null);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                startActivity(new Intent(this, ConversationList.class));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Cursor c = (Cursor) mAdapter.getItem(position);
        // 0 = _id, 1 = status
        finish(c.getString(1));
    }

    private void finish(String text) {
        if (text.trim().length() <= 0)
            text = text.trim();
        MessagingPreferences.setStatusMessage(this, text);
        MessagingPreferences.addRecentStatusMessage(this, text);

        // start the message center to push the status message
        MessageCenterService.updateStatus(this);
        finish();
    }

    public void onStatusOk(View view) {
        String text = mStatus.getText().toString();
        finish(text);
    }

    public void onStatusCancel(View view) {
        finish();
    }
}
