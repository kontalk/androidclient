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

package org.kontalk.position.model;

import java.util.List;

public class Location {
    private String cc;
    private String country;
    private String address;
    private List<LabeledLatLngsItem> labeledLatLngs;
    private double lng;
    private int distance;
    private List<String> formattedAddress;
    private String city;
    private String postalCode;
    private String state;
    private String crossStreet;
    private double lat;

    public String getCc() {
        return cc;
    }

    public void setCc(String cc) {
        this.cc = cc;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public List<LabeledLatLngsItem> getLabeledLatLngs() {
        return labeledLatLngs;
    }

    public void setLabeledLatLngs(List<LabeledLatLngsItem> labeledLatLngs) {
        this.labeledLatLngs = labeledLatLngs;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public List<String> getFormattedAddress() {
        return formattedAddress;
    }

    public void setFormattedAddress(List<String> formattedAddress) {
        this.formattedAddress = formattedAddress;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCrossStreet() {
        return crossStreet;
    }

    public void setCrossStreet(String crossStreet) {
        this.crossStreet = crossStreet;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    @Override
    public String toString() {
        return
            "Location{" +
                "cc = '" + cc + '\'' +
                ",country = '" + country + '\'' +
                ",address = '" + address + '\'' +
                ",labeledLatLngs = '" + labeledLatLngs + '\'' +
                ",lng = '" + lng + '\'' +
                ",distance = '" + distance + '\'' +
                ",formattedAddress = '" + formattedAddress + '\'' +
                ",city = '" + city + '\'' +
                ",postalCode = '" + postalCode + '\'' +
                ",state = '" + state + '\'' +
                ",crossStreet = '" + crossStreet + '\'' +
                ",lat = '" + lat + '\'' +
                "}";
    }
}
