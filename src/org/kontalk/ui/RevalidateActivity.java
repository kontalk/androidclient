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
import org.kontalk.authenticator.Authenticator;
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
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.google.protobuf.MessageLite;


public class RevalidateActivity extends SherlockActivity implements RequestListener, TxListener {

    private ProgressBar mProgress;
    private TextView mCode;
    private Button mRetry;

    private Handler mHandler;
    private Runnable mTimeout;

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

        mProgress = (ProgressBar) findViewById(android.R.id.progress);
        mCode = (TextView) findViewById(R.id.validation_code);
        mRetry = (Button) findViewById(R.id.button_retry);

        // fill account name
        String acc = Authenticator.getDefaultAccountName(this);
        TextView textAccount = (TextView) findViewById(R.id.account);
        textAccount.setText(acc);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mHandler = new Handler();
        mTimeout = new Runnable() {
            public void run() {
                error(null, null, null);
            }
        };
        mConn = new RevalidateServiceConnection();
        retry(null);
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

    public void retry(View v) {
        if (MessageCenterService.isNetworkConnectionAvailable(this)) {

            mRetry.setVisibility(View.GONE);
            mProgress.setVisibility(View.VISIBLE);

            // wait for 30 seconds
            mHandler.postDelayed(mTimeout, 30000);

            if (!bindService(new Intent(getApplicationContext(),
                MessageCenterService.class), mConn,
                Context.BIND_AUTO_CREATE)) {
            // cannot bind :(
            // IllegalArgumentException("unable to bind to message center");
            }
        }
        else {
            Toast.makeText(this, R.string.err_sync_nonetwork, Toast.LENGTH_SHORT).show();
        }
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
        // remove timeout
        mHandler.removeCallbacks(mTimeout);
        // show retry button
        mProgress.setVisibility(View.GONE);
        mCode.setVisibility(View.GONE);
        mRetry.setVisibility(View.VISIBLE);
        return false;
    }

    @Override
    public boolean tx(ClientConnection connection, String txId, MessageLite pack) {
        if (pack != null && pack instanceof ValidationCodeResponse) {
            final ValidationCodeResponse res = (ValidationCodeResponse) pack;
            runOnUiThread(new Runnable() {
                public void run() {
                    // remove timeout
                    mHandler.removeCallbacks(mTimeout);
                    // show code
                    mCode.setText(res.getCode());
                    mProgress.setVisibility(View.GONE);
                    mCode.setVisibility(View.VISIBLE);
                }
            });
        }
        return false;
    }

}
