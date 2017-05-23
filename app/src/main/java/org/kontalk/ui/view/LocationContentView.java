package org.kontalk.ui.view;

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
import org.kontalk.message.LocationComponent;
import org.kontalk.ui.ComposeMessage;
import org.kontalk.util.MediaStorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.regex.Pattern;

/**
 * @author andreacappelli
 * @version 1.0
 *          DATE: 23/05/17
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

        Bitmap bitmap = getBitmap();
        showBitmap(bitmap);
    }

    /** This method might be called from a thread other than the main thread. */
    void showBitmap(Bitmap bitmap) {
        // this method might be called from another thread
        final LocationComponent component = mComponent;
        if (component == null)
            return;

        if (bitmap != null) {
            mContent.setImageBitmap(bitmap);
            mPlaceholder.setVisibility(GONE);
            mContent.setVisibility(VISIBLE);
        }
        else {
            String placeholder = CompositeMessage.getSampleTextContent("Position");
            mPlaceholder.setText(placeholder);
            TextContentView.setTextStyle(mPlaceholder);
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

        return BitmapFactory.decodeResource(getContext().getResources(), R.drawable.attach_location);
    }

    @Override
    public void unbind() {
        clear();
    }

    @Override
    public LocationComponent getComponent() {
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

    public static LocationContentView create(LayoutInflater inflater, ViewGroup parent) {
        return (LocationContentView) inflater.inflate(R.layout.message_content_location,
                parent, false);
    }

    interface ThumbnailListener {
        void onThumbnailGenerated(File previewFile);
    }

    final static class GenerateThumbnailTask extends AsyncTask<Void, Void, Boolean> {
        private final Uri mLocalUri;
        private final File mPreviewFile;
        private final WeakReference<Context> mContext;
        private final ImageContentView.ThumbnailListener mListener;

        GenerateThumbnailTask(Context context, Uri localUri, File previewFile, ImageContentView.ThumbnailListener listener) {
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
