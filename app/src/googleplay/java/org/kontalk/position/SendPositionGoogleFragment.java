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

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.car2go.maps.google.CameraUpdateFactory;
import com.car2go.maps.google.MapsConfiguration;
import com.car2go.maps.model.LatLng;
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

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.util.ViewUtils;


/**
 * Send Position Google Maps Fragment
 *
 * @author Andrea Cappelli
 */
public class SendPositionGoogleFragment extends SendPositionAbstractFragment implements
    GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, ResultCallback<LocationSettingsResult> {

    private final static String TAG = SendPositionGoogleFragment.class.getSimpleName();

    private static final int REQUEST_LOCATION = 1;

    private GoogleApiClient mGoogleApiClient;

    private AnimatorSet mAnimatorSet;

    private LocationRequest mLocationRequest;
    /** This will be non-null if we were unable to obtain a location. */
    private Status mLastStatus;

    private static final long UPDATE_INTERVAL = 20 * 1000;  /* 20 secs */
    private static final long FASTEST_INTERVAL = 4000; /* 4 secs */

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
        return inflater.inflate(R.layout.fragment_send_position_google, container, false);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        MapsConfiguration.getInstance().initialize(getContext());

        return view;
    }

    @Override
    protected boolean isPlacesEnabled() {
        return true;
    }

    @Override
    protected CameraUpdateFactory getCameraUpdateFactory() {
        return com.car2go.maps.google.CameraUpdateFactory.getInstance();
    }

    @Override
    protected void onFabClicked(Location location) {
        setCustomLocation(null);
    }

    @Override
    protected void onMapTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (mAnimatorSet != null) {
                mAnimatorSet.cancel();
            }
            mAnimatorSet = new AnimatorSet();
            mAnimatorSet.setDuration(200);
            mAnimatorSet.playTogether(
                ObjectAnimator.ofFloat(mMapPin, "translationY", mMarkerTop + -ViewUtils.dp(getContext(), 10)),
                ObjectAnimator.ofFloat(mPinX, "alpha", 1.0f));
            mAnimatorSet.start();
        }
        else if (ev.getAction() == MotionEvent.ACTION_UP) {
            if (mAnimatorSet != null) {
                mAnimatorSet.cancel();
            }
            mAnimatorSet = new AnimatorSet();
            mAnimatorSet.setDuration(200);
            mAnimatorSet.playTogether(
                ObjectAnimator.ofFloat(mMapPin, "translationY", mMarkerTop),
                ObjectAnimator.ofFloat(mPinX, "alpha", 0.0f));
            mAnimatorSet.start();
        }
        if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            if (!mUserLocationMoved) {
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.setDuration(200);
                animatorSet.play(ObjectAnimator.ofFloat(mFabMyLocation, "alpha", 1.0f));
                animatorSet.start();
                mUserLocationMoved = true;
            }

            if (mMap != null && mMyLocation != null) {
                mUserLocation.setLatitude(mMap.getCameraPosition().target.latitude);
                mUserLocation.setLongitude(mMap.getCameraPosition().target.longitude);
            }

            setCustomLocation(mUserLocation);
        }
    }

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

    @Override
    public void onStart() {
        super.onStart();
        // Disconnecting the client invalidates it.
        mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }

    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
            mLocationRequest, this);
    }

    private void requestAndPollLastLocation() {
        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        // Note that this can be NULL if last location isn't already known.
        if (lastLocation != null) {
            // Print current location if not null
            Log.d(TAG, "last location: " + lastLocation.toString());
            LatLng latLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
            mMyLocation.setLatitude(lastLocation.getLatitude());
            mMyLocation.setLongitude(lastLocation.getLongitude());
            if (mMap != null)
                mMap.animateCamera(CameraUpdateFactory.getInstance().newLatLngZoom(latLng, 12));
        }
        // Begin polling for new location updates.
        startLocationUpdates();
    }

    @Override
    public void onResult(@NonNull LocationSettingsResult result) {
        if (result.getStatus().isSuccess()) {
            requestAndPollLastLocation();
        }
        else {
            mLastStatus = result.getStatus();
            // this will trigger the location services dialog
            isLocationEnabled();
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
                case Activity.RESULT_CANCELED:
                    // try to request location anyway
                    requestAndPollLastLocation();
                    break;
            }
        }
    }

}
