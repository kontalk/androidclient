package org.kontalk.xmpp.util;

import java.util.Date;

import org.kontalk.xmpp.message.PlainTextMessage;


/**
 * Generates a Message/CPIM body.
 * @author Daniele Ricci
 */
public class CPIMMessage {

    private static final String TYPE = "Message/CPIM";
    private static final String CHARSET = "utf-8";

    private final String mFrom;
    private final String mTo;
    private final Date mDate;
    private final String mMime;
    private final String mBody;

    private StringBuilder mBuf;

    /** Constructs a new plain text message. */
    public CPIMMessage(String from, String to, Date date, String body) {
        mFrom = from;
        mTo = to;
        mDate = date;
        mMime = PlainTextMessage.MIME_TYPE;
        mBody = body;
    }

    public String toString() {
        if (mBuf == null) {
            mBuf = new StringBuilder("Content-Type: ")
                .append(TYPE)
                .append("\nFrom: ")
                .append(mFrom)
                .append("\nTo: ")
                .append(mTo)
                .append("\nDateTime: ")
                // TODO date format
                .append(mDate.toString())
                .append("\nContent-Type: ")
                .append(mMime)
                .append("; charset=")
                .append(CHARSET)
                .append("\n\n")
                .append(mBody);
        }

        return mBuf.toString();
    }

    public byte[] toByteArray() {
        return toString().getBytes();
    }

}
