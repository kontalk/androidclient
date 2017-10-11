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

import com.car2go.maps.AnyMap;
import com.car2go.maps.OnInterceptTouchEvent;
import com.car2go.maps.OnMapReadyCallback;
import com.car2go.maps.google.CameraUpdateFactory;
import com.car2go.maps.google.MapView;
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
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.view.ViewHelper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.util.RecyclerItemClickListener;
import org.kontalk.util.ViewUtils;


/**
 * Send Position Google Maps Fragment
 *
 * @author Andrea Cappelli
 */
public class SendPositionGoogleFragment extends Fragment implements OnMapReadyCallback,
    GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, ResultCallback<LocationSettingsResult> {

    private final static String TAG = SendPositionGoogleFragment.class.getSimpleName();

    private static final int REQUEST_LOCATION = 1;

    GoogleApiClient mGoogleApiClient;

    FrameLayout mMapViewClip;
    private MapView mMapView;
    AnyMap mMap;

    Location mUserLocation;
    Location mMyLocation;

    ImageView mMapPin;
    ImageView mPinX;
    FloatingActionButton mFabMyLocation;

    AnimatorSet mAnimatorSet;

    RecyclerView mRecyclerView;
    RecyclerView mSearchRecyclerView;
    LinearLayoutManager mRecyclerViewLayoutManager;
    SearchPlacesAdapter mSearchAdapter;
    PlacesAdapter mAdapter;

    boolean mUserLocationMoved = false;

    private int mOverScrollHeight;
    int mMarkerTop;

    private LocationRequest mLocationRequest;
    /** This will be non-null if we were unable to obtain a location. */
    private Status mLastStatus;


    private static final long UPDATE_INTERVAL = 20 * 1000;  /* 20 secs */
    private static final long FASTEST_INTERVAL = 4000; /* 4 secs */

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(getContext())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        }
    }

    @SuppressLint("WrongViewCast")
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_send_position_google, container, false);

        mMapViewClip = view.findViewById(R.id.mapview_clip);

        mMapView = view.findViewById(R.id.mapView);

        mMapPin = view.findViewById(R.id.map_pin);
        mPinX = view.findViewById(R.id.pin_x);
        ViewHelper.setAlpha(mPinX, 0.0f);

        mRecyclerView = view.findViewById(R.id.recyclerView);
        mSearchRecyclerView = view.findViewById(R.id.search_recyclerView);

        mFabMyLocation = view.findViewById(R.id.fab_my_position);

        mMapView.onCreate(savedInstanceState);

        return view;
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
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

                    mAdapter.setCustomLocation(mUserLocation);
                }
            }
        });

        mFabMyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMyLocation != null && mMap != null && mGoogleApiClient.isConnected() && isLocationEnabled()) {
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.setDuration(200);
                    animatorSet.play(ObjectAnimator.ofFloat(mFabMyLocation, "alpha", 0.0f));
                    animatorSet.start();
                    mAdapter.setCustomLocation(null);
                    mUserLocationMoved = false;
                    mMap.animateCamera(CameraUpdateFactory.getInstance().newLatLngZoom(new LatLng(mMyLocation.getLatitude(), mMyLocation.getLongitude()), 12));
                }
            }
        });

        mAdapter = new PlacesAdapter(getContext());
        mSearchAdapter = new SearchPlacesAdapter(getContext());

        mRecyclerViewLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        LinearLayoutManager searchRecyclerViewLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);

        mRecyclerView.setLayoutManager(mRecyclerViewLayoutManager);
        mRecyclerView.setAdapter(mAdapter);

        mSearchRecyclerView.setLayoutManager(searchRecyclerViewLayoutManager);
        mSearchRecyclerView.setAdapter(mSearchAdapter);

        mRecyclerView.addOnItemTouchListener(new RecyclerItemClickListener(getContext(), new RecyclerItemClickListener.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (position == 1) {
                    Position p = new Position(mUserLocation.getLatitude(), mUserLocation.getLongitude());
                    Intent intent = new Intent();
                    intent.putExtra("position", p);
                    getActivity().setResult(Activity.RESULT_OK, intent);
                    getActivity().finish();
                }
                else {
                    Position item = mAdapter.getVenuesItem(position);
                    if (item != null) {
                        Intent intent = new Intent();
                        intent.putExtra("position", item);
                        getActivity().setResult(Activity.RESULT_OK, intent);
                        getActivity().finish();
                    }
                }
            }
        }));

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (mAdapter.getItemCount() == 0) {
                    return;
                }
                int position = mRecyclerViewLayoutManager.findFirstVisibleItemPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                updateClipView(position);
            }
        });

        mSearchRecyclerView.addOnItemTouchListener(new RecyclerItemClickListener(getContext(), new RecyclerItemClickListener.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                Position item = mSearchAdapter.getVenuesItem(position);
                if (item != null) {
                    Intent intent = new Intent();
                    intent.putExtra("position", item);
                    getActivity().setResult(Activity.RESULT_OK, intent);
                    getActivity().finish();
                }
            }
        }));

        ViewHelper.setAlpha(mFabMyLocation, 0.0f);
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

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();

        if (getView().getMeasuredHeight() == 0)
            getView().post(new Runnable() {
                @Override
                public void run() {
                    prepareLayout(true);
                }
            });
        else
            prepareLayout(true);
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
        MenuItem searchItem = menu.findItem(R.id.menu_search);
        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mRecyclerView.setVisibility(View.GONE);
                mMapViewClip.setVisibility(View.GONE);
                mSearchRecyclerView.setVisibility(View.VISIBLE);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mRecyclerView.setVisibility(View.VISIBLE);
                mMapViewClip.setVisibility(View.VISIBLE);
                mSearchAdapter.searchPlacesWithQuery(null, null);
                mSearchRecyclerView.setVisibility(View.GONE);
                return true;
            }
        });
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                mSearchAdapter.searchPlacesWithQuery(query, mUserLocation);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (super.onOptionsItemSelected(item))
            return true;

        switch (item.getItemId()) {
            case R.id.map:
                if (!item.isChecked()) {
                    item.setChecked(true);
                    mMap.setMapType(AnyMap.Type.NORMAL);
                }
                return true;

            case R.id.satellite:
                if (!item.isChecked()) {
                    item.setChecked(true);
                    mMap.setMapType(AnyMap.Type.SATELLITE);
                }
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

    @Override
    public void onLocationChanged(Location location) {
        if (mMap != null) {
            positionMarker(location);
        }
    }

    @Override
    public void onMapReady(final AnyMap anyMap) {
        mMap = anyMap;
        anyMap.setMyLocationEnabled(true);
        anyMap.getUiSettings().setMyLocationButtonEnabled(false);
        anyMap.getUiSettings().setMapToolbarEnabled(false);
        anyMap.getUiSettings().setCompassEnabled(false);
    }

    private void positionMarker(Location location) {
        if (location == null) {
            return;
        }
        mMyLocation = new Location(location);

        //TODO

        if (mAdapter != null) {
            mAdapter.searchPlaces(mMyLocation);
            mAdapter.setGpsPosition(mMyLocation);
        }

        if (!mUserLocationMoved) {
            mUserLocation = new Location(location);

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            mMap.moveCamera(CameraUpdateFactory.getInstance().newLatLngZoom(latLng, 12));

        }
    }

    void prepareLayout(final boolean resume) {
        if (mRecyclerView != null) {
            int viewHeight = getView().getMeasuredHeight();
            if (viewHeight == 0) {
                return;
            }
            mOverScrollHeight = viewHeight - ViewUtils.dp(getContext(), 66);

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mMapViewClip.getLayoutParams();
            layoutParams.height = mOverScrollHeight;
            mMapViewClip.setLayoutParams(layoutParams);

            mAdapter.setOverScrollHeight(mOverScrollHeight);
            layoutParams = (FrameLayout.LayoutParams) mMapView.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.height = mOverScrollHeight + ViewUtils.dp(getContext(), 10);
                if (mMap != null) {
                    mMap.setPadding(0, 0, 0, ViewUtils.dp(getContext(), 10));
                }
                mMapView.setLayoutParams(layoutParams);
            }
            mAdapter.notifyDataSetChanged();

            if (resume) {
                mRecyclerViewLayoutManager.scrollToPositionWithOffset(0, -(int) (ViewUtils.dp(getContext(), 56) * 2.5f + ViewUtils.dp(getContext(), 36 + 66)));
                updateClipView(mRecyclerViewLayoutManager.findFirstVisibleItemPosition());
                mRecyclerView.post(new Runnable() {
                    @Override
                    public void run() {
                        mRecyclerViewLayoutManager.scrollToPositionWithOffset(0, -(int) (ViewUtils.dp(getContext(), 56) * 2.5f + ViewUtils.dp(getContext(), 36 + 66)));
                        updateClipView(mRecyclerViewLayoutManager.findFirstVisibleItemPosition());
                    }
                });
            }
            else {
                updateClipView(mRecyclerViewLayoutManager.findFirstVisibleItemPosition());
            }

        }
    }

    void updateClipView(int firstVisibleItem) {
        if (firstVisibleItem == RecyclerView.NO_POSITION) {
            return;
        }
        int height = 0;
        int top = 0;
        View child = mRecyclerView.getChildAt(0);
        if (child != null) {
            if (firstVisibleItem == 0) {
                top = child.getTop();
                height = mOverScrollHeight + (top < 0 ? top : 0);
            }
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mMapViewClip.getLayoutParams();
            if (layoutParams != null) {
                if (height <= 0) {
                    if (mMapView.getVisibility() == View.VISIBLE) {
                        mMapView.setVisibility(View.INVISIBLE);
                        mMapViewClip.setVisibility(View.INVISIBLE);
                    }
                }
                else {
                    if (mMapView.getVisibility() == View.INVISIBLE) {
                        mMapView.setVisibility(View.VISIBLE);
                        mMapViewClip.setVisibility(View.VISIBLE);
                    }
                }

                ViewHelper.setTranslationY(mMapViewClip, Math.min(0, top));
                ViewHelper.setTranslationY(mMapView, Math.max(0, -top / 2));
                ViewHelper.setTranslationY(mMapPin, mMarkerTop = -top - ViewUtils.dp(getContext(), 42) + height / 2);
                ViewHelper.setTranslationY(mPinX, -top - ViewUtils.dp(getContext(), 7) + height / 2);

                layoutParams = (FrameLayout.LayoutParams) mMapView.getLayoutParams();
                if (layoutParams != null && layoutParams.height != mOverScrollHeight + ViewUtils.dp(getContext(), 10)) {
                    layoutParams.height = mOverScrollHeight + ViewUtils.dp(getContext(), 10);
                    if (mMap != null) {
                        mMap.setPadding(0, 0, 0, ViewUtils.dp(getContext(), 10));
                    }
                    mMapView.setLayoutParams(layoutParams);
                }
            }
        }
    }
}
