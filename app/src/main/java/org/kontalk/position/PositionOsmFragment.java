/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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
import com.car2go.maps.CameraUpdateFactory;
import com.car2go.maps.model.BitmapDescriptor;
import com.car2go.maps.osm.BitmapDescriptorFactory;
import com.car2go.maps.osm.MapsConfiguration;

import android.content.Context;
import android.content.Intent;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.kontalk.R;


/**
 * Position Open Street Maps fragment
 *
 * @author Andrea Cappelli
 */
public class PositionOsmFragment extends PositionAbstractFragment implements LocationListener {

    private LocationManager mLocationManager;

    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLocationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    protected View onInflateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_position_osm, container, false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        MapsConfiguration.getInstance().initialize(getContext());

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        // OSM doesn't have satellite
        menu.removeItem(R.id.satellite);
    }

    @Override
    protected CameraUpdateFactory getCameraUpdateFactory() {
        return com.car2go.maps.osm.CameraUpdateFactory.getInstance();
    }

    @Override
    protected BitmapDescriptor createMarkerBitmap() {
        // TODO OSM should have its own pin icon
        return BitmapDescriptorFactory.getInstance().fromResource(R.drawable.ic_map_pin_google);
    }

    @Override
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
    public void requestLocation() {
        boolean hasProvider = false, permissionDenied = false;
        try {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            hasProvider = true;
        }
        catch (IllegalArgumentException e) {
            // no gps available
        }
        catch (SecurityException e) {
            permissionDenied = true;
        }
        try {
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
            hasProvider = true;
        }
        catch (IllegalArgumentException e) {
            // no network location available
        }
        catch (SecurityException e) {
            permissionDenied = true;
        }

        if (permissionDenied) {
            Toast.makeText(getContext(), R.string.err_location_permission,
                Toast.LENGTH_LONG).show();
        }
        else if (!hasProvider) {
            Toast.makeText(getContext(), R.string.err_location_no_providers,
                Toast.LENGTH_LONG).show();
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

}

