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

import java.util.ArrayList;
import java.util.List;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.Response;

import android.content.Context;
import android.location.Location;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.kontalk.position.model.CategoriesItem;
import org.kontalk.position.model.SearchResponse;
import org.kontalk.position.model.VenuesItem;

/**
 * Search Places adapter
 *
 * @author andreacappelli
 */

public class SearchPlacesAdapter extends RecyclerView.Adapter<SearchPlacesAdapter.ViewHolder> {

    private final static int LOCATION = 0;
    private final static int LOADING = 1;

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
            case LOADING: {
                LocationLoadingRow locationLoadingRow = new LocationLoadingRow(mContext, true);
                locationLoadingRow.setEnabled(false);
                view = locationLoadingRow;
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
                ((LocationLoadingRow) holder.itemView).setLoading(true);
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
        if (mSearching)
            return 1;
        return mList.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (mSearching)
            return LOADING;
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
            Ion.getDefault(mContext).cancelAll();
        }

        mSearching = true;

        notifyDataSetChanged();

        PlacesRestClient.getPlacesByQuery(mContext, location.getLatitude(), location.getLongitude(),
            query, new FutureCallback<Response<SearchResponse>>() {
                @Override
                public void onCompleted(Exception e, Response<SearchResponse> result) {
                    mSearching = false;
                    if (e != null) {
                        mList.clear();
                        notifyDataSetChanged();
                        return;
                    }

                    mList = result.getResult().getResponse().getVenues();
                    notifyDataSetChanged();
                }
            });
    }

}

