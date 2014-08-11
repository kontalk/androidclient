package org.kontalk.ui;

import java.util.regex.Pattern;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.TextComponent;
import org.kontalk.message.VCardComponent;


/**
 * Message component for {@link org.kontalk.message.VCardComponent}.
 * @author Daniele Ricci
 */
public class VCardContentView extends TextView
    implements MessageContentView<VCardComponent> {

    private VCardComponent mComponent;

    public VCardContentView(Context context) {
        super(context);
    }

    public VCardContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VCardContentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void bind(VCardComponent component, Contact contact, Pattern highlight) {
        mComponent = component;

        String text = CompositeMessage.getSampleTextContent(component.getMime());
        setText(text);
    }

    public void unbind() {
        clear();
    }

    public VCardComponent getComponent() {
        return mComponent;
    }

    @Override
    public int getPriority() {
        return 7;
    }

    private void clear() {
        mComponent = null;
    }

    public static VCardContentView create(LayoutInflater inflater, ViewGroup parent) {
        VCardContentView view = (VCardContentView) inflater.inflate(R.layout.message_content_vcard,
            parent, false);
        return view;
    }

}
