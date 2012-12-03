package org.kontalk;

import org.kontalk.service.MessageCenterService;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;


public class ServiceTest extends Activity {

    private TextView textLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.service_test);

        textLog = (TextView) findViewById(R.id.text_log);
    }

    private void appendLog(String text) {
        textLog.append("\n");
        textLog.append(text);
    }

    public void startService(View view) {
        Intent i = new Intent(this, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.type", "text/plain");
        i.putExtra("org.kontalk.message.to", "test@kontalk.net");
        i.putExtra("org.kontalk.message.body", "bella zio!");
        startService(i);
        appendLog("service started.");
    }

    public void stopService(View view) {
        stopService(new Intent(this, MessageCenterService.class));
        appendLog("service stopped.");
    }
}
