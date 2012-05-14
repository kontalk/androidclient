/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.kontalk.R;
import org.kontalk.message.AbstractMessage;
import org.kontalk.message.ImageMessage;
import org.kontalk.message.VCardMessage;
import org.kontalk.provider.MyMessages.Messages;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.text.format.Time;


public final class MessageUtils {
    private MessageUtils() {}

    public static CharSequence formatRelativeTimeSpan(Context context, long when) {
        int format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT |
                DateUtils.FORMAT_ABBREV_ALL |
                DateUtils.FORMAT_CAP_AMPM;

        return DateUtils.getRelativeDateTimeString(context, when,
                DateUtils.SECOND_IN_MILLIS, DateUtils.DAY_IN_MILLIS * 2,
                format_flags);
    }

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
            throws NoSuchAlgorithmException {

        MessageDigest md;
        md = MessageDigest.getInstance("SHA-1");
        byte[] sha1hash = new byte[40];
        md.update(text.getBytes(), 0, text.length());
        sha1hash = md.digest();

        return convertToHex(sha1hash);
    }

    public static CharSequence getMessageDetails(Context context, AbstractMessage<?> msg, String decodedPeer) {
        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();
        int direction = msg.getDirection();

        // Message type
        details.append(res.getString(R.string.message_type_label));

        int resId;
        if (msg instanceof ImageMessage)
            resId = R.string.image_message;
        else if (msg instanceof VCardMessage)
        	resId = R.string.vcard_message;
        else
            resId = R.string.text_message;

        details.append(res.getString(resId));

        // To/From
        details.append('\n');
        if (direction == Messages.DIRECTION_OUT)
            details.append(res.getString(R.string.to_address_label));
        else
            details.append(res.getString(R.string.from_label));

        details.append(decodedPeer);

        // Encrypted
        details.append('\n');
        details.append(res.getString(R.string.encrypted_label));
        resId = (msg.wasEncrypted()) ? R.string.yes : R.string.no;
        details.append(res.getString(resId));

        // Message length
        details.append('\n');
        // TODO i18n
        details.append("Size: ");
        // TODO human-readable size
        details.append((msg.getLength() >= 0) ?
            // TODO i18n
            humanReadableByteCount(msg.getLength(), false) : "(unknown)");

        // Date
        int status = msg.getStatus();

        // incoming message
        if (direction == Messages.DIRECTION_IN) {
            details.append('\n');
            appendTimestamp(context, details,
                    res.getString(R.string.received_label), msg.getTimestamp(), true);
            details.append('\n');
            appendTimestamp(context, details,
                    res.getString(R.string.sent_label), msg.getServerTimestamp().getTime(), true);
        }
        // outgoing messages
        else {
            long timestamp = 0;
            switch (status) {
                case Messages.STATUS_NOTACCEPTED:
                    resId = R.string.refused_label;
                    timestamp = msg.getStatusChanged();
                    break;
                case Messages.STATUS_ERROR:
                    resId = R.string.error_label;
                    timestamp = msg.getStatusChanged();
                    break;
                case Messages.STATUS_SENT:
                case Messages.STATUS_SENDING:
                case Messages.STATUS_RECEIVED:
                case Messages.STATUS_NOTDELIVERED:
                    resId = R.string.sent_label;
                    timestamp = msg.getTimestamp();
                    break;
                default:
                    resId = -1;
                    break;
            }

            if (resId > 0) {
                details.append('\n');
                appendTimestamp(context, details,
                        res.getString(resId), timestamp, true);
            }

            // print out received if any
            if (status == Messages.STATUS_RECEIVED) {
                details.append('\n');
                appendTimestamp(context, details,
                        res.getString(R.string.received_label), msg.getStatusChanged(), true);
            }
            else if (status == Messages.STATUS_NOTDELIVERED) {
                details.append('\n');
                appendTimestamp(context, details,
                        res.getString(R.string.notdelivered_label), msg.getStatusChanged(), true);
            }

        }

        // TODO Error code/reason

        return details.toString();
    }

    private static void appendTimestamp(Context context, StringBuilder details,
            String label, long time, boolean fullFormat) {
        details.append(label);
        details.append(MessageUtils.formatTimeStampString(context, time, fullFormat));
    }

    /**
     * Cool handy method to format a size in bytes in some human readable form.
     * @see http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
     */
    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "i" : "");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }


}
