package org.kontalk.xmpp.message;


/**
 * Raw data message component.
 * This is used mainly when the message is still encrypted.
 * @author Daniele Ricci
 */
public class RawComponent extends MessageComponent<byte[]> {

	public RawComponent(byte[] text, boolean encrypted, int securityFlags) {
		super(text, text.length, encrypted, securityFlags);
	}

}
