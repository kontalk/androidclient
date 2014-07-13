package org.kontalk.ui;

import org.kontalk.data.Contact;

import java.util.regex.Pattern;


/**
 * Interface for message content views for all message component types.
 * @author Daniele Ricci
 */
public interface MessageContentView<T> {

    /** Binds the given component with this view. */
    public void bind(T component, Contact contact, Pattern highlight);

    // TODO

}
