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
import com.car2go.maps.model.BitmapDescriptor;
import com.car2go.maps.osm.BitmapDescriptorFactory;
import com.car2go.maps.osm.MapsConfiguration;

import android.content.Context;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

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
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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
    public void onPause() {
        super.onPause();
        mLocationManager.removeUpdates(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
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

