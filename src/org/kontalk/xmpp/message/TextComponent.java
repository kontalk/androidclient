package org.kontalk.xmpp.message;


/**
 * Plain text message component.
 * @author Daniele Ricci
 */
public class TextComponent extends MessageComponent<String> {

    public static final String MIME_TYPE = "text/plain";

	public TextComponent(String text) {
		super(text, text.length(), false);
	}

    public static boolean supportsMimeType(String mime) {
        return MIME_TYPE.equalsIgnoreCase(mime);
    }


}
