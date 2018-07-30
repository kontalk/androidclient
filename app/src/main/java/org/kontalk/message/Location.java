/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.message;


/**
 * Location data.
 * @author andreacappelli
 */

public class Location {

    private final double mLatitude;
    private final double mLongitude;
    private final String mText;
    private final String mStreet;


    public Location(double lat, double lon, String text, String street) {
        mLatitude = lat;
        mLongitude = lon;
        mText = text;
        mStreet = street;
    }

    public double getLatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    public String getText() {
        return mText;
    }

    public String getStreet() {
        return mStreet;
    }
}
