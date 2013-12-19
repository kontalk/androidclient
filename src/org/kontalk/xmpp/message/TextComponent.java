package org.kontalk.xmpp.message;


/**
 * Plain text message component.
 * @author Daniele Ricci
 */
public class TextComponent extends MessageComponent<byte[]> {

	protected long mLength;

	public TextComponent(String text, boolean encrypted) {
		this(text.getBytes(), encrypted);
	}

	public TextComponent(byte[] text, boolean encrypted) {
		super(text, encrypted);

		mLength = text.length;
	}

}
