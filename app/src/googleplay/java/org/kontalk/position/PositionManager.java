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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.widget.Toast;

import org.kontalk.R;
import org.kontalk.util.Preferences;

/**
 * @author andreacappelli
 */

public class PositionManager {
    private static final String PROVIDER_GOOGLE = "google";
    private static final String PROVIDER_OSM = "osm";

    public static Fragment getSendPositionFragment(Context context) {
        String provider = Preferences.getMapsProvider(context);
        Fragment fragment = null;
        if (PROVIDER_GOOGLE.equals(provider)) {
            if (isGoogleMapsAvailable(context)) {
                fragment = new SendPositionGoogleFragment();
            }
            else {
                Toast.makeText(context, R.string.err_googlemaps_fallback_osm,
                    Toast.LENGTH_LONG).show();
                fragment = new SendPositionOsmFragment();
            }
        }
        else if (PROVIDER_OSM.equals(provider)) {
            fragment = new SendPositionOsmFragment();
        }

        return fragment;
    }

    public static Fragment getPositionFragment(Context context) {
        String provider = Preferences.getMapsProvider(context);
        Fragment fragment = null;
        if (PROVIDER_GOOGLE.equals(provider)) {
            if (isGoogleMapsAvailable(context)) {
                fragment = new PositionGoogleFragment();
            }
            else {
                Toast.makeText(context, R.string.err_googlemaps_fallback_osm,
                    Toast.LENGTH_LONG).show();
                fragment = new PositionOsmFragment();
            }
        }
        else if (PROVIDER_OSM.equals(provider)) {
            fragment = new PositionOsmFragment();
        }

        return fragment;
    }

    public static String getStaticMapUrl(Context context, double lat, double lon, Integer zoom, int width, int height, Integer scale) {
        String provider = Preferences.getMapsProvider(context);
        if (PROVIDER_GOOGLE.equals(provider)) {
            return new GMStaticUrlBuilder().setCenter(lat, lon).setZoom(zoom)
                .setMarker(lat, lon).setSize(width, height).setScale(scale).toString();
        }
        else if (PROVIDER_OSM.equals(provider)) {
            return new OsmStaticUrlBuilder().setCenter(lat, lon).setZoom(zoom)
                .setMarker(lat, lon).setSize(width, height).toString();
        }

        return null;
    }

    public static String getMapsUrl(Context context, double lat, double lon) {
        String provider = Preferences.getMapsProvider(context);
        if (PROVIDER_GOOGLE.equals(provider)) {
            return GMapsUrlBuilder.build(lat, lon);
        }
        else if (PROVIDER_OSM.equals(provider)) {
            return OsmUrlBuilder.build(lat, lon);
        }
        return null;
    }

    private static boolean isGoogleMapsAvailable(Context context) {
        int status = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context);
        return status == ConnectionResult.SUCCESS ||
            status == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED;
    }

    public static RecyclerView.Adapter<?> createSearchPlacesAdapter(Context context) {
        return new SearchPlacesAdapter(context);
    }

    public static RecyclerView.Adapter<?> createPlacesAdapter(Context context) {
        return new PlacesAdapter(context);
    }

}
