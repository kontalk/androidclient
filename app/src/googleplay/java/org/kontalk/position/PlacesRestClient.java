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

package org.kontalk.position;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;

import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Foursquare Venues rest client
 * @author andreacappelli
 */

public class PlacesRestClient {

    private final static String TAG = PlacesRestClient.class.getSimpleName();

    private final static String CLIENT_ID = "P2LJNVUONE1PPJSUYCEWEFB5LNV5S1ESRNFDNX15OQRXEF42";
    private final static String CLIENT_SECRET = "VKZJKBW5ZSMQTTW4ITNJXRM4D5G4V0HWACVEPKSPTCVKNI2I";

    public static void getPlacesByLocation(Context context, double lat, double lon, int limit, Callback callback) {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        format.format(date);

        OkHttpClient client = new OkHttpClient();
        HttpUrl.Builder urlBuilder = HttpUrl.parse("https://api.foursquare.com/v2/venues/search").newBuilder();
        urlBuilder.addQueryParameter("v", format.format(date));
        urlBuilder.addQueryParameter("ll", String.valueOf(lat + "," + lon));
        urlBuilder.addQueryParameter("limit", String.valueOf(limit));
        urlBuilder.addQueryParameter("client_id", CLIENT_ID);
        urlBuilder.addQueryParameter("client_secret", CLIENT_SECRET);

        String url = urlBuilder.build().toString();

        Request request = new Request.Builder()
            .url(url)
            .build();

        client.newCall(request).enqueue(callback);
    }

    public static void getPlacesByQuery(Context context, double lat, double lon, String query, Callback callback) {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        format.format(date);

        OkHttpClient client = new OkHttpClient();
        HttpUrl.Builder urlBuilder = HttpUrl.parse("https://api.foursquare.com/v2/venues/search").newBuilder();
        urlBuilder.addQueryParameter("v", format.format(date));
        urlBuilder.addQueryParameter("ll", String.valueOf(lat + "," + lon));
        urlBuilder.addQueryParameter("query", query);
        urlBuilder.addQueryParameter("client_id", CLIENT_ID);
        urlBuilder.addQueryParameter("client_secret", CLIENT_SECRET);

        String url = urlBuilder.build().toString();

        Request request = new Request.Builder()
            .url(url)
            .build();

        client.newCall(request).enqueue(callback);
    }
}
