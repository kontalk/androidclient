/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

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

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.ImageComponent;

import java.util.regex.Pattern;


/**
 * Message component for {@link ImageComponent}.
 * @author Daniele Ricci
 */
public class ImageContentView extends FrameLayout
        implements MessageContentView<ImageComponent> {

    private ImageComponent mComponent;
    private ImageView mContent;
    private TextView mPlaceholder;

    public ImageContentView(Context context) {
        super(context);
    }

    public ImageContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageContentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = (ImageView) findViewById(R.id.content);
        mPlaceholder = (TextView) findViewById(R.id.placeholder);
    }

    @Override
    public void bind(long messageId, ImageComponent component, Pattern highlight) {
        mComponent = component;

        Bitmap bitmap = mComponent.getBitmap();
        if (bitmap != null) {
            mContent.setImageBitmap(bitmap);
            mPlaceholder.setVisibility(GONE);
            mContent.setVisibility(VISIBLE);
        }
        else {
            String placeholder = CompositeMessage.getSampleTextContent(component.getContent().getMime());
            mPlaceholder.setText(placeholder);
            TextContentView.setTextStyle(mPlaceholder);
            mContent.setVisibility(GONE);
            mPlaceholder.setVisibility(VISIBLE);
        }
    }

    @Override
    public void unbind() {
        clear();
    }

    @Override
    public ImageComponent getComponent() {
        return mComponent;
    }

    /** Image is always on top. */
    @Override
    public int getPriority() {
        return 1;
    }

    private void clear() {
        mComponent = null;
        mContent.setImageBitmap(null);
    }

    public static ImageContentView create(LayoutInflater inflater, ViewGroup parent) {
        return (ImageContentView) inflater.inflate(R.layout.message_content_image,
            parent, false);
    }

}
