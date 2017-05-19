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

import android.content.Context;

import org.kontalk.R;

/**
 * @author andreacappelli
 * @version 1.0
 *          DATE: 18/05/17
 */

public class PositionManager {

    public static String getDefaultMapsProvider(Context context) {
        return context.getString(R.string.pref_default_maps_osm);
    }

    public static MapContainerView getMapView(Context context) {
        String osm = context.getString(R.string.pref_default_maps_osm);
        if (Preferences.getMapsProvider(context).equals(osm)) {
            com.car2go.maps.osm.MapView osmMapView = new com.car2go.maps.osm.MapView(context);
            osmMapView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            return osmMapView;
        }

        return null;
    }
}
