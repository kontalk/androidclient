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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.position.model.CategoriesItem;
import org.kontalk.position.model.SearchResponse;
import org.kontalk.position.model.VenuesItem;

/**
 * Google places list adapter
 *
 * @author andreacappelli
 */

public class PlacesAdapter extends RecyclerView.Adapter<PlacesAdapter.ViewHolder>
        implements IPlacesAdapter {

    private static final String TAG = PlacesAdapter.class.getSimpleName();

    private final static int EMPTY = 0;
    private final static int SEND_LOCATION = 1;
    private final static int GRAY_DIVIDER = 2;
    private final static int LOCATION = 3;
    private final static int LOADING = 4;

    private Context mContext;
    private List<VenuesItem> mList = new ArrayList<>();
    private int mOverScrollHeight;
    private SendLocationRow mSendLocationRow;
    private Location mGpsLocation;
    private Location mCustomLocation;

    private boolean mSearching;
    private Location mLastSearchLocation;

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View itemView) {
            super(itemView);
        }

    }

    public PlacesAdapter(Context context) {
        mContext = context;
    }

    public void setOverScrollHeight(int value) {
        mOverScrollHeight = value;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;
        switch (viewType) {
            case EMPTY:
                view = new EmptyRow(mContext);
                break;
            case SEND_LOCATION:
                view = new SendLocationRow(mContext);
                break;
            case GRAY_DIVIDER:
                view = new GrayDividerRow(mContext);
                break;
            case LOCATION:
                view = new LocationRow(mContext);
                break;
            case LOADING:
                view = new LocationLoadingRow(mContext);
                break;
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        switch (holder.getItemViewType()) {
            case EMPTY:
                ((EmptyRow) holder.itemView).setHeight(mOverScrollHeight);
                break;
            case SEND_LOCATION:
                mSendLocationRow = (SendLocationRow) holder.itemView;
                updateRow();
                break;
            case GRAY_DIVIDER:
                //NOTHING
                break;
            case LOCATION:
                VenuesItem item = mList.get(position - 3);
                String iconUrl = null;
                if (item.getCategories() != null && item.getCategories().size() > 0) {
                    CategoriesItem category = item.getCategories().get(0);
                    iconUrl = String.format("%s64%s", category.getIcon().getPrefix(), category.getIcon().getSuffix());
                }

                String address = null;
                if (item.getLocation().getAddress() != null) {
                    address = item.getLocation().getAddress();
                }
                else if (item.getLocation().getCity() != null) {
                    address = item.getLocation().getCity();
                }
                else if (item.getLocation().getState() != null) {
                    address = item.getLocation().getState();
                }
                else if (item.getLocation().getCountry() != null) {
                    address = item.getLocation().getCountry();
                }

                ((LocationRow) holder.itemView).setLocation(iconUrl, item.getName(), address, true);
                break;
            case LOADING:
                ((LocationLoadingRow) holder.itemView).setLoading(mSearching);
                break;
        }

        holder.itemView.setEnabled(isEnabled(holder));
    }

    @Override
    public int getItemCount() {
        if (mSearching || !mSearching && mList.isEmpty()) {
            return 4;
        }
        return 3 + mList.size();
    }

    public void setCustomLocation(Location location) {
        mCustomLocation = location;
        updateRow();
    }

    public void setGpsPosition(Location location) {
        mGpsLocation = location;
        updateRow();
    }

    public void searchPlaces(Location location) {
        if (mLastSearchLocation != null && location.distanceTo(mLastSearchLocation) < 200) {
            return;
        }
        mLastSearchLocation = location;
        if (mSearching) {
            mSearching = false;
            new OkHttpClient().dispatcher().cancelAll();
        }

        mSearching = true;

        notifyDataSetChanged();

        PlacesRestClient.getPlacesByLocation(mContext, location.getLatitude(), location.getLongitude(),
            25, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mSearching = false;
                    mList.clear();
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            notifyDataSetChanged();
                        }
                    });
                    Log.e(TAG, e.getLocalizedMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    mSearching = false;
                    if (response.body() != null) {
                        Gson gson = new Gson();
                        SearchResponse searchResponse = gson.fromJson(response.body().string(), SearchResponse.class);
                        mList = searchResponse.getResponse().getVenues();
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                notifyDataSetChanged();
                            }
                        });
                    }
                }
            });
    }

    private void updateRow() {
        if (mSendLocationRow != null) {
            if (mCustomLocation != null) {
                mSendLocationRow.setText(mContext.getString(R.string.send_selected_location), String.format(Locale.US, "(%f, %f)", mCustomLocation.getLatitude(), mCustomLocation.getLongitude()));
            }
            else {
                if (mGpsLocation != null) {
                    mSendLocationRow.setText(mContext.getString(R.string.send_location), mContext.getString(R.string.accurate_to, String.valueOf((int) mGpsLocation.getAccuracy())));
                }
                else {
                    mSendLocationRow.setText(mContext.getString(R.string.send_location), mContext.getString(R.string.loading));
                }
            }
        }
    }

    public Position getVenuesItem(int i) {
        if (i > 2 && i < mList.size() + 3) {
            VenuesItem item = mList.get(i - 3);
            Position position = new Position(item.getLocation().getLat(),
                item.getLocation().getLng());
            position.setName(item.getName());
            String address = null;
            if (item.getLocation().getAddress() != null) {
                address = item.getLocation().getAddress();
            }
            else if (item.getLocation().getCity() != null) {
                address = item.getLocation().getCity();
            }
            else if (item.getLocation().getState() != null) {
                address = item.getLocation().getState();
            }
            else if (item.getLocation().getCountry() != null) {
                address = item.getLocation().getCountry();
            }
            position.setAddress(address);

            return position;
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return EMPTY;
        }
        else if (position == 1) {
            return SEND_LOCATION;
        }
        else if (position == 2) {
            return GRAY_DIVIDER;
        }
        else if (mSearching || !mSearching && mList.isEmpty()) {
            return LOADING;
        }

        return LOCATION;
    }

    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int position = holder.getAdapterPosition();
        return !(position == 2 || position == 0 || position == 3 && (mSearching || !mSearching && mList.isEmpty()) || position == mList.size() + 3);
    }
}
