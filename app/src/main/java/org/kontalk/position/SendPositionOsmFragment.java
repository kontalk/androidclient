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

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.car2go.maps.AnyMap;
import com.car2go.maps.model.LatLng;
import com.car2go.maps.osm.CameraUpdateFactory;
import com.car2go.maps.osm.MapsConfiguration;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.util.ViewUtils;


/**
 * Send position OpenStreetMaps Fragment
 *
 * @author Andrea Cappelli
 */
public class SendPositionOsmFragment extends SendPositionAbstractFragment implements LocationListener {

    private final static String TAG = SendPositionOsmFragment.class.getSimpleName();

    private LocationManager mLocationManager;

    private AnimatorSet mAnimatorSet;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLocationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    protected View onInflateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_send_position_osm, container, false);
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
        return false;
    }

    @Override
    protected void onFabClicked(Location location) {
        setGpsPosition(location);
    }

    @Override
    protected CameraUpdateFactory getCameraUpdateFactory() {
        return com.car2go.maps.osm.CameraUpdateFactory.getInstance();
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
                ObjectAnimator.ofFloat(mMapPin, "translationY", -ViewUtils.dp(getContext(), 10)),
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
                ObjectAnimator.ofFloat(mMapPin, "translationY", 0),
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
        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            needLocation();
            return false;
        }
        return true;
    }

    private void needLocation() {
        new MaterialDialog.Builder(getContext())
            .content(R.string.msg_location_disabled)
            .positiveText(android.R.string.ok)
            .negativeText(android.R.string.cancel)
            .onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    Intent locationSettings = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(locationSettings);
                }
            })
            .show();
    }

    @Override
    public void onPause() {
        super.onPause();
        mLocationManager.removeUpdates(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        // this will trigger a dialog to ask for location
        isLocationEnabled();

        boolean hasProvider = false;
        try {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            hasProvider = true;
        }
        catch (IllegalArgumentException e) {
            // no gps available
        }
        try {
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
            hasProvider = true;
        }
        catch (IllegalArgumentException e) {
            // no network location available
        }

        if (!hasProvider) {
            Toast.makeText(getContext(), R.string.err_location_no_providers,
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        // OSM doesn't have satellite
        menu.removeItem(R.id.satellite);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    @Override
    public void onMapReady(final AnyMap anyMap) {
        super.onMapReady(anyMap);

        Location lastLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        // Note that this can be NULL if last location isn't already known.
        if (lastLocation != null) {
            // Print current location if not null
            Log.d(TAG, "last location: " + lastLocation.toString());
            LatLng latLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
            mMyLocation.setLatitude(lastLocation.getLatitude());
            mMyLocation.setLongitude(lastLocation.getLongitude());
            if (mMap != null)
                mMap.animateCamera(CameraUpdateFactory.getInstance().newLatLngZoom(latLng, 16));
        }
    }
}
