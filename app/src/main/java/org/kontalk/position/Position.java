package org.kontalk.position;

import java.io.Serializable;

/**
 * @author andreacappelli
 */

public class Position implements Serializable {

    private double mLatitude;
    private double mLongitude;
    private String mName;
    private String mAddress;

    public Position(double latitude, double longitude) {
        mLatitude = latitude;
        mLongitude = longitude;
    }

    public Position(double latitude, double longitude, String name, String address) {
        mLatitude = latitude;
        mLongitude = longitude;
        mName = name;
        mAddress = address;
    }

    public double getLatitude() {
        return mLatitude;
    }

    public void setLatitude(double latitude) {
        mLatitude = latitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    public void setLongitude(double longitude) {
        mLongitude = longitude;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getAddress() {
        return mAddress;
    }

    public void setAddress(String address) {
        mAddress = address;
    }

}
