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

import java.util.Locale;

import com.car2go.maps.AnyMap;
import com.car2go.maps.OnInterceptTouchEvent;
import com.car2go.maps.OnMapReadyCallback;
import com.car2go.maps.model.LatLng;
import com.car2go.maps.osm.CameraUpdateFactory;
import com.car2go.maps.osm.MapView;
import com.car2go.maps.osm.MapsConfiguration;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.view.ViewHelper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.util.ViewUtils;

/**
 * Send position OpenStreetMaps Fragment
 *
 * @author andreacappelli
 */

public class SendPositionOsmFragment extends Fragment implements OnMapReadyCallback, LocationListener {

    private final static String TAG = SendPositionOsmFragment.class.getSimpleName();

    private LocationManager mLocationManager;

    private RelativeLayout mMapViewClip;
    private MapView mMapView;
    private AnyMap mGoogleMap;

    private Location mUserLocation;
    private Location mMyLocation;

    private ImageView mMapPin;
    private ImageView mPinX;
    private FloatingActionButton mFabMyLocation;

    private SendLocationRow mSendLocationRow;

    private AnimatorSet mAnimatorSet;

    private boolean mUserLocationMoved = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mLocationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_send_position_osm, container, false);

        mMapView = (MapView) view.findViewById(R.id.mapView);

        mMapViewClip = (RelativeLayout) view.findViewById(R.id.mapview_clip);

        mMapPin = (ImageView) view.findViewById(R.id.map_pin);
        mPinX = (ImageView) view.findViewById(R.id.pin_x);
        ViewHelper.setAlpha(mPinX, 0.0f);

        mSendLocationRow = (SendLocationRow) view.findViewById(R.id.send_location);

        mFabMyLocation = (FloatingActionButton) view.findViewById(R.id.fab_my_position);

        mMapView.onCreate(savedInstanceState);

        MapsConfiguration.getInstance().initialize(getContext());

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mUserLocation = new Location("network");
        mUserLocation.setLatitude(41.8508384);
        mUserLocation.setLongitude(11.9545216);

        mMyLocation = new Location("network");

        mMapView.getMapAsync(this);

        mMapView.setOnInterceptTouchEventListener(new OnInterceptTouchEvent() {
            @Override
            public void onInterceptTouchEvent(MotionEvent ev) {
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

                    if (mGoogleMap != null && mMyLocation != null) {
                        mUserLocation.setLatitude(mGoogleMap.getCameraPosition().target.latitude);
                        mUserLocation.setLongitude(mGoogleMap.getCameraPosition().target.longitude);
                    }

                    setCustomLocation(mUserLocation);
                }
            }
        });

        mFabMyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMyLocation != null && mGoogleMap != null) {
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.setDuration(200);
                    animatorSet.play(ObjectAnimator.ofFloat(mFabMyLocation, "alpha", 0.0f));
                    animatorSet.start();
                    setGpsPosition(mMyLocation);
                    mUserLocationMoved = false;
                    mGoogleMap.animateCamera(CameraUpdateFactory.getInstance().newLatLngZoom(new LatLng(mMyLocation.getLatitude(), mMyLocation.getLongitude()), 16));
                }
            }
        });

        mSendLocationRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Position position = new Position(mUserLocation.getLatitude(), mUserLocation.getLongitude());
                Intent intent = new Intent();
                intent.putExtra("position", position);
                getActivity().setResult(Activity.RESULT_OK, intent);
                getActivity().finish();
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
        mLocationManager.removeUpdates(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.send_position_menu, menu);
        menu.removeItem(R.id.menu_search);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (super.onOptionsItemSelected(item))
            return true;

        switch (item.getItemId()) {
            case R.id.map:
                mGoogleMap.setMapType(AnyMap.Type.NORMAL);
                return true;

            case R.id.satellite:
                mGoogleMap.setMapType(AnyMap.Type.SATELLITE);
                return true;
        }

        return false;
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (ActivityCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(),
                    new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION}, 2);
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (mGoogleMap != null) {
            positionMarker(location);
        }
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
        mGoogleMap = anyMap;
        anyMap.setMyLocationEnabled(true);
        anyMap.getUiSettings().setMyLocationButtonEnabled(false);
        anyMap.getUiSettings().setMapToolbarEnabled(false);
        anyMap.getUiSettings().setCompassEnabled(false);

        Location lastLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        // Note that this can be NULL if last location isn't already known.
        if (lastLocation != null) {
            // Print current location if not null
            Log.d(TAG, "last location: " + lastLocation.toString());
            LatLng latLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
            mMyLocation.setLatitude(lastLocation.getLatitude());
            mMyLocation.setLongitude(lastLocation.getLongitude());
            if (mGoogleMap != null)
                mGoogleMap.animateCamera(CameraUpdateFactory.getInstance().newLatLngZoom(latLng, 16));
        }
    }

    private void positionMarker(Location location) {
        if (location == null) {
            return;
        }
        mMyLocation = new Location(location);

        setGpsPosition(mMyLocation);

        if (!mUserLocationMoved) {
            mUserLocation = new Location(location);

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            mGoogleMap.moveCamera(CameraUpdateFactory.getInstance().newLatLngZoom(latLng, 16));

        }
    }

    public void setCustomLocation(Location location) {
        mSendLocationRow.setText(getString(R.string.send_selected_location),
            String.format(Locale.US, "(%f, %f)", location.getLatitude(), location.getLongitude()));
    }

    public void setGpsPosition(Location location) {
        mSendLocationRow.setText(getString(R.string.send_location),
            getString(R.string.accurate_to, String.valueOf((int) location.getAccuracy())));
    }
}
