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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.regex.Pattern;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.ImageComponent;
import org.kontalk.ui.ComposeMessage;
import org.kontalk.util.MediaStorage;


/**
 * Message component for {@link ImageComponent}.
 * @author Daniele Ricci
 */
public class ImageContentView extends FrameLayout
        implements MessageContentView<ImageComponent> {
    static final String TAG = ComposeMessage.TAG;

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

        Bitmap bitmap = getBitmap();
        showBitmap(bitmap);
    }

    /** This method might be called from a thread other than the main thread. */
    void showBitmap(Bitmap bitmap) {
        // this method might be called from another thread
        final ImageComponent component = mComponent;
        if (component == null)
            return;

        if (bitmap != null) {
            mContent.setImageBitmap(bitmap);
            mPlaceholder.setVisibility(GONE);
            mContent.setVisibility(VISIBLE);
        }
        else {
            String placeholder = CompositeMessage.getSampleTextContent(component.getContent().getMime());
            mPlaceholder.setText(placeholder);
            TextContentView.setTextStyle(mPlaceholder, true);
            mContent.setVisibility(GONE);
            mPlaceholder.setVisibility(VISIBLE);
        }
    }

    private BitmapFactory.Options bitmapOptions() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return options;
    }

    Bitmap loadPreview(File previewFile) throws IOException {
        InputStream in = new FileInputStream(previewFile);
        BitmapFactory.Options options = bitmapOptions();
        Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
        in.close();
        return bitmap;
    }

    private Bitmap getBitmap() {
        /*
         * local_uri is used for referencing the original media.
         * preview_uri is used to load the media thumbnail.
         * If preview_uri is null or cannot be found, a thumbnail is
         * generated on the fly from local_uri - if possible.
         */

        File previewFile = mComponent.getPreviewFile();
        Uri localUri = mComponent.getLocalUri();
        try {
            // preview path
            if (previewFile != null) {
                // load from file - we know it's a file uri
                return loadPreview(previewFile);
            }
        }
        catch (Exception e) {
            Log.w(TAG, "unable to load thumbnail, generating one");

            /*
             * unable to load preview - generate thumbnail
             * Of course a thumbnail can be generated only if the image has
             * already been downloaded.
             */
            if (localUri != null) {
                new GenerateThumbnailTask(getContext(), localUri, previewFile, new ThumbnailListener() {
                    @Override
                    public void onThumbnailGenerated(File previewFile) {
                        try {
                            Bitmap bitmap = loadPreview(previewFile);
                            showBitmap(bitmap);
                        }
                        catch (IOException e) {
                            // since at this point anything can happen, just ignore any errors
                            Log.w(TAG, "unable to load generated thumbnail", e);
                        }
                    }
                }).execute();
            }
        }
        return null;
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
        return 4;
    }

    private void clear() {
        mComponent = null;
        mContent.setImageBitmap(null);
    }

    public static ImageContentView create(LayoutInflater inflater, ViewGroup parent) {
        return (ImageContentView) inflater.inflate(R.layout.message_content_image,
            parent, false);
    }

    interface ThumbnailListener {
        void onThumbnailGenerated(File previewFile);
    }

    final static class GenerateThumbnailTask extends AsyncTask<Void, Void, Boolean> {
        private final Uri mLocalUri;
        private final File mPreviewFile;
        private final WeakReference<Context> mContext;
        private final ThumbnailListener mListener;

        GenerateThumbnailTask(Context context, Uri localUri, File previewFile, ThumbnailListener listener) {
            mContext = new WeakReference<>(context);
            mLocalUri = localUri;
            mPreviewFile = previewFile;
            mListener = listener;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Context context = mContext.get();
            if (context != null) {
                try {
                    MediaStorage.cacheThumbnail(context, mLocalUri, mPreviewFile, false);
                    return true;
                }
                catch (Exception e) {
                    Log.e(TAG, "unable to generate thumbnail", e);
                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result != null && result) {
                mListener.onThumbnailGenerated(mPreviewFile);
            }
        }
    }

}
