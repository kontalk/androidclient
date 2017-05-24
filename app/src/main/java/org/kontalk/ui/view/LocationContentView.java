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

package org.kontalk.ui.view;

import java.util.regex.Pattern;

import com.bumptech.glide.Glide;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.message.LocationComponent;
import org.kontalk.position.GMStaticUrlBuilder;
import org.kontalk.ui.ComposeMessage;

/**
 * Message component for {@link LocationComponent}.
 * @author andreacappelli
 */

public class LocationContentView extends FrameLayout
    implements MessageContentView<LocationComponent> {
    static final String TAG = ComposeMessage.TAG;

    private LocationComponent mComponent;
    private ImageView mContent;
    private TextView mPlaceholder;

    public LocationContentView(Context context) {
        super(context);
    }

    public LocationContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LocationContentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = (ImageView) findViewById(R.id.content);
        mPlaceholder = (TextView) findViewById(R.id.placeholder);
    }

    @Override
    public void bind(long messageId, LocationComponent component, Pattern highlight) {
        mComponent = component;

        showMap();
    }

    private void showMap() {
        final LocationComponent component = mComponent;
        if (component == null)
            return;

        mPlaceholder.setVisibility(GONE);
        mContent.setVisibility(VISIBLE);

        String imageURL = new GMStaticUrlBuilder().setCenter(mComponent.getLatitude(),
            mComponent.getLongitude()).setMarker(mComponent.getLatitude(), mComponent.getLongitude()).toString();

        Glide.with(getContext()).load(imageURL).into(mContent);
    }

    @Override
    public void unbind() {
        clear();
    }

    @Override
    public LocationComponent getComponent() {
        return mComponent;
    }

    /**
     * Image is always on top.
     */
    @Override
    public int getPriority() {
        return 1;
    }

    private void clear() {
        mComponent = null;
        mContent.setImageBitmap(null);
    }

    public static LocationContentView create(LayoutInflater inflater, ViewGroup parent) {
        return (LocationContentView) inflater.inflate(R.layout.message_content_location,
            parent, false);
    }

}
