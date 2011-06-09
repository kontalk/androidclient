package org.nuntius.data;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * A message server ID.
 * Example: 334D0F63F3672A05F91648E865C866AE3AE420FE20110603190225+0200I7E0D
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageID {
    public static final int LENGTH = 64;
    private static final SimpleDateFormat dateFormat =
        new SimpleDateFormat("yyyyMMddHHmmssZ");

    private String mServerFingerprint;
    private String mTimestamp;
    private String mPad;

    private Date mParsedTimestamp;

    private MessageID(String serverFingerprint, String timestamp, String pad)
            throws ParseException {
        mServerFingerprint = serverFingerprint;
        mTimestamp = timestamp;
        mPad = pad;
        mParsedTimestamp = dateFormat.parse(mTimestamp);
    }

    public String getServerFingerprint() {
        return mServerFingerprint;
    }

    public Date getDate() {
        return mParsedTimestamp;
    }

    public long getTime() {
        return mParsedTimestamp.getTime();
    }

    public String toString() {
        return mServerFingerprint + mTimestamp + mPad;
    }

    public static MessageID parse(String msgId) throws ParseException {
        if (msgId == null || msgId.length() != LENGTH)
            throw new ParseException("invalid message id", 0);

        String fp = msgId.substring(0, 40);
        String tm = msgId.substring(40, 59);
        String pad = msgId.substring(59, 64);
        return new MessageID(fp, tm, pad);
    }
}
