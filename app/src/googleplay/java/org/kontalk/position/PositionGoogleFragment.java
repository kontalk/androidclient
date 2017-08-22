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

import com.amulyakhare.textdrawable.TextDrawable;
import com.car2go.maps.AnyMap;
import com.car2go.maps.OnMapReadyCallback;
import com.car2go.maps.google.BitmapDescriptorFactory;
import com.car2go.maps.google.CameraUpdateFactory;
import com.car2go.maps.google.MapView;
import com.car2go.maps.model.LatLng;
import com.car2go.maps.model.MarkerOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.hdodenhof.circleimageview.CircleImageView;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.ui.PositionActivity;
import org.kontalk.ui.ToolbarActivity;


/**
 * Position Google Maps fragment
 *
 * @author Andrea Cappelli
 */
public class PositionGoogleFragment extends Fragment implements OnMapReadyCallback,
    GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private final static String TAG = SendPositionGoogleFragment.class.getSimpleName();

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    private MapView mMapView;
    private AnyMap mMap;

    private Position mPosition;
    private Location mUserLocation;
    private Location mMyLocation;

    private FloatingActionButton mFabMyLocation;
    private FloatingActionButton mFabRoute;

    private View mBottomView;
    private CircleImageView mUserAvatar;
    private TextView mUserName;
    private TextView mDistance;

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

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_position_google, container, false);

        mMapView = ((MapView) view.findViewById(R.id.mapView));

        mBottomView = view.findViewById(R.id.bottom_view);
        mUserAvatar = (CircleImageView) view.findViewById(R.id.avatar);
        mUserName = (TextView) view.findViewById(R.id.user_text_name);
        mDistance = (TextView) view.findViewById(R.id.user_text_position);

        mFabRoute = (FloatingActionButton) view.findViewById(R.id.fab_route);

        mFabMyLocation = (FloatingActionButton) view.findViewById(R.id.fab_my_position);

        mMapView.onCreate(savedInstanceState);

        return view;
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMyLocation = new Location("network");

        mMapView.getMapAsync(this);

        String userId = getArguments().getString(PositionActivity.EXTRA_USERID);

        if (userId != null) {
            Contact contact = Contact.findByUserId(getContext(), userId);
            mUserAvatar.setImageDrawable(contact.getAvatar(getContext()));
            mUserName.setText(contact.getDisplayName());
        }
        else {
            mUserName.setText(R.string.your_position);
            TextDrawable drawable = TextDrawable.builder()
                .buildRound("Y", ContextCompat.getColor(getContext(), R.color.app_primary));
            mUserAvatar.setImageDrawable(drawable);
        }

        mPosition = (Position) getArguments().getSerializable(PositionActivity.EXTRA_USERPOSITION);

        if (!TextUtils.isEmpty(mPosition.getName())) {
            ((ToolbarActivity) getActivity()).getSupportActionBar().setTitle(mPosition.getName());
            ((ToolbarActivity) getActivity()).getSupportActionBar().setSubtitle(mPosition.getAddress());
        }


        mUserLocation = new Location("network");
        mUserLocation.setLatitude(mPosition.getLatitude());
        mUserLocation.setLongitude(mPosition.getLongitude());

        mBottomView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mUserLocation != null) {
                    LatLng latLng = new LatLng(mUserLocation.getLatitude(), mUserLocation.getLongitude());
                    if (mMap != null) {
                        mMap.animateCamera(CameraUpdateFactory.getInstance().newLatLngZoom(latLng, 12));
                    }
                }
            }
        });

        mFabMyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMyLocation != null && mMap != null && mGoogleApiClient.isConnected()) {
                    mMap.animateCamera(CameraUpdateFactory.getInstance().newLatLngZoom(new LatLng(mMyLocation.getLatitude(), mMyLocation.getLongitude()), 12));
                }
            }
        });

        mFabRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMyLocation != null) {
                    try {
                        double lat = mPosition.getLatitude();
                        double lon = mPosition.getLongitude();
                        startActivity(new Intent(android.content.Intent.ACTION_VIEW,
                            Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon)));
                    }
                    catch (Exception e) {
                        Log.e(TAG, e.getLocalizedMessage());
                    }
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

        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
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

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.position_menu, menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (super.onOptionsItemSelected(item))
            return true;

        switch (item.getItemId()) {
            case R.id.menu_share: {
                try {
                    double lat = mPosition.getLatitude();
                    double lon = mPosition.getLongitude();
                    startActivity(new Intent(android.content.Intent.ACTION_VIEW,
                        Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon)));
                }
                catch (Exception e) {
                    Log.e(TAG, e.getLocalizedMessage());
                }
                break;
            }
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
        // Create the location request
        mLocationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(UPDATE_INTERVAL)
            .setFastestInterval(FASTEST_INTERVAL);
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
            mMyLocation.setLatitude(lastLocation.getLatitude());
            mMyLocation.setLongitude(lastLocation.getLongitude());
        }
        // Begin polling for new location updates.
        startLocationUpdates();
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

        if (mPosition != null) {
            LatLng latLng = new LatLng(mUserLocation.getLatitude(), mUserLocation.getLongitude());
            try {
                mMap.addMarker(new MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.getInstance().fromResource(R.drawable.ic_map_pin)));
            }
            catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
            mMap.moveCamera(CameraUpdateFactory.getInstance().newLatLngZoom(latLng, 12));
        }
    }

    private void positionMarker(Location location) {
        if (location == null) {
            return;
        }
        mMyLocation = new Location(location);

        if (mUserLocation != null && mDistance != null) {
            float distance = location.distanceTo(mUserLocation);
            if (distance < 1000) {
                mDistance.setText(getString(R.string.meters_away, (int) (distance)));
            }
            else {
                mDistance.setText(getString(R.string.kilometers_away, distance / 1000.0f));
            }
        }
    }

}

