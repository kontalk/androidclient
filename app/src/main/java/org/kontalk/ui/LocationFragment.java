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

package org.kontalk.ui;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.car2go.maps.MapContainerView;

import org.kontalk.R;
import org.kontalk.position.PositionManager;

/**
 * Location Activity
 * @author andreacappelli
 */

public class LocationFragment extends Fragment {

    private LinearLayout mRootLayout;
    private MapContainerView mMapContainerView;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(PositionManager.getMapView(getContext()), container, false);

        mRootLayout = (LinearLayout) view.findViewById(R.id.root_view);

        mMapContainerView = (MapContainerView) view.findViewById(R.id.mapView);

        mMapContainerView.onCreate(savedInstanceState);

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapContainerView.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapContainerView.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMapContainerView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapContainerView.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapContainerView.onSaveInstanceState(outState);
    }
}
