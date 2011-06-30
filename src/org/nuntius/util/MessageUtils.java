package org.nuntius.util;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.nuntius.R;
import org.nuntius.client.AbstractMessage;
import org.nuntius.client.ImageMessage;
import org.nuntius.provider.MyMessages.Messages;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.text.format.Time;


public final class MessageUtils {
    private MessageUtils() {}

    public static String formatTimeStampString(Context context, long when) {
        return formatTimeStampString(context, when, false);
    }

    public static String formatTimeStampString(Context context, long when, boolean fullFormat) {
        Time then = new Time();
        then.set(when);
        Time now = new Time();
        now.setToNow();

        // Basic settings for formatDateTime() we want for all cases.
        int format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT |
                           DateUtils.FORMAT_ABBREV_ALL |
                           DateUtils.FORMAT_CAP_AMPM;

        // If the message is from a different year, show the date and year.
        if (then.year != now.year) {
            format_flags |= DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE;
        } else if (then.yearDay != now.yearDay) {
            // If it is from a different day than today, show only the date.
            format_flags |= DateUtils.FORMAT_SHOW_DATE;
        } else {
            // Otherwise, if the message is from today, show the time.
            format_flags |= DateUtils.FORMAT_SHOW_TIME;
        }

        // If the caller has asked for full details, make sure to show the date
        // and time no matter what we've determined above (but still make showing
        // the year only happen if it is a different year from today).
        if (fullFormat) {
            format_flags |= (DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
        }

        return DateUtils.formatDateTime(context, when, format_flags);
    }

    private static String convertToHex(byte[] data) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
            int halfbyte = (data[i] >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                if ((0 <= halfbyte) && (halfbyte <= 9))
                    buf.append((char) ('0' + halfbyte));
                else
                    buf.append((char) ('a' + (halfbyte - 10)));
                halfbyte = data[i] & 0x0F;
            } while(two_halfs++ < 1);
        }
        return buf.toString();
    }

    public static String sha1(String text)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {

        MessageDigest md;
        md = MessageDigest.getInstance("SHA-1");
        byte[] sha1hash = new byte[40];
        md.update(text.getBytes("iso-8859-1"), 0, text.length());
        sha1hash = md.digest();

        return convertToHex(sha1hash);
    }

    public static CharSequence getMessageDetails(Context context, AbstractMessage<?> msg, String decodedPeer) {
        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        // Message Type: Text message.
        details.append(res.getString(R.string.message_type_label));

        int resId;
        if (msg instanceof ImageMessage)
            resId = R.string.image_message;
        else
            resId = R.string.text_message;

        details.append(res.getString(resId));

        // Address: ***
        details.append('\n');
        if (msg.getDirection() == Messages.DIRECTION_OUT)
            details.append(res.getString(R.string.to_address_label));
        else
            details.append(res.getString(R.string.from_label));

        details.append(decodedPeer);

        // Date: ***
        details.append('\n');
        int status = msg.getStatus();
        switch (status) {
            case Messages.STATUS_INCOMING:
            case Messages.STATUS_RECEIVED:
            case Messages.STATUS_CONFIRMED:
                resId = R.string.received_label;
                break;
            case Messages.STATUS_ERROR:
                resId = R.string.send_error;
                break;
            case Messages.STATUS_NOTACCEPTED:
                resId = R.string.send_refused;
                break;
            case Messages.STATUS_SENT:
                resId = R.string.sent_label;
                break;
            default: // including Messages.STATUS_SENDING
                resId = -1;
        }

        details.append(res.getString(resId));

        long date = msg.getTimestamp();
        details.append(MessageUtils.formatTimeStampString(context, date, true));

        // Error code: ***
        /*
        int errorCode = cursor.getInt(MessageListAdapter.COLUMN_SMS_ERROR_CODE);
        if (errorCode != 0) {
            details.append('\n')
                .append(res.getString(R.string.error_code_label))
                .append(errorCode);
        }
        */

        return details.toString();
    }


}
