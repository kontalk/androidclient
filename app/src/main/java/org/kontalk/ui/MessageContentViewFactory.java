package org.kontalk.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.MessageComponent;
import org.kontalk.message.TextComponent;

import java.util.regex.Pattern;


/**
 * Factory for building {@link MessageContentView}s out of
 * {@link MessageComponent}s.
 * @author Daniele Ricci
 */
public class MessageContentViewFactory {

    private MessageContentViewFactory() {
    }

    /** Builds the content for the given component. */
    @SuppressWarnings("unchecked")
    public static MessageContentView<?> createContent(LayoutInflater inflater,
            ViewGroup parent, MessageComponent<?> component,
            Contact contact, Pattern highlight) {

        // using conditionals to avoid reflection
        MessageContentView<MessageComponent<?>> view = null;

        if (component instanceof TextComponent) {
            view = (MessageContentView<MessageComponent<?>>) inflater
                .inflate(R.layout.message_content_text, parent, false);
        }

        if (view != null)
            view.bind(component, contact, highlight);

        return view;
    }

}
