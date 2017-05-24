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

package org.kontalk.client;

import java.io.IOException;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;


/**
 * XEP-0080: User Location
 * http://xmpp.org/extensions/xep-0080.html
 * @author Andrea Cappelli
 * @author Daniele Ricci
 */

public class UserLocation implements ExtensionElement {
    public static final String NAMESPACE = "http://jabber.org/protocol/geoloc";
    public static final String ELEMENT_NAME = "geoloc";

    private double mLatitude;
    private double mLongitude;

    public UserLocation(double latitude, double longitude) {
        mLatitude = latitude;
        mLongitude = longitude;
    }

    public double getLatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    @Override
    public String getElementName() {
        return ELEMENT_NAME;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String toXML() {
        return new StringBuilder("<")
                .append(ELEMENT_NAME)
                .append(" xmlns='")
                .append(NAMESPACE)
                .append("'><lat>")
                .append(mLatitude)
                .append("</lat><lon>")
                .append(mLongitude)
                .append("</lon></")
                .append(ELEMENT_NAME)
                .append('>')
                .toString();
    }

    public static final class Provider extends ExtensionElementProvider<UserLocation> {
        @Override
        public UserLocation parse(XmlPullParser parser, int initialDepth) throws XmlPullParserException, IOException, SmackException {
            double lat = 0, lon = 0;
            boolean lat_found = false, lon_found = false;
            boolean in_lat = false, in_lon = false, done = false;

            while (!done)
            {
                int eventType = parser.next();

                if (eventType == XmlPullParser.START_TAG)
                {
                    if ("lon".equals(parser.getName())) {
                        in_lon = true;
                    }
                    else if ("lat".equals(parser.getName())) {
                        in_lat = true;
                    }
                }
                else if (eventType == XmlPullParser.END_TAG)
                {
                    if ("lon".equals(parser.getName())) {
                        in_lon = false;
                    }
                    else if ("lat".equals(parser.getName())) {
                        in_lat = false;
                    }
                    else if (ELEMENT_NAME.equals(parser.getName())) {
                        done = true;
                    }
                }
                else if (eventType == XmlPullParser.TEXT) {
                    if (in_lon) {
                        try {
                            lon = Double.parseDouble(parser.getText());
                            lon_found = true;
                        }
                        catch (NumberFormatException e) {
                        }
                    }
                    else if (in_lat) {
                        try {
                            lat = Double.parseDouble(parser.getText());
                            lat_found = true;
                        }
                        catch (NumberFormatException e) {
                        }
                    }
                }
            }

            if (lon_found && lat_found)
                return new UserLocation(lat, lon);
            else
                return null;
        }
    }
}
