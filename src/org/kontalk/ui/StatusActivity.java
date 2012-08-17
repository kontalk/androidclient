package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.service.MessageCenterService;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.MenuItem;


/** Status message activity. */
public class StatusActivity extends SherlockListActivity {
    private EditText mStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.status_screen);

        mStatus = (EditText) findViewById(android.R.id.input);
        // TODO retrieve from server
        mStatus.setText(MessagingPreferences.getStatusMessage(this));

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    public static void start(Activity context) {
        Intent intent = new Intent(context, StatusActivity.class);
        context.startActivityIfNeeded(intent, -1);
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

    public void onStatusOk(View view) {
        String text = mStatus.getText().toString();
        if (text.trim().length() <= 0)
            text = text.trim();
        MessagingPreferences.setStatusMessage(this, text);

        // start the message center to push the status message
        MessageCenterService.updateStatus(this);
        finish();
    }

    public void onStatusCancel(View view) {
        finish();
    }
}
