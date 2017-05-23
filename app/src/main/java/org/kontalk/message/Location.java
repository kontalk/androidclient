package org.kontalk.message;

/**
 * Location data.
 * @author andreacappelli
 */

public class Location {

    private final double mLatitude;
    private final double mLongitude;

    public Location(double lat, double lon) {
        mLatitude = lat;
        mLongitude = lon;
    }

    public double getLatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

}
