/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

import java.util.Collections;
import java.util.List;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.MenuItem;
import android.view.ViewGroup;

import me.dm7.barcodescanner.zxing.ZXingScannerView;
import pub.devrel.easypermissions.EasyPermissions;

import org.kontalk.R;
import org.kontalk.util.Permissions;


/**
 * Simple activity to scan a barcode and return the result to the caller.
 * @author Daniele Ricci
 */
public class ScanTextActivity extends ToolbarActivity
        implements ZXingScannerView.ResultHandler, EasyPermissions.PermissionCallbacks {

    private ZXingScannerView mScannerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scan_text_screen);

        mScannerView = new ZXingScannerView(this);
        List<BarcodeFormat> formats = Collections.singletonList(BarcodeFormat.QR_CODE);
        mScannerView.setFormats(formats);
        mScannerView.setAspectTolerance(0.5f);

        ViewGroup contentFrame = findViewById(R.id.content);
        contentFrame.addView(mScannerView);

        setupToolbar(true, false);
        setTitle(getIntent().getStringExtra("title"));
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
    protected void onResume() {
        super.onResume();
        mScannerView.setResultHandler(this);

        if (Permissions.canUseCamera(this)) {
            startCamera();
        }
        else {
            Permissions.requestCamera(this, getString(R.string.err_camera_scanner_denied));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCamera();
    }

    @Override
    protected boolean isNormalUpNavigation() {
        return true;
    }

    private void startCamera() {
        mScannerView.startCamera();
    }

    private void stopCamera() {
        mScannerView.stopCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void handleResult(Result result) {
        Intent data = new Intent();
        data.putExtra("text", result.getText());
        setResult(RESULT_OK, data);
        finish();
    }

    public static void start(Activity activity, String title, int requestCode) {
        Intent i = new Intent(activity, ScanTextActivity.class);
        i.putExtra("title", title);
        activity.startActivityForResult(i, requestCode);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        startCamera();
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        finish();
    }
}
