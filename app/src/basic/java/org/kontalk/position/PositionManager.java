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

import android.content.Context;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;


/**
 * @author Andrea Cappelli
 */
public class PositionManager {

    public static Fragment getSendPositionFragment(Context context) {
        return new SendPositionOsmFragment();
    }

    public static Fragment getPositionFragment(Context context) {
        return new PositionOsmFragment();
    }

    public static RequestDetails getStaticMapUrl(Context context, double lat, double lon, Integer zoom, int width, int height, Integer scale) {
        return new RequestDetails(new OsmStaticUrlBuilder().setCenter(lat, lon).setZoom(zoom)
                .setMarker(lat, lon).setSize(width, height).toString());
    }

    public static String getMapsUrl(Context context, double lat, double lon) {
        return OsmUrlBuilder.build(lat, lon);
    }

    public static RecyclerView.Adapter<?> createSearchPlacesAdapter(Context context) {
        return null;
    }

    public static RecyclerView.Adapter<?> createPlacesAdapter(Context context) {
        return null;
    }
}
