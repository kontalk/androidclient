package org.kontalk.position;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.Response;

import android.content.Context;
import android.util.Log;

import org.kontalk.BuildConfig;
import org.kontalk.position.model.SearchResponse;

/**
 * Foursquare Venues rest client
 * @author andreacappelli
 */

public class PlacesRestClient {

    private final static String TAG = PlacesRestClient.class.getSimpleName();

    private final static String CLIENT_ID = "P2LJNVUONE1PPJSUYCEWEFB5LNV5S1ESRNFDNX15OQRXEF42";
    private final static String CLIENT_SECRET = "VKZJKBW5ZSMQTTW4ITNJXRM4D5G4V0HWACVEPKSPTCVKNI2I";

    public static void getPlacesByLocation(Context context, double lat, double lon, int limit, FutureCallback<Response<SearchResponse>> callback) {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        format.format(date);
        Ion.with(context)
            .load("https://api.foursquare.com/v2/venues/search")
            .addQuery("v", format.format(date))
            .addQuery("ll", String.valueOf(lat + "," + lon))
            .addQuery("limit", String.valueOf(limit))
            .addQuery("client_id", CLIENT_ID)
            .addQuery("client_secret", CLIENT_SECRET)
            .setLogging(TAG, Log.VERBOSE)
            .as(SearchResponse.class).withResponse().setCallback(callback);
    }

    public static void getPlacesByQuery(Context context, double lat, double lon, String query, FutureCallback<Response<SearchResponse>> callback) {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        format.format(date);
        Ion.with(context)
            .load("https://api.foursquare.com/v2/venues/search")
            .addQuery("v", format.format(date))
            .addQuery("ll", String.valueOf(lat + "," + lon))
            .addQuery("query", query)
            .addQuery("client_id", CLIENT_ID)
            .addQuery("client_secret", CLIENT_SECRET)
            .setLogging(TAG, Log.VERBOSE)
            .as(SearchResponse.class).withResponse().setCallback(callback);
    }
}
