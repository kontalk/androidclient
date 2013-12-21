package org.kontalk.xmpp.message;

import org.kontalk.xmpp.crypto.Coder;


/**
 * Plain text message component (always cleartext).
 * @author Daniele Ricci
 */
public class TextComponent extends MessageComponent<String> {

    public static final String MIME_TYPE = "text/plain";

	public TextComponent(String text) {
		super(text, text.length(), false, Coder.SECURITY_CLEARTEXT);
	}

    public static boolean supportsMimeType(String mime) {
        return MIME_TYPE.equalsIgnoreCase(mime);
    }


}
