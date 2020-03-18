/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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
import com.bumptech.glide.load.model.GlideUrl;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.message.LocationComponent;
import org.kontalk.position.PositionManager;
import org.kontalk.position.RequestDetails;
import org.kontalk.ui.ComposeMessage;
import org.kontalk.util.CombinedDrawable;

/**
 * Message component for {@link LocationComponent}.
 * @author andreacappelli
 */

public class LocationContentView extends RelativeLayout
    implements MessageContentView<LocationComponent> {
    static final String TAG = ComposeMessage.TAG;

    private LocationComponent mComponent;
    private ImageView mContent;
    private TextView mPlaceholder;
    private TextView mName;
    private TextView mAddress;

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
        mContent = findViewById(R.id.content);
        mPlaceholder = findViewById(R.id.placeholder);
        mName = findViewById(R.id.name);
        mAddress = findViewById(R.id.address);
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

        RequestDetails imageURL = PositionManager.getStaticMapUrl(getContext(),
            mComponent.getLatitude(), mComponent.getLongitude(), 15,
            200, 100, (int) getContext().getResources().getDisplayMetrics().density);

        Drawable drawable = createRoundRectDrawableWithIcon(0, R.drawable.ic_pin);

        mContent.setBackground(drawable);

        if (mComponent.getText() != null) {
            mName.setVisibility(VISIBLE);
            mName.setText(mComponent.getText());
        }

        if (mComponent.getStreet() != null) {
            mAddress.setVisibility(VISIBLE);
            mAddress.setText(mComponent.getStreet());
        }

        if (imageURL != null) {
            GlideUrl url = new GlideUrl(imageURL.url, imageURL.headers);
            Glide.with(getContext())
                .load(url)
                .into(mContent);
        }
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
        return 3;
    }

    private void clear() {
        mComponent = null;
        mContent.setImageBitmap(null);
    }

    public static LocationContentView create(LayoutInflater inflater, ViewGroup parent) {
        return (LocationContentView) inflater.inflate(R.layout.message_content_location,
            parent, false);
    }

    public Drawable createRoundRectDrawableWithIcon(int rad, int iconRes) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        defaultDrawable.getPaint().setColor(ContextCompat.getColor(getContext(), R.color.map_placeholder_background));
        Drawable drawable = getContext().getResources().getDrawable(iconRes).mutate();
        DrawableCompat.setTint(drawable, ContextCompat.getColor(getContext(), R.color.app_primary));

        return new CombinedDrawable(defaultDrawable, drawable);
    }
}
