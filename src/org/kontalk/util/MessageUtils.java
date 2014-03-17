/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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
import java.util.Locale;

import org.kontalk.R;
import org.kontalk.crypto.Coder;
import org.kontalk.message.AttachmentComponent;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.ImageComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.message.VCardComponent;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.ui.QuickAction;

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
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;


public final class MessageUtils {
	// TODO convert these to XML styles
    private static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);
    private static final ForegroundColorSpan STYLE_RED = new ForegroundColorSpan(Color.RED);
    private static final ForegroundColorSpan STYLE_GREEN = new ForegroundColorSpan(Color.rgb(0, 0xAA, 0));

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

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

    public static QuickAction smileysPopup(Context context, AdapterView.OnItemClickListener listener) {
        QuickAction act = new QuickAction(context, R.layout.popup_smileys);

        ImageAdapter adapter = new ImageAdapter(context, Emoji.emojiGroups);
        act.setGridAdapter(adapter, listener);
        return act;
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

    public static String sha1(String text) {
    	try {
	        MessageDigest md;
	        md = MessageDigest.getInstance("SHA-1");
	        byte[] sha1hash = new byte[40];
	        md.update(text.getBytes(), 0, text.length());
	        sha1hash = md.digest();

	        return convertToHex(sha1hash);
    	}
    	catch (NoSuchAlgorithmException e) {
    		// no SHA-1?? WWWHHHHAAAAAATTTT???!?!?!?!?!
    		throw new RuntimeException("no SHA-1 available. What the crap of a device do you have?");
    	}
    }

    public static CharSequence getFileInfoMessage(Context context, CompositeMessage msg, String decodedPeer) {
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

        int resId = R.string.text_message;
        AttachmentComponent attachment = (AttachmentComponent) msg
        		.getComponent(AttachmentComponent.class);

        if (attachment != null) {
        	if (attachment instanceof ImageComponent)
        		resId = R.string.image_message;
        	else if (attachment instanceof VCardComponent)
        		resId = R.string.vcard_message;
        }

        details.append(res.getString(resId));

        // Message length
        details.append('\n');
        details.append(res.getString(R.string.size_label));

        long length = -1;
        if (attachment != null) {
            // attachment length
        	length = attachment.getLength();
        }
        else {
        	// text content length (if found)
        	TextComponent txt = (TextComponent) msg
        			.getComponent(TextComponent.class);

        	if (txt != null)
        		length = txt.getLength();
        }
        // otherwise unknown length

        details.append(length >= 0 ?
            humanReadableByteCount(length, false) :
                res.getString(R.string.size_unknown));

        return details.toString();
    }

    public static CharSequence getMessageDetails(Context context, CompositeMessage msg, String decodedPeer) {
        SpannableStringBuilder details = new SpannableStringBuilder();
        Resources res = context.getResources();
        int direction = msg.getDirection();

        // Message type
        details.append(res.getString(R.string.message_type_label));

        int resId = R.string.text_message;
        AttachmentComponent attachment = (AttachmentComponent) msg
        		.getComponent(AttachmentComponent.class);

        if (attachment != null) {
        	if (attachment instanceof ImageComponent)
        		resId = R.string.image_message;
        	else if (attachment instanceof VCardComponent)
        		resId = R.string.vcard_message;
        }

        details.append(res.getString(resId));

        // To/From
        details.append('\n');
        if (direction == Messages.DIRECTION_OUT)
            details.append(res.getString(R.string.to_address_label));
        else
            details.append(res.getString(R.string.from_label));

        details.append(decodedPeer);

        // Encrypted
        int securityFlags = msg.getSecurityFlags();
        details.append('\n');
        details.append(res.getString(R.string.encrypted_label));
        if (securityFlags != Coder.SECURITY_CLEARTEXT) {
            details.append(res.getString(R.string.yes));

            // Security flags (verification status)
        	details.append('\n');
        	details.append(res.getString(R.string.security_label));

        	boolean securityError = Coder.isError(securityFlags);
        	// save start position for spans
    		int startPos = details.length();

        	if (securityError) {
        		details.append(res.getString(R.string.security_status_bad));

        		int stringId = 0;

            	if ((securityFlags & Coder.SECURITY_ERROR_INVALID_SIGNATURE) != 0) {
            		stringId = R.string.security_error_invalid_signature;
            	}

            	else if ((securityFlags & Coder.SECURITY_ERROR_INVALID_SENDER) != 0) {
            		stringId = R.string.security_error_invalid_sender;
            	}

            	else if ((securityFlags & Coder.SECURITY_ERROR_INVALID_RECIPIENT) != 0) {
            		stringId = R.string.security_error_invalid_recipient;
            	}

            	else if ((securityFlags & Coder.SECURITY_ERROR_INVALID_TIMESTAMP) != 0) {
            		stringId = R.string.security_error_invalid_timestamp;
            	}

            	else if ((securityFlags & Coder.SECURITY_ERROR_INVALID_DATA) != 0) {
            		stringId = R.string.security_error_invalid_data;
            	}

            	else if ((securityFlags & Coder.SECURITY_ERROR_DECRYPT_FAILED) != 0) {
            		stringId = R.string.security_error_decrypt_failed;
            	}

            	else if ((securityFlags & Coder.SECURITY_ERROR_INTEGRITY_CHECK) != 0) {
            		stringId = R.string.security_error_integrity_check;
            	}

            	else if ((securityFlags & Coder.SECURITY_ERROR_PUBLIC_KEY_UNAVAILABLE) != 0) {
            		stringId = R.string.security_error_public_key_unavail;
            	}

            	if (stringId > 0)
            		details.append(res.getString(stringId));
        	}

        	else {
        		details.append(res.getString(R.string.security_status_good));
        	}

            details.setSpan(STYLE_BOLD, startPos, details.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            details.setSpan(securityError ? STYLE_RED : STYLE_GREEN, startPos, details.length(),
            		Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

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

        long length = -1;
        if (attachment != null) {
            // attachment length
        	length = attachment.getLength();
        }
        else {
        	// text content length (if found)
        	TextComponent txt = (TextComponent) msg
        			.getComponent(TextComponent.class);

        	if (txt != null)
        		length = txt.getLength();
        }
        // otherwise unknown length

        details.append(length >= 0 ?
            humanReadableByteCount(length, false) :
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
                    res.getString(R.string.sent_label), msg.getServerTimestamp(), true);
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
                case Messages.STATUS_SENDING:
                    resId = R.string.sending_label;
                    timestamp = msg.getTimestamp();
                    break;
                case Messages.STATUS_SENT:
                case Messages.STATUS_RECEIVED:
                case Messages.STATUS_NOTDELIVERED:
                    resId = R.string.sent_label;
                    long serverTime = msg.getServerTimestamp();
                    timestamp = serverTime > 0 ? serverTime : msg.getTimestamp();
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
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    /** Converts a Kontalk user id to a JID. */
    public static String toJID(String userId, String network) {
        StringBuilder jid = new StringBuilder();

        // this is for avoiding a useless call to subSequence
        int l = userId.length();
        if (l > CompositeMessage.USERID_LENGTH)
            jid.append(userId.subSequence(0, CompositeMessage.USERID_LENGTH));
        else
            jid.append(userId);

        jid.append('@');
        jid.append(network);

        if (l > CompositeMessage.USERID_LENGTH)
            jid.append(userId.subSequence(CompositeMessage.USERID_LENGTH, l));

        return jid.toString();
    }

    public static boolean compareUserId(String a, String b, boolean full) throws IllegalArgumentException {
        int aLen = a.length();
        int bLen = b.length();
        // validate :)
        if ((aLen != CompositeMessage.USERID_LENGTH && aLen != CompositeMessage.USERID_LENGTH_RESOURCE) ||
                (bLen != CompositeMessage.USERID_LENGTH && bLen != CompositeMessage.USERID_LENGTH_RESOURCE) ||
                a.contains("@") || b.contains("@"))
            throw new IllegalArgumentException("either one or both parameters are not valid user id.");

        if (full)
            // full comparison - just equals
            return a.equalsIgnoreCase(b);
        else
            // user id comparison
            return a.substring(0, CompositeMessage.USERID_LENGTH)
                .equalsIgnoreCase(b.substring(0, CompositeMessage.USERID_LENGTH));
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
