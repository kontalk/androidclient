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


/**
 * @author Andrea Cappelli
 */
public class OsmStaticUrlBuilder {

    private static final String URL = "http://staticmap.openstreetmap.de/staticmap.php";

    private String mCenter;
    private int mZoom = 15;
    private String mSize = "600x300";
    private String mMarker;

    public OsmStaticUrlBuilder setCenter(double lat, double lon) {
        mCenter = lat + "," + lon;
        return this;
    }

    public OsmStaticUrlBuilder setZoom(Integer zoom) {
        if (zoom != null)
            mZoom = zoom;
        return this;
    }

    public OsmStaticUrlBuilder setSize(int width, int height) {
        mSize = width + "x" + height;
        return this;
    }

    public OsmStaticUrlBuilder setMarker(double lat, double lon) {
        mMarker = lat + "," + lon;
        return this;
    }

    @Override
    public String toString() {
        return URL +
            "?center=" +
            mCenter +
            "&zoom=" +
            mZoom +
            "&size=" +
            mSize +
            "&markers=" +
            mMarker +
            ",ol-marker";
    }

}
