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
import com.car2go.maps.model.LatLng;
import com.car2go.maps.model.MarkerOptions;
import com.car2go.maps.osm.BitmapDescriptorFactory;
import com.car2go.maps.osm.CameraUpdateFactory;
import com.car2go.maps.osm.MapView;
import com.car2go.maps.osm.MapsConfiguration;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
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
 * Position Open Street Maps fragment
 *
 * @author andreacappelli
 */

public class PositionOsmFragment extends Fragment implements OnMapReadyCallback, LocationListener{

    private final static String TAG = PositionOsmFragment.class.getSimpleName();

    private LocationManager mLocationManager;

    private MapView mMapView;
    private AnyMap mOsmMap;

    private Position mPosition;
    private Location mUserLocation;
    private Location mMyLocation;

    private FloatingActionButton mFabMyLocation;
    private FloatingActionButton mFabRoute;

    private View mBottomView;
    private CircleImageView mUserAvatar;
    private TextView mUserName;
    private TextView mDistance;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        mLocationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_position_osm, container, false);

        mMapView = (MapView) view.findViewById(R.id.mapView);

        mBottomView = view.findViewById(R.id.bottom_view);
        mUserAvatar = (CircleImageView) view.findViewById(R.id.avatar);
        mUserName = (TextView) view.findViewById(R.id.user_text_name);
        mDistance = (TextView) view.findViewById(R.id.user_text_position);

        mFabRoute = (FloatingActionButton) view.findViewById(R.id.fab_route);

        mFabMyLocation = (FloatingActionButton) view.findViewById(R.id.fab_my_position);

        mMapView.onCreate(savedInstanceState);

        MapsConfiguration.getInstance().initialize(getContext());

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

        mUserLocation = new Location("network");
        mUserLocation.setLatitude(mPosition.getLatitude());
        mUserLocation.setLongitude(mPosition.getLongitude());

        mBottomView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mUserLocation != null) {
                    LatLng latLng = new LatLng(mUserLocation.getLatitude(), mUserLocation.getLongitude());
                    if (mOsmMap != null) {
                        mOsmMap.animateCamera(CameraUpdateFactory.getInstance().newLatLngZoom(latLng, 14));
                    }
                }
            }
        });

        mFabMyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMyLocation != null && mOsmMap != null) {
                    mOsmMap.animateCamera(CameraUpdateFactory.getInstance().newLatLngZoom(new LatLng(mMyLocation.getLatitude(), mMyLocation.getLongitude()), 14));
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
                mOsmMap.setMapType(AnyMap.Type.NORMAL);
                return true;

            case R.id.satellite:
                mOsmMap.setMapType(AnyMap.Type.SATELLITE);
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
        if (mOsmMap != null) {
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
        mOsmMap = anyMap;
        anyMap.setMyLocationEnabled(true);
        anyMap.getUiSettings().setMyLocationButtonEnabled(false);
        anyMap.getUiSettings().setMapToolbarEnabled(false);
        anyMap.getUiSettings().setCompassEnabled(false);

        if (mPosition != null) {
            LatLng latLng = new LatLng(mUserLocation.getLatitude(), mUserLocation.getLongitude());
            try {
                mOsmMap.addMarker(new MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.getInstance().fromResource(R.drawable.ic_map_pin)));
            }
            catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
            mOsmMap.moveCamera(CameraUpdateFactory.getInstance().newLatLngZoom(latLng, 14));
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

