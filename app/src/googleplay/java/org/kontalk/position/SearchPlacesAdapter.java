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

import com.google.gson.Gson;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;

import org.kontalk.Log;
import org.kontalk.position.model.CategoriesItem;
import org.kontalk.position.model.SearchResponse;
import org.kontalk.position.model.VenuesItem;

/**
 * Search Places adapter
 *
 * @author andreacappelli
 */

public class SearchPlacesAdapter extends RecyclerView.Adapter<SearchPlacesAdapter.ViewHolder>
        implements ISearchPlacesAdapter {

    private static final String TAG = SearchPlacesAdapter.class.getSimpleName();

    private final static int LOCATION = 0;
    private final static int LOADING = 1;
    private final static int NO_RESULT = 2;

    private Context mContext;
    private List<VenuesItem> mList = new ArrayList<>();

    private boolean mSearching;

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View itemView) {
            super(itemView);
        }

    }

    public SearchPlacesAdapter(Context context) {
        mContext = context;
    }

    @Override
    public SearchPlacesAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;
        switch (viewType) {
            case LOADING:
            case NO_RESULT: {
                LoadingRow loadingRow = new LoadingRow(mContext);
                loadingRow.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                loadingRow.setEnabled(false);
                view = loadingRow;
                break;
            }
            case LOCATION: {
                view = new LocationRow(mContext);
            }
        }


        return new SearchPlacesAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(SearchPlacesAdapter.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case LOADING: {
                ((LoadingRow) holder.itemView).setLoading(true);
                break;
            }
            case NO_RESULT: {
                ((LoadingRow) holder.itemView).setLoading(false);
                break;
            }
            case LOCATION: {
                VenuesItem item = mList.get(position);
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

                ((LocationRow) holder.itemView).setLocation(iconUrl, item.getName(), address, position != mList.size() - 1);
                break;
            }
        }
    }

    @Override
    public int getItemCount() {
        if (mList == null || mList.isEmpty())
            return 1;
        return mList.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (mSearching)
            return LOADING;

        if (mList == null || mList.isEmpty())
            return NO_RESULT;

        return LOCATION;
    }

    public Position getVenuesItem(int i) {
        if (mList.size() > 0) {
            VenuesItem item = mList.get(i);
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

    public void searchPlacesWithQuery(String query, Location location) {
        if (query == null || query.length() == 0) {
            mList.clear();
            notifyDataSetChanged();
            mSearching = false;
            return;
        }

        if (mSearching) {
            mSearching = false;
            new OkHttpClient().dispatcher().cancelAll();
        }

        mSearching = true;

        notifyDataSetChanged();

        PlacesRestClient.getPlacesByQuery(mContext, location.getLatitude(), location.getLongitude(),
            query, new Callback() {
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

}

