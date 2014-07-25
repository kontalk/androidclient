package org.kontalk.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.ImageComponent;

import java.util.regex.Pattern;


/**
 * Message component for {@link ImageComponent}.
 * @author Daniele Ricci
 */
public class ImageContentView extends ImageView
        implements MessageContentView<ImageComponent> {

    private ImageComponent mComponent;

    public ImageContentView(Context context) {
        super(context);
    }

    public ImageContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageContentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void bind(ImageComponent component, Contact contact, Pattern highlight) {
        mComponent = component;

        // prepend some text for the ImageSpan
        //String placeholder = CompositeMessage.getSampleTextContent(component.getContent().getMime());

        Bitmap bitmap = mComponent.getBitmap();
        if (bitmap != null)
            setImageBitmap(bitmap);

        // TODO else: maybe some placeholder like Image: image/jpeg

    }

    public void unbind() {
        clear();
    }

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
        setImageBitmap(null);
    }

    public static ImageContentView create(LayoutInflater inflater, ViewGroup parent) {
        return (ImageContentView) inflater.inflate(R.layout.message_content_image,
            parent, false);
    }

}
