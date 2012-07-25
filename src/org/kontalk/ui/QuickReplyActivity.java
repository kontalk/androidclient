package org.kontalk.ui;

import org.kontalk.R;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;


public class QuickReplyActivity extends Activity {
    private static final String TAG = QuickReplyActivity.class.getSimpleName();

    private TextView mFrom;
    private TextView mContent;
    private PendingIntent mOpenConv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.quick_reply);

        mFrom = (TextView) findViewById(R.id.from);
        mContent = (TextView) findViewById(R.id.content);

        processIntent();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        processIntent();
    }

    private void processIntent() {
        Intent intent = getIntent();
        Log.v(TAG, "processing intent: " + intent);

        String from = intent.getStringExtra("org.kontalk.quickreply.FROM");
        String content = intent.getStringExtra("org.kontalk.quickreply.MESSAGE");

        mOpenConv = intent.getParcelableExtra("org.kontalk.quickreply.OPEN_INTENT");
        mFrom.setText("From: " + from);
        mContent.setText(content);
    }

    public void reply(View view) {
        // TODO
    }

    public void delete(View view) {
        // TODO
    }

    public void openConversation(View view) {
        try {
            mOpenConv.send();
        }
        catch (CanceledException e) {
            Log.e(TAG, "intent canceled!", e);
        }
        finish();
    }
}
