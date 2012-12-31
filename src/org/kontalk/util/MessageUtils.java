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

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.kontalk.R;
import org.kontalk.message.AbstractMessage;
import org.kontalk.message.ImageMessage;
import org.kontalk.message.VCardMessage;
import org.kontalk.provider.MyMessages.Messages;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;


public final class MessageUtils {
    private static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);
    private static final ForegroundColorSpan STYLE_RED = new ForegroundColorSpan(Color.RED);

    private MessageUtils() {}

    public static void convertSmileys(Context context, Spannable text, int size) {
        // remove all of our spans first
        SmileyImageSpan[] oldSpans = text.getSpans(0, text.length(), SmileyImageSpan.class);
        for (int i = 0; i < oldSpans.length; i++)
            text.removeSpan(oldSpans[i]);

        int len = text.length();
        int skip;
        for (int i = 0; i < len; i += skip) {
            skip = 0;
            int icon = 0;
            char c = text.charAt(i);
            if (Emoji.isSoftBankEmoji(c)) {
                try {
                    icon = Emoji.getSoftbankEmojiResource(c);
                    skip = 1;
                }
                catch (ArrayIndexOutOfBoundsException e) {
                    // skip code
                }
            }

            if (icon == 0) {
                // softbank encoding not found, try extracting a code point
                int unicode = Character.codePointAt(text, i);
                // calculate skip count if not previously set
                if (skip == 0)
                    skip = Character.charCount(unicode);

                // avoid looking up if unicode < 0xFF
                if (unicode > 0xff)
                    icon = Emoji.getEmojiResource(context, unicode);
            }

            if (icon > 0) {
                // set emoji span
                SmileyImageSpan span = new SmileyImageSpan(context, icon, size);
                text.setSpan(span, i, i+skip, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE /* | Spannable.SPAN_COMPOSING*/);
            }
        }
    }

    public static final class SmileyImageSpan extends DynamicDrawableSpan {
        public static final int SIZE_DIALOG = 40;
        public static final int SIZE_EDITABLE = 24;
        public static final int SIZE_LISTITEM = 18;

        private final Context mContext;
        private final int mResourceId;
        private final int mSize;
        private Drawable mDrawable;

        public SmileyImageSpan(Context context, int resourceId, int size) {
            super(ALIGN_BOTTOM);
            mContext = context;
            mResourceId = resourceId;
            mSize = size;
        }

        public Drawable getDrawable() {
            Drawable drawable = null;

            if (mDrawable != null) {
                drawable = mDrawable;
            }
            else {
                try {
                    drawable = mContext.getResources().getDrawable(mResourceId);
                    int size = getDensityPixel(mContext, mSize);
                    drawable.setBounds(0, 0, size, size);
                } catch (Exception e) {
                    Log.e("sms", "Unable to find resource: " + mResourceId);
                }
            }

            return drawable;
        }
    }

    private static final class ImageAdapter extends BaseAdapter {
        private Context mContext;
        private int[][] mTheme;

        public ImageAdapter(Context c, int[][] theme) {
            mContext = c;
            mTheme = theme;
        }

        public int getCount() {
            return mTheme[0].length;
        }

        /** Actually not used. */
        public Object getItem(int position) {
            int icon = Emoji.getEmojiResource(mContext, mTheme[0][position]);
            return mContext.getResources().getDrawable(icon);
        }

        public long getItemId(int position) {
            return mTheme[0][position];
        }

        public boolean hasStableIds() {
            return true;
        }

        // create a new ImageView for each item referenced by the Adapter
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView imageView;
            if (convertView == null) {
                imageView = new ImageView(mContext);
                int size = getDensityPixel(mContext, SmileyImageSpan.SIZE_DIALOG/*+8*/);
                imageView.setLayoutParams(new GridView.LayoutParams(size, size));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                int padding = getDensityPixel(mContext, 4);
                imageView.setPadding(padding, padding, padding, padding);
            } else {
                imageView = (ImageView) convertView;
            }

            int icon = Emoji.getEmojiResource(mContext, mTheme[0][position]);
            imageView.setImageResource(icon);
            return imageView;
        }
    }

    public static Dialog smileysDialog(Context context, AdapterView.OnItemClickListener listener) {
        ImageAdapter adapter = new ImageAdapter(context, Emoji.emojiGroups);

        LayoutInflater inflater = LayoutInflater.from(context);
        GridView grid = (GridView) inflater.inflate(R.layout.grid_smileys, null);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(listener);

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        //b.setTitle("Select smiley");
        b.setView(grid);
        b.setNegativeButton(android.R.string.cancel, null);

        return b.create();
    }

    private static int getDensityPixel(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

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

    public static CharSequence getFileInfoMessage(Context context, AbstractMessage<?> msg, String decodedPeer) {
        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();
        int direction = msg.getDirection();

        // To/From
        if (direction == Messages.DIRECTION_OUT)
            details.append(res.getString(R.string.to_address_label));
        else
            details.append(res.getString(R.string.from_label));

        details.append(decodedPeer);

        // Message type
        details.append('\n');
        details.append(res.getString(R.string.message_type_label));

        int resId;
        if (msg instanceof ImageMessage)
            resId = R.string.image_message;
        else if (msg instanceof VCardMessage)
            resId = R.string.vcard_message;
        else
            resId = R.string.text_message;

        details.append(res.getString(resId));

        // Message length
        details.append('\n');
        details.append(res.getString(R.string.size_label));
        details.append((msg.getLength() >= 0) ?
            humanReadableByteCount(msg.getLength(), false) :
                res.getString(R.string.size_unknown));

        return details.toString();
    }

    public static CharSequence getMessageDetails(Context context, AbstractMessage<?> msg, String decodedPeer) {
        SpannableStringBuilder details = new SpannableStringBuilder();
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
        if (msg.wasEncrypted()) {
            details.append(res.getString(R.string.yes));
        }
        else {
            CharSequence noText = res.getString(R.string.no);
            int startPos = details.length();
            details.append(noText);
            details.setSpan(STYLE_BOLD, startPos, details.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            details.setSpan(STYLE_RED, startPos, details.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // Message length
        details.append('\n');
        details.append(res.getString(R.string.size_label));
        details.append((msg.getLength() >= 0) ?
            humanReadableByteCount(msg.getLength(), false) :
                res.getString(R.string.size_unknown));

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
                        res.getString(R.string.delivered_label), msg.getStatusChanged(), true);
            }
            else if (status == Messages.STATUS_NOTDELIVERED) {
                details.append('\n');
                appendTimestamp(context, details,
                        res.getString(R.string.notdelivered_label), msg.getStatusChanged(), true);
            }

        }

        // TODO Error code/reason

        return details;
    }

    private static void appendTimestamp(Context context, Appendable details,
            String label, long time, boolean fullFormat) {
        try {
            details.append(label);
            details.append(MessageUtils.formatTimeStampString(context, time, fullFormat));
        }
        catch (IOException e) {
            // ignored
        }
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
