package org.kontalk.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.AudioComponent;
import org.kontalk.message.AudioComponent;

import java.util.regex.Pattern;


/**
 * Audio content view for {@link AudioComponent}s.
 */
public class AudioContentView extends LinearLayout
        implements MessageContentView<AudioComponent> {

    private AudioComponent mComponent;

    public AudioContentView(Context context) {
        super(context);
    }

    public AudioContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AudioContentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void bind(AudioComponent component, Contact contact, Pattern highlight) {
        mComponent = component;
        // TODO
    }

    public void unbind() {
        clear();
    }

    public AudioComponent getComponent() {
        return mComponent;
    }

    private void clear() {
        mComponent = null;
    }

    public static AudioContentView create(LayoutInflater inflater, ViewGroup parent) {
        return (AudioContentView) inflater.inflate(R.layout.message_content_audio,
            parent, false);
    }

}
