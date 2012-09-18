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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;


public final class MessageUtils {
    /** Default smiley theme. */
    public static final int[] SMILEY_DEFAULT = {
        R.drawable.sm_default_afraid,
        R.drawable.sm_default_amorous,
        R.drawable.sm_default_angel,
        R.drawable.sm_default_angry,
        R.drawable.sm_default_bathing,
        R.drawable.sm_default_beer,
        R.drawable.sm_default_bored,
        R.drawable.sm_default_boy,
        R.drawable.sm_default_camera,
        R.drawable.sm_default_chilli,
        R.drawable.sm_default_cigarette,
        R.drawable.sm_default_cinema,
        R.drawable.sm_default_coffee,
        R.drawable.sm_default_cold,
        R.drawable.sm_default_confused,
        R.drawable.sm_default_console,
        R.drawable.sm_default_cross,
        R.drawable.sm_default_crying,
        R.drawable.sm_default_devil,
        R.drawable.sm_default_disappointed,
        R.drawable.sm_default_dont_know,
        R.drawable.sm_default_drool,
        R.drawable.sm_default_embarrassed,
        R.drawable.sm_default_excited,
        R.drawable.sm_default_excruciating,
        R.drawable.sm_default_eyeroll,
        R.drawable.sm_default_girl,
        R.drawable.sm_default_grumpy,
        R.drawable.sm_default_happy,
        R.drawable.sm_default_hot,
        R.drawable.sm_default_hug_left,
        R.drawable.sm_default_hug_right,
        R.drawable.sm_default_hungry,
        R.drawable.sm_default_in_love,
        R.drawable.sm_default_internet,
        R.drawable.sm_default_invincible,
        R.drawable.sm_default_kiss,
        R.drawable.sm_default_lamp,
        R.drawable.sm_default_lying,
        R.drawable.sm_default_meeting,
        R.drawable.sm_default_mobile,
        R.drawable.sm_default_mrgreen,
        R.drawable.sm_default_musical_note,
        R.drawable.sm_default_music,
        R.drawable.sm_default_nerdy,
        R.drawable.sm_default_neutral,
        R.drawable.sm_default_party,
        R.drawable.sm_default_phone,
        R.drawable.sm_default_pirate,
        R.drawable.sm_default_pissed_off,
        R.drawable.sm_default_plate,
        R.drawable.sm_default_question,
        R.drawable.sm_default_restroom,
        R.drawable.sm_default_rose,
        R.drawable.sm_default_sad,
        R.drawable.sm_default_search,
        R.drawable.sm_default_shame,
        R.drawable.sm_default_shocked,
        R.drawable.sm_default_shopping,
        R.drawable.sm_default_shut_mouth,
        R.drawable.sm_default_sick,
        R.drawable.sm_default_silent,
        R.drawable.sm_default_sleeping,
        R.drawable.sm_default_sleepy,
        R.drawable.sm_default_star,
        R.drawable.sm_default_stressed,
        R.drawable.sm_default_studying,
        R.drawable.sm_default_suit,
        R.drawable.sm_default_surfing,
        R.drawable.sm_default_thinking,
        R.drawable.sm_default_thunder,
        R.drawable.sm_default_tongue,
        R.drawable.sm_default_tv,
        R.drawable.sm_default_typing,
        R.drawable.sm_default_uhm_yeah,
        R.drawable.sm_default_wink,
        R.drawable.sm_default_working,
        R.drawable.sm_default_writing
    };

    private MessageUtils() {}

    public static Drawable getSmiley(Context context, int itemId) {
        Drawable d = context.getResources().getDrawable(SMILEY_DEFAULT[itemId]);
        d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        return d;
    }

    public static int getSmileyByName(String name) {
        try {
            if (name.startsWith("sm_default_"))
                return Integer.parseInt(name.substring("sm_default_".length()));
        }
        catch (Exception e) {
            // ignored
        }
        return -1;
    }

    /*
    private static final String TAG_IMG_START = "<img src=\"";
    private static final String TAG_IMG_END = "\">";
    private static final int TAG_IMG_START_LEN = TAG_IMG_START.length();
    private static final int TAG_IMG_END_LEN = TAG_IMG_END.length();
    */

    // TODO error handling
    public static String cleanTextMessage(Context context, Editable edit) {
        //StringBuilder message = new StringBuilder();
        String text = Html.toHtml(edit);
        text = text.replace("<p>", "").replace("</p>", "");

        // TODO inline image data
        /*
        int sPos = text.indexOf(TAG_IMG_START);
        int ePos = -TAG_IMG_END_LEN;
        while (sPos >= 0) {
            // append the text until now
            message.append(text.substring(ePos+TAG_IMG_END_LEN, sPos));

            ePos = text.indexOf(TAG_IMG_END, sPos);
            if (ePos >= 0) {
                String sName = text.substring(sPos + TAG_IMG_START_LEN, ePos);
                int sIndex = getSmileyByName(sName);
                if (sIndex >= 0) {
                    try {
                        final StringBuilder output = new StringBuilder();
                        InputStream in = context.getResources().openRawResource(SMILEY_DEFAULT[sIndex]);
                        Base64InputStream b64in = new Base64InputStream(in, Base64.NO_WRAP);
                        InputStreamReader bin = new InputStreamReader(b64in);
                        char[] buf = new char[512];
                        int read = bin.read(buf);
                        while (read > 0) {
                            output.append(buf, 0, read);
                            read = bin.read(buf);
                        }
                        bin.close();

                        // append image data

                    }
                    catch (IOException e) {
                        Log.e("MessageUtils", "error decoding smiley", e);
                    }
                }
            }

            sPos = text.indexOf(TAG_IMG_START);
        }
        */

        return text;
    }

    private static final class ImageAdapter extends BaseAdapter {
        private Context mContext;
        private int[] mThumbIds;

        public ImageAdapter(Context c, int[] imageList) {
            mContext = c;
            mThumbIds = imageList;
        }

        public int getCount() {
            return mThumbIds.length;
        }

        public Object getItem(int position) {
            return mThumbIds[position];
        }

        public long getItemId(int position) {
            return position;
        }

        public boolean hasStableIds() {
            return true;
        }

        // create a new ImageView for each item referenced by the Adapter
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView imageView;
            if (convertView == null) {  // if it's not recycled, initialize some attributes
                imageView = new ImageView(mContext);
                int size = getDensityPixel(mContext, 24);
                imageView.setLayoutParams(new GridView.LayoutParams(size, size));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                //imageView.setPadding(4, 4, 4, 4);
            } else {
                imageView = (ImageView) convertView;
            }

            imageView.setImageResource(mThumbIds[position]);
            return imageView;
        }
    }

    public static Dialog smileysDialog(Context context, int[] icons, AdapterView.OnItemClickListener listener) {
        ImageAdapter adapter = new ImageAdapter(context, icons);

        LayoutInflater inflater = LayoutInflater.from(context);
        GridView grid = (GridView) inflater.inflate(R.layout.grid_smileys, null);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(listener);

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Select smiley");
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
