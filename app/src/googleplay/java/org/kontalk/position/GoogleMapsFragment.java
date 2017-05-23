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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.car2go.maps.AnyMap;
import com.car2go.maps.OnInterceptTouchEvent;
import com.car2go.maps.OnMapReadyCallback;
import com.car2go.maps.google.CameraUpdateFactory;
import com.car2go.maps.google.MapView;
import com.car2go.maps.model.LatLng;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;

import org.kontalk.Log;
import org.kontalk.R;

import java.util.Locale;

/**
 * Google Maps Fragment
 *
 * @author andreacappelli
 */

public class GoogleMapsFragment extends Fragment implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private final static String TAG = GoogleMapsFragment.class.getName();

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    private MapView mMapView;
    private AnyMap mGoogleMap;

    private Location mUserLocation;

    private ImageView mMapPin;
    private FloatingActionButton mFabMyLocation;

    private TextView mTextPosition;

    private AnimatorSet mAnimatorSet;

    private ImageView mImageSendPosition;

    private static final long UPDATE_INTERVAL = 10 * 1000;  /* 10 secs */
    private static final long FASTEST_INTERVAL = 2000; /* 2 sec */


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

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_google, container, false);

        mMapView = ((MapView) view.findViewById(R.id.mapView));

        mMapPin = (ImageView) view.findViewById(R.id.map_pin);

        mImageSendPosition = (ImageView) view.findViewById(R.id.image_position);

        mTextPosition = (TextView) view.findViewById(R.id.text_position);

        mFabMyLocation = (FloatingActionButton) view.findViewById(R.id.fab_my_position);

        mMapView.onCreate(savedInstanceState);

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mUserLocation = new Location("network");

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
                            ObjectAnimator.ofFloat(mMapPin, "scaleX", 1.0f, 1.2f),
                            ObjectAnimator.ofFloat(mMapPin, "scaleY", 1.0f, 1.2f));
                    mAnimatorSet.start();
                } else if (ev.getAction() == MotionEvent.ACTION_UP) {
                    if (mAnimatorSet != null) {
                        mAnimatorSet.cancel();
                    }
                    mAnimatorSet = new AnimatorSet();
                    mAnimatorSet.setDuration(200);
                    mAnimatorSet.playTogether(
                            ObjectAnimator.ofFloat(mMapPin, "scaleX", 1.2f, 1.0f),
                            ObjectAnimator.ofFloat(mMapPin, "scaleY", 1.2f, 1.0f));
                    mAnimatorSet.start();
                }
                if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                    /*if (!userLocationMoved) {
                        AnimatorSet animatorSet = new AnimatorSet();
                        animatorSet.setDuration(200);
                        animatorSet.play(ObjectAnimator.ofFloat(locationButton, "alpha", 1.0f));
                        animatorSet.start();
                        userLocationMoved = true;
                    }
                    if (googleMap != null && userLocation != null) {
                        userLocation.setLatitude(googleMap.getCameraPosition().target.latitude);
                        userLocation.setLongitude(googleMap.getCameraPosition().target.longitude);
                    }
                    adapter.setCustomLocation(userLocation);*/
                    if (mGoogleMap != null && mUserLocation != null) {
                        mUserLocation.setLatitude(mGoogleMap.getCameraPosition().target.latitude);
                        mUserLocation.setLongitude(mGoogleMap.getCameraPosition().target.longitude);
                        mTextPosition.setText(String.format(Locale.US, "(%f, %f)", mUserLocation.getLatitude(), mUserLocation.getLongitude()));
                    }
                }
            }
        });

        mFabMyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mGoogleMap != null && mGoogleApiClient.isConnected()) {
                    positionMarker(LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient));
                }
            }
        });

        mImageSendPosition.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mUserLocation != null) {
                    Intent intent = new Intent();
                    intent.putExtra("location", mUserLocation);
                    getActivity().setResult(Activity.RESULT_OK, intent);
                    getActivity().finish();
                }
            }
        });
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

        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);

        if( mGoogleApiClient != null && mGoogleApiClient.isConnected() ) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
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

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (ActivityCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION}, 2);
            }
        }
    }

    protected void startLocationUpdates() {
        // Create the location request
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Request location updates
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,
                mLocationRequest, this);
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        // Note that this can be NULL if last location isn't already known.
        if (lastLocation != null) {
            // Print current location if not null
            Log.d(TAG, "last location: " + lastLocation.toString());
            LatLng latLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
            mUserLocation.setLatitude(lastLocation.getLatitude());
            mUserLocation.setLongitude(lastLocation.getLongitude());
            if (mGoogleMap != null)
                mGoogleMap.animateCamera(CameraUpdateFactory.getInstance().newLatLngZoom(latLng, 12));
        }
        // Begin polling for new location updates.
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (i == CAUSE_SERVICE_DISCONNECTED) {
            Log.d(TAG, "Disconnected. Please re-connect.");
        } else if (i == CAUSE_NETWORK_LOST) {
            Log.d(TAG, "Network lost. Please re-connect.");
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        if (mGoogleMap != null) {
            positionMarker(location);
        }
    }

    @Override
    public void onMapReady(final AnyMap anyMap) {
        mGoogleMap = anyMap;
        anyMap.setMyLocationEnabled(true);
        anyMap.getUiSettings().setMyLocationButtonEnabled(false);
        anyMap.getUiSettings().setMapToolbarEnabled(false);
        anyMap.getUiSettings().setCompassEnabled(false);
    }

    private void positionMarker(Location location) {
        if (location == null) {
            return;
        }

        mUserLocation = new Location(location);

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        mGoogleMap.moveCamera(CameraUpdateFactory.getInstance().newLatLngZoom(latLng, 12));

        mTextPosition.setText(String.format(Locale.US, "(%f, %f)", location.getLatitude(), location.getLongitude()));
    }

}
