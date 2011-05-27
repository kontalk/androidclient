package org.nuntius.android;

import org.nuntius.android.client.EndpointServer;
import org.nuntius.android.client.PollingClient;
import org.nuntius.android.service.MessageCenterService;

import android.app.Activity;
import android.content.Intent;
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        //getTextView().append("stopping service...\n");
        //stopService(new Intent(this, MessageCenterService.class));
    }
}
