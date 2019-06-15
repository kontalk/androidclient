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

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.car2go.maps.AnyMap;
import com.car2go.maps.CameraUpdateFactory;
import com.car2go.maps.MapContainerView;
import com.car2go.maps.OnInterceptTouchEvent;
import com.car2go.maps.OnMapReadyCallback;
import com.car2go.maps.model.LatLng;

import org.kontalk.R;
import org.kontalk.util.Permissions;
import org.kontalk.util.RecyclerItemClickListener;
import org.kontalk.util.ViewUtils;

import java.util.Locale;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;


/**
 * Base send position fragment
 *
 * @author Andrea Cappelli
 * @author Daniele Ricci
 */
public abstract class SendPositionAbstractFragment extends Fragment
        implements OnMapReadyCallback {

    private static final String STATE_MYLOCATION = SendPositionAbstractFragment.class
        .getSimpleName() + "myLocation";

    FrameLayout mMapViewClip;
    private MapContainerView mMapView;
    AnyMap mMap;

    Location mUserLocation;
    Location mMyLocation;

    ImageView mMapPin;
    ImageView mPinX;
    FloatingActionButton mFabMyLocation;

    RecyclerView mRecyclerView;
    RecyclerView mSearchRecyclerView;
    LinearLayoutManager mRecyclerViewLayoutManager;
    RecyclerView.Adapter<?> mSearchAdapter;
    RecyclerView.Adapter<?> mAdapter;

    private SendLocationRow mSendLocationRow;

    boolean mUserLocationMoved;

    private int mOverScrollHeight;
    int mMarkerTop;

    private boolean mPermissionAsked;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    protected abstract View onInflateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState);

    @SuppressLint("WrongViewCast")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = onInflateView(inflater, container, savedInstanceState);

        if (isPlacesEnabled()) {
            mMapViewClip = view.findViewById(R.id.mapview_clip);
            mRecyclerView = view.findViewById(R.id.recyclerView);
            mSearchRecyclerView = view.findViewById(R.id.search_recyclerView);
        }
        else {
            mSendLocationRow = view.findViewById(R.id.send_location);
        }

        mMapView = view.findViewById(R.id.mapView);

        mMapPin = view.findViewById(R.id.map_pin);
        mPinX = view.findViewById(R.id.pin_x);
        mPinX.setAlpha(0.0f);

        mFabMyLocation = view.findViewById(R.id.fab_my_position);

        mMapView.onCreate(savedInstanceState);

        return view;
    }

    protected abstract boolean isPlacesEnabled();

    protected abstract void onMapTouchEvent(MotionEvent ev);

    protected abstract void onFabClicked(Location location);

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mUserLocation = new Location("network");

        if (savedInstanceState != null)
            mMyLocation = savedInstanceState.getParcelable(STATE_MYLOCATION);
        else
            mMyLocation = new Location("network");

        mMapView.getMapAsync(this);

        mMapView.setOnInterceptTouchEventListener(new OnInterceptTouchEvent() {
            @Override
            public void onInterceptTouchEvent(MotionEvent ev) {
                onMapTouchEvent(ev);
            }
        });

        mFabMyLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMyLocation != null && mMap != null && isLocationEnabled()) {
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.setDuration(200);
                    animatorSet.play(ObjectAnimator.ofFloat(mFabMyLocation, "alpha", 0.0f));
                    animatorSet.start();
                    onFabClicked(mMyLocation);
                    if (mAdapter != null) {
                        ((IPlacesAdapter) mAdapter).setCustomLocation(null);
                    }
                    mUserLocationMoved = false;
                    mMap.animateCamera(getCameraUpdateFactory().newLatLngZoom(new LatLng(mMyLocation.getLatitude(), mMyLocation.getLongitude()), PositionAbstractFragment.DEFAULT_ZOOM));
                }
            }
        });

        if (isPlacesEnabled()) {
            mAdapter = PositionManager.createPlacesAdapter(getContext());
            mSearchAdapter = PositionManager.createSearchPlacesAdapter(getContext());

            mRecyclerViewLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
            LinearLayoutManager searchRecyclerViewLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);

            mRecyclerView.setLayoutManager(mRecyclerViewLayoutManager);
            mRecyclerView.setAdapter(mAdapter);

            mSearchRecyclerView.setLayoutManager(searchRecyclerViewLayoutManager);
            mSearchRecyclerView.setAdapter(mSearchAdapter);

            mRecyclerView.addOnItemTouchListener(new RecyclerItemClickListener(getContext(), new RecyclerItemClickListener.OnItemClickListener() {
                @Override
                public void onItemClick(View view, int position) {
                    if (position == 1) {
                        Position p = new Position(mUserLocation.getLatitude(), mUserLocation.getLongitude());
                        Intent intent = new Intent();
                        intent.putExtra("position", p);
                        getActivity().setResult(Activity.RESULT_OK, intent);
                        getActivity().finish();
                    }
                    else {
                        Position item = ((IPlacesAdapter) mAdapter).getVenuesItem(position);
                        if (item != null) {
                            Intent intent = new Intent();
                            intent.putExtra("position", item);
                            getActivity().setResult(Activity.RESULT_OK, intent);
                            getActivity().finish();
                        }
                    }
                }
            }));

            mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (mAdapter.getItemCount() == 0) {
                        return;
                    }
                    int position = mRecyclerViewLayoutManager.findFirstVisibleItemPosition();
                    if (position == RecyclerView.NO_POSITION) {
                        return;
                    }
                    updateClipView(position);
                }
            });

            mSearchRecyclerView.addOnItemTouchListener(new RecyclerItemClickListener(getContext(), new RecyclerItemClickListener.OnItemClickListener() {
                @Override
                public void onItemClick(View view, int position) {
                    Position item = ((ISearchPlacesAdapter) mSearchAdapter).getVenuesItem(position);
                    if (item != null) {
                        Intent intent = new Intent();
                        intent.putExtra("position", item);
                        getActivity().setResult(Activity.RESULT_OK, intent);
                        getActivity().finish();
                    }
                }
            }));

            mFabMyLocation.setAlpha(0.0f);
        }
        else {
            mSendLocationRow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Position position = new Position(mUserLocation.getLatitude(), mUserLocation.getLongitude());
                    Intent intent = new Intent();
                    intent.putExtra("position", position);
                    getActivity().setResult(Activity.RESULT_OK, intent);
                    getActivity().finish();
                }
            });
        }
    }

    protected abstract boolean isLocationEnabled();

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();

        if (isPlacesEnabled()) {
            if (getView().getMeasuredHeight() == 0)
                getView().post(new Runnable() {
                    @Override
                    public void run() {
                        prepareLayout(true);
                    }
                });
            else
                prepareLayout(true);
        }

        // my location will be saved in state, so gps fix time will be zero
        if (mMyLocation.getTime() <= 0) {
            askPermissions();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);
        outState.putParcelable(STATE_MYLOCATION, mMyLocation);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.send_position_menu, menu);

        if (isPlacesEnabled()) {
            MenuItem searchItem = menu.findItem(R.id.menu_search);
            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    mRecyclerView.setVisibility(View.GONE);
                    mMapViewClip.setVisibility(View.GONE);
                    mSearchRecyclerView.setVisibility(View.VISIBLE);
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    mRecyclerView.setVisibility(View.VISIBLE);
                    mMapViewClip.setVisibility(View.VISIBLE);
                    ((ISearchPlacesAdapter) mSearchAdapter).searchPlacesWithQuery(null, null);
                    mSearchRecyclerView.setVisibility(View.GONE);
                    return true;
                }
            });
            SearchView searchView = (SearchView) searchItem.getActionView();
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    ((ISearchPlacesAdapter) mSearchAdapter).searchPlacesWithQuery(query, mUserLocation);
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    return false;
                }
            });
        }
        else {
            menu.removeItem(R.id.menu_search);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (super.onOptionsItemSelected(item))
            return true;

        switch (item.getItemId()) {
            case R.id.map:
                if (!item.isChecked()) {
                    item.setChecked(true);
                    mMap.setMapType(AnyMap.Type.NORMAL);
                }
                return true;

            case R.id.satellite:
                if (!item.isChecked()) {
                    item.setChecked(true);
                    mMap.setMapType(AnyMap.Type.SATELLITE);
                }
                return true;
        }

        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @AfterPermissionGranted(Permissions.RC_LOCATION)
    void onLocationAccess() {
        if (mMap != null)
            onMapReady(mMap);
        requestLocation();
    }

    /** Child classes should override this to begin location requests. */
    protected abstract void requestLocation();

    @Override
    public void onMapReady(final AnyMap anyMap) {
        mMap = anyMap;
        try {
            anyMap.setMyLocationEnabled(true);
        }
        catch (SecurityException e) {
            // will enable my location later
        }
        anyMap.getUiSettings().setMyLocationButtonEnabled(false);
        anyMap.getUiSettings().setMapToolbarEnabled(false);
        anyMap.getUiSettings().setCompassEnabled(false);
    }

    protected void setGpsPosition(Location location) {
        if (isPlacesEnabled()) {
            ((IPlacesAdapter) mAdapter).searchPlaces(mMyLocation);
            ((IPlacesAdapter) mAdapter).setGpsPosition(mMyLocation);
        }
        else if (!mUserLocationMoved) {
            mSendLocationRow.setText(getString(R.string.send_location),
                getString(R.string.accurate_to, String.valueOf((int) location.getAccuracy())));
        }
    }

    protected void setCustomLocation(Location location) {
        if (isPlacesEnabled()) {
            ((IPlacesAdapter) mAdapter).setCustomLocation(location);
        }
        else {
            mSendLocationRow.setText(getString(R.string.send_selected_location),
                String.format(Locale.US, "(%f, %f)", location.getLatitude(), location.getLongitude()));
        }
    }

    protected abstract CameraUpdateFactory getCameraUpdateFactory();

    protected void positionMarker(Location location) {
        if (location == null) {
            return;
        }
        mMyLocation = new Location(location);

        setGpsPosition(mMyLocation);

        if (!mUserLocationMoved) {
            mUserLocation = new Location(location);

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            mMap.moveCamera(getCameraUpdateFactory().newLatLngZoom(latLng, PositionAbstractFragment.DEFAULT_ZOOM));
        }
    }

    //@Override
    public void onLocationChanged(Location location) {
        if (mMap != null) {
            positionMarker(location);
        }
    }

    // used only for places
    void prepareLayout(final boolean resume) {
        if (mRecyclerView != null) {
            int viewHeight = getView().getMeasuredHeight();
            if (viewHeight == 0) {
                return;
            }
            mOverScrollHeight = viewHeight - ViewUtils.dp(getContext(), 66);

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mMapViewClip.getLayoutParams();
            layoutParams.height = mOverScrollHeight;
            mMapViewClip.setLayoutParams(layoutParams);

            ((IPlacesAdapter) mAdapter).setOverScrollHeight(mOverScrollHeight);
            layoutParams = (FrameLayout.LayoutParams) mMapView.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.height = mOverScrollHeight + ViewUtils.dp(getContext(), 10);
                if (mMap != null) {
                    mMap.setPadding(0, 0, 0, ViewUtils.dp(getContext(), 10));
                }
                mMapView.setLayoutParams(layoutParams);
            }
            mAdapter.notifyDataSetChanged();

            if (resume) {
                mRecyclerViewLayoutManager.scrollToPositionWithOffset(0, -(int) (ViewUtils.dp(getContext(), 56) * 2.5f + ViewUtils.dp(getContext(), 36 + 66)));
                updateClipView(mRecyclerViewLayoutManager.findFirstVisibleItemPosition());
                mRecyclerView.post(new Runnable() {
                    @Override
                    public void run() {
                        mRecyclerViewLayoutManager.scrollToPositionWithOffset(0, -(int) (ViewUtils.dp(getContext(), 56) * 2.5f + ViewUtils.dp(getContext(), 36 + 66)));
                        updateClipView(mRecyclerViewLayoutManager.findFirstVisibleItemPosition());
                    }
                });
            }
            else {
                updateClipView(mRecyclerViewLayoutManager.findFirstVisibleItemPosition());
            }

        }
    }

    // used only for places
    void updateClipView(int firstVisibleItem) {
        if (firstVisibleItem == RecyclerView.NO_POSITION) {
            return;
        }
        int height = 0;
        int top = 0;
        View child = mRecyclerView.getChildAt(0);
        if (child != null) {
            if (firstVisibleItem == 0) {
                top = child.getTop();
                height = mOverScrollHeight + (top < 0 ? top : 0);
            }
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mMapViewClip.getLayoutParams();
            if (layoutParams != null) {
                if (height <= 0) {
                    if (mMapView.getVisibility() == View.VISIBLE) {
                        mMapView.setVisibility(View.INVISIBLE);
                        mMapViewClip.setVisibility(View.INVISIBLE);
                    }
                }
                else {
                    if (mMapView.getVisibility() == View.INVISIBLE) {
                        mMapView.setVisibility(View.VISIBLE);
                        mMapViewClip.setVisibility(View.VISIBLE);
                    }
                }

                mMapViewClip.setTranslationY(Math.min(0, top));
                mMapView.setTranslationY(Math.max(0, -top / 2));
                mMapPin.setTranslationY(mMarkerTop = -top - ViewUtils.dp(getContext(), 42) + height / 2);
                mPinX.setTranslationY(-top - ViewUtils.dp(getContext(), 7) + height / 2);

                layoutParams = (FrameLayout.LayoutParams) mMapView.getLayoutParams();
                if (layoutParams != null && layoutParams.height != mOverScrollHeight + ViewUtils.dp(getContext(), 10)) {
                    layoutParams.height = mOverScrollHeight + ViewUtils.dp(getContext(), 10);
                    if (mMap != null) {
                        mMap.setPadding(0, 0, 0, ViewUtils.dp(getContext(), 10));
                    }
                    mMapView.setLayoutParams(layoutParams);
                }
            }
        }
    }

    private void askPermissions() {
        if (!Permissions.canAccessLocation(getContext())) {
            if (!mPermissionAsked) {
                Permissions.requestLocation(this, getString(R.string.err_location_denied));
                mPermissionAsked = true;
            }
        }
        else {
            if (mMap != null)
                mMap.setMyLocationEnabled(true);
            requestLocation();
        }
    }

}
