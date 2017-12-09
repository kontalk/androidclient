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

package org.kontalk.position;

import com.car2go.maps.CameraUpdateFactory;
import com.car2go.maps.google.BitmapDescriptorFactory;
import com.car2go.maps.google.MapsConfiguration;
import com.car2go.maps.model.BitmapDescriptor;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.ui.ToolbarActivity;


/**
 * Position Google Maps fragment
 *
 * @author Andrea Cappelli
 */
public class PositionGoogleFragment extends PositionAbstractFragment implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, ResultCallback<LocationSettingsResult> {

    private final static String TAG = SendPositionGoogleFragment.class.getSimpleName();
    private static final int REQUEST_LOCATION = 1;

    private GoogleApiClient mGoogleApiClient;

    private static final long UPDATE_INTERVAL = 20 * 1000;  /* 20 secs */
    private static final long FASTEST_INTERVAL = 4000; /* 4 secs */
    private LocationRequest mLocationRequest;
    /** This will be non-null if we were unable to obtain a location. */
    private Status mLastStatus;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(getContext())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        }
    }

    @Override
    protected View onInflateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_position_google, container, false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        MapsConfiguration.getInstance().initialize(getContext());

        return view;
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!TextUtils.isEmpty(mPosition.getName())) {
            ActionBar actionBar = ((ToolbarActivity) getActivity()).getSupportActionBar();
            actionBar.setTitle(mPosition.getName());
            actionBar.setSubtitle(mPosition.getAddress());
        }
    }

    @Override
    protected CameraUpdateFactory getCameraUpdateFactory() {
        return com.car2go.maps.google.CameraUpdateFactory.getInstance();
    }

    @Override
    protected BitmapDescriptor createMarkerBitmap() {
        return BitmapDescriptorFactory.getInstance().fromResource(R.drawable.ic_map_pin_google);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void requestLocation() {
        // we have permission to begin!
        mGoogleApiClient.connect();
    }

    @Override
    protected boolean isLocationEnabled() {
        if (mLastStatus != null) {
            if (mLastStatus.hasResolution()) {
                try {
                    startIntentSenderForResult(mLastStatus.getResolution().getIntentSender(),
                        REQUEST_LOCATION, null, 0, 0, 0, null);
                }
                catch (IntentSender.SendIntentException e) {
                    Toast.makeText(getContext(), R.string.err_location_access_unknown_error,
                        Toast.LENGTH_LONG).show();
                }
            }
            else if (mLastStatus.getStatusCode() == LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE) {
                // no location no party!
                Toast.makeText(getContext(), R.string.err_location_access_unknown_error,
                    Toast.LENGTH_LONG).show();
            }
            return false;
        }
        return true;
    }

    private void startLocationUpdates() {
        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                mLocationRequest, this);
        }
        catch (SecurityException e) {
            Toast.makeText(getContext(), R.string.err_location_permission,
                Toast.LENGTH_LONG).show();
        }
    }

    private void requestAndPollLastLocation() {
        Location lastLocation = null;
        boolean permissionDenied = false;
        try {
            lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        }
        catch (SecurityException e) {
            permissionDenied = true;
            Toast.makeText(getContext(), R.string.err_location_permission,
                Toast.LENGTH_LONG).show();
        }

        // Note that this can be NULL if last location isn't already known.
        if (lastLocation != null) {
            // Print current location if not null
            Log.d(TAG, "last location: " + lastLocation.toString());
            mMyLocation.setLatitude(lastLocation.getLatitude());
            mMyLocation.setLongitude(lastLocation.getLongitude());
        }
        // Begin polling for new location updates.
        if (!permissionDenied)
            startLocationUpdates();
    }

    @Override
    public void onResult(@NonNull LocationSettingsResult result) {
        if (result.getStatus().isSuccess()) {
            requestAndPollLastLocation();
        }
        else {
            mLastStatus = result.getStatus();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        requestLocation(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void requestLocation(int accuracy) {
        // this will be our location request
        mLocationRequest = LocationRequest.create()
            .setPriority(accuracy)
            .setInterval(UPDATE_INTERVAL)
            .setFastestInterval(FASTEST_INTERVAL);

        // check for location settings now
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
            .addLocationRequest(mLocationRequest);
        PendingResult<LocationSettingsResult> pendingResult = LocationServices
            .SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
        pendingResult.setResultCallback(this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (i == CAUSE_SERVICE_DISCONNECTED) {
            Log.d(TAG, "Disconnected. Please re-connect.");
        }
        else if (i == CAUSE_NETWORK_LOST) {
            Log.d(TAG, "Network lost. Please re-connect.");
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LOCATION) {
            mLastStatus = null;

            switch (resultCode) {
                case Activity.RESULT_OK:
                    requestAndPollLastLocation();
                    break;
                case Activity.RESULT_CANCELED:
                    // try again with low power (i.e. network only)
                    requestLocation(LocationRequest.PRIORITY_LOW_POWER);
                    break;
            }
        }
    }
}

