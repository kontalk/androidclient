/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.client.ClientConnection;
import org.kontalk.client.Protocol.ValidationCodeResponse;
import org.kontalk.client.RevalidateJob;
import org.kontalk.client.TxListener;
import org.kontalk.service.ClientThread;
import org.kontalk.service.MessageCenterService;
import org.kontalk.service.MessageCenterService.MessageCenterInterface;
import org.kontalk.service.RequestJob;
import org.kontalk.service.RequestListener;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.google.protobuf.MessageLite;


public class RevalidateActivity extends SherlockActivity implements RequestListener, TxListener {

    private class RevalidateServiceConnection implements ServiceConnection {
        private MessageCenterService service;

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder ibinder) {
            MessageCenterInterface binder = (MessageCenterInterface) ibinder;
            service = binder.getService();
            RevalidateJob job = service.revalidate();
            job.setListener(RevalidateActivity.this);

            try {
                unbindService(this);
            }
            catch (Exception e) {
                // ignore exception on exit
            }
            service = null;
        }
    }

    private RevalidateServiceConnection mConn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.revalidate_screen);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // TODO request verification code through MessageCenterService
        mConn = new RevalidateServiceConnection();
        if (!bindService(new Intent(getApplicationContext(),
                        MessageCenterService.class), mConn,
                        Context.BIND_AUTO_CREATE)) {
            // cannot bind :(
            // mMessageSenderListener.error(conn.job, new
            // IllegalArgumentException("unable to bind to message center"));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unbindService(mConn);
        }
        catch (Exception e) {
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void starting(ClientThread client, RequestJob job) {
    }

    @Override
    public void uploadProgress(ClientThread client, RequestJob job, long bytes) {
    }

    @Override
    public void downloadProgress(ClientThread client, RequestJob job, long bytes) {
    }

    @Override
    public void done(ClientThread client, RequestJob job, String txId) {
        client.setTxListener(txId, this);
    }

    @Override
    public boolean error(ClientThread client, RequestJob job, Throwable exc) {
        // TODO
        return false;
    }

    @Override
    public boolean tx(ClientConnection connection, String txId, MessageLite pack) {
        Log.v("REVAL", "message tx=" + txId + ", pack=" + pack);
        if (pack != null && pack instanceof ValidationCodeResponse) {
            final ValidationCodeResponse res = (ValidationCodeResponse) pack;
            Log.v("REVAL", "validation code: " + res.getCode());

            runOnUiThread(new Runnable() {
                public void run() {
                    ((TextView)findViewById(R.id.validation_code)).setText(res.getCode());
                    findViewById(android.R.id.progress).setVisibility(View.GONE);
                    findViewById(R.id.validation_code).setVisibility(View.VISIBLE);
                }
            });
        }
        return false;
    }

}
