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

import com.afollestad.assent.Assent;
import com.afollestad.assent.AssentCallback;
import com.afollestad.assent.PermissionResultSet;
import com.amulyakhare.textdrawable.TextDrawable;
import com.car2go.maps.AnyMap;
import com.car2go.maps.CameraUpdateFactory;
import com.car2go.maps.MapContainerView;
import com.car2go.maps.OnMapReadyCallback;
import com.car2go.maps.model.BitmapDescriptor;
import com.car2go.maps.model.LatLng;
import com.car2go.maps.model.MarkerOptions;

import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
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


/**
 * Base position fragment
 *
 * @author Andrea Cappelli
 * @author Daniele Ricci
 */
public abstract class PositionAbstractFragment extends Fragment
        implements OnMapReadyCallback, AssentCallback {
    final static String TAG = PositionAbstractFragment.class.getSimpleName();

    private static final int REQUEST_PERMISSIONS = 320;

    final static int DEFAULT_ZOOM = 12;

    private MapContainerView mMapView;
    protected AnyMap mMap;

    protected Position mPosition;
    protected Location mUserLocation;
    protected Location mMyLocation;

    private FloatingActionButton mFabMyLocation;
    private FloatingActionButton mFabRoute;

    private View mBottomView;
    private CircleImageView mUserAvatar;
    private TextView mUserName;
    private TextView mDistance;

    private boolean mPermissionAsked;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Assent.setFragment(this, this);
        setHasOptionsMenu(true);
    }

    protected abstract View onInflateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = onInflateView(inflater, container, savedInstanceState);

        mMapView = view.findViewById(R.id.mapView);

        mBottomView = view.findViewById(R.id.bottom_view);
        mUserAvatar = view.findViewById(R.id.avatar);
        mUserName = view.findViewById(R.id.user_text_name);
        mDistance = view.findViewById(R.id.user_text_position);

        mFabRoute = view.findViewById(R.id.fab_route);

        mFabMyLocation = view.findViewById(R.id.fab_my_position);

        mMapView.onCreate(savedInstanceState);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable Bundle savedInstanceState) {
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

        mUserLocation = new Location("network");
        mUserLocation.setLatitude(mPosition.getLatitude());
        mUserLocation.setLongitude(mPosition.getLongitude());

        mBottomView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mUserLocation != null) {
                    LatLng latLng = new LatLng(mUserLocation.getLatitude(), mUserLocation.getLongitude());
                    if (mMap != null) {
                        mMap.animateCamera(getCameraUpdateFactory().newLatLngZoom(latLng, DEFAULT_ZOOM));
                    }
                }
            }
        });

        mFabMyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMyLocation != null && mMap != null && isLocationEnabled()) {
                    zoomToMyLocation();
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
                        startActivity(new Intent(Intent.ACTION_VIEW,
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Assent.handleResult(permissions, grantResults);
    }

    @Override
    public void onPermissionResult(PermissionResultSet result) {
        if (result.allPermissionsGranted()) {
            onMapReady(mMap);
            requestLocation();
        }
    }

    /** Child classes should override this to begin location requests. */
    protected abstract void requestLocation();

    protected abstract boolean isLocationEnabled();

    protected void zoomToMyLocation() {
        mMap.animateCamera(getCameraUpdateFactory()
            .newLatLngZoom(new LatLng(mMyLocation.getLatitude(), mMyLocation.getLongitude()), DEFAULT_ZOOM));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity() != null && getActivity().isFinishing())
            Assent.setFragment(this, null);
        mMapView.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        Assent.setFragment(this, this);
        mMapView.onResume();
        askPermissions();
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
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
                    startActivity(new Intent(Intent.ACTION_VIEW,
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

    //@Override
    public void onLocationChanged(Location location) {
        if (mMap != null) {
            positionMarker(location);
        }
    }

    protected abstract CameraUpdateFactory getCameraUpdateFactory();

    protected abstract BitmapDescriptor createMarkerBitmap();

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
                mMap.addMarker(new MarkerOptions().position(latLng).anchor(0.5f, 1).icon(createMarkerBitmap()));
            }
            catch (Exception e) {
                Log.e(TAG, "error adding position marker", e);
            }
            mMap.moveCamera(getCameraUpdateFactory().newLatLngZoom(latLng, DEFAULT_ZOOM));
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

    private void askPermissions() {
        if (!Assent.isPermissionGranted(Assent.ACCESS_COARSE_LOCATION) ||
            !Assent.isPermissionGranted(Assent.ACCESS_FINE_LOCATION)) {

            if (!mPermissionAsked) {
                Assent.requestPermissions(this, REQUEST_PERMISSIONS,
                    Assent.ACCESS_COARSE_LOCATION,
                    Assent.ACCESS_FINE_LOCATION);
                mPermissionAsked = true;
            }
        }
        else {
            requestLocation();
        }
    }

}

