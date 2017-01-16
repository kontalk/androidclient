/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import com.afollestad.materialdialogs.MaterialDialog;

import org.jivesoftware.smack.util.StringUtils;
import org.spongycastle.jcajce.provider.digest.SHA1;
import org.spongycastle.openpgp.PGPException;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.client.EndpointServer;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.message.AttachmentComponent;
import org.kontalk.message.AudioComponent;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.DefaultAttachmentComponent;
import org.kontalk.message.GroupCommandComponent;
import org.kontalk.message.GroupComponent;
import org.kontalk.message.ImageComponent;
import org.kontalk.message.LocationComponent;
import org.kontalk.message.RawComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.message.VCardComponent;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.MyMessages.Messages;


public final class MessageUtils {
    // TODO convert these to XML styles
    // these spans can't be used more than once in a Spanned because it's the same object reference!!!
    @Deprecated
    public static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);
    @Deprecated
    private static final ForegroundColorSpan STYLE_RED = new ForegroundColorSpan(Color.RED);
    @Deprecated
    private static final ForegroundColorSpan STYLE_GREEN = new ForegroundColorSpan(Color.rgb(0, 0xAA, 0));

    /** For ascii to emoji converter. */
    private static Map<String, String> sEmojiConverterMap = new HashMap<>();

    static {
        //http://apps.timwhitlock.info/emoji/tables/unicode
        //http://unicode.org/emoji/charts/full-emoji-list.html
        //use this to get UTF-16 from UTF-32: http://www.fileformat.info/info/unicode/char/search.htm
        //you have to use UTF-16 here!
        sEmojiConverterMap.put(":)", "\uD83D\uDE42");
        sEmojiConverterMap.put(":-)", "\uD83D\uDE42");
        sEmojiConverterMap.put(":(", "\uD83D\uDE41");
        sEmojiConverterMap.put(":-(", "\uD83D\uDE41");
        sEmojiConverterMap.put(":'(", "\uD83D\uDE22");
        sEmojiConverterMap.put("<3", "\u0000\u2764");
        sEmojiConverterMap.put(";-)", "\uD83D\uDE09");
        sEmojiConverterMap.put(";)", "\uD83D\uDE09");
        sEmojiConverterMap.put(":p", "\uD83D\uDE1B");
        sEmojiConverterMap.put(":P", "\uD83D\uDE1B");
        sEmojiConverterMap.put(":b", "\uD83D\uDE1B");
        sEmojiConverterMap.put(";p", "\uD83D\uDE1C");
        sEmojiConverterMap.put(";P", "\uD83D\uDE1C");
        sEmojiConverterMap.put(";b", "\uD83D\uDE1C");
        sEmojiConverterMap.put("xp", "\uD83D\uDE1D");
        sEmojiConverterMap.put("xP", "\uD83D\uDE1D");
        sEmojiConverterMap.put("xb", "\uD83D\uDE1D");
        sEmojiConverterMap.put("Xp", "\uD83D\uDE1D");
        sEmojiConverterMap.put("XP", "\uD83D\uDE1D");
        sEmojiConverterMap.put("Xb", "\uD83D\uDE1D");
    }

    public static final int MILLISECONDS_IN_DAY = 86400000;

    private MessageUtils() {}

    public static CharSequence formatRelativeTimeSpan(Context context, long when) {
        int format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT |
                DateUtils.FORMAT_ABBREV_ALL |
                DateUtils.FORMAT_CAP_AMPM;

        return DateUtils.getRelativeDateTimeString(context, when,
            DateUtils.SECOND_IN_MILLIS, DateUtils.DAY_IN_MILLIS * 2,
            format_flags);
    }

    public static String formatDateString(Context context, long when) {
        Time then = new Time();
        then.set(when);
        Time now = new Time();
        now.setToNow();

        // Basic settings for formatDateTime() we want for all cases.
        int format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT |
                DateUtils.FORMAT_SHOW_DATE |
                DateUtils.FORMAT_CAP_AMPM;

        // If the message is from a different year, show the date and year.
        if (then.year != now.year) {
            format_flags |= DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE;
        } else if (then.yearDay != now.yearDay) {
            // If it is from a different day than today, show only the date.
            format_flags |= DateUtils.FORMAT_SHOW_DATE;
        }

        return DateUtils.formatDateTime(context, when, format_flags);
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

    public static String formatTimeString(Context context, long when) {
        Time then = new Time();
        then.set(when);
        Time now = new Time();
        now.setToNow();

        // Basic settings for formatDateTime() we want for all cases.
        int format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT |
                DateUtils.FORMAT_ABBREV_ALL |
                DateUtils.FORMAT_CAP_AMPM |
                DateUtils.FORMAT_SHOW_TIME;

        return DateUtils.formatDateTime(context, when, format_flags);
    }

    public static boolean isSameDate(long a, long b) {
        TimeZone tm = TimeZone.getDefault();
        a += tm.getOffset(a);
        b += tm.getOffset(b);
        return (a / MILLISECONDS_IN_DAY) == (b / MILLISECONDS_IN_DAY);
    }

    public static long getMessageTimestamp(CompositeMessage msg) {
        long serverTime = msg.getServerTimestamp();
        return serverTime > 0 ? serverTime : msg.getTimestamp();
    }

    public static long getMessageTimestamp(Cursor c) {
        long serverTime = c.getLong(CompositeMessage.COLUMN_SERVER_TIMESTAMP);
        return serverTime > 0 ? serverTime : c.getLong(CompositeMessage.COLUMN_TIMESTAMP);
    }

    public static String getMessagePeer(CompositeMessage msg) {
        return msg.getDirection() == Messages.DIRECTION_IN ?
            msg.getSender(true) : msg.getRecipients().get(0);
    }

    public static String getMessagePeer(Cursor c) {
        return c.getString(CompositeMessage.COLUMN_PEER);
    }

    public static int getMessageDirection(Cursor c) {
        return c.getInt(CompositeMessage.COLUMN_DIRECTION);
    }

    public static String bytesToHex(byte[] data) {
        StringBuilder buf = new StringBuilder();
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

    /** TODO move somewhere else */
    public static String sha1(String text) {
        MessageDigest md = new SHA1.Digest();
        md.update(text.getBytes(), 0, text.length());
        byte[] sha1hash = md.digest();

        return bytesToHex(sha1hash);
    }

    public static ByteArrayInOutStream readFully(InputStream in, long maxSize) throws IOException {
        byte[] buf = new byte[1024];
        ByteArrayInOutStream out = new ByteArrayInOutStream();
        int l;
        while ((l = in.read(buf, 0, 1024)) > 0 && out.size() < maxSize)
            out.write(buf, 0, l);
        return out;
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
        AttachmentComponent attachment = msg
                .getComponent(AttachmentComponent.class);

        if (attachment != null) {
            if (attachment instanceof ImageComponent)
                resId = R.string.image_message;
            else if (attachment instanceof VCardComponent)
                resId = R.string.vcard_message;
            else if (attachment instanceof AudioComponent)
                resId = R.string.audio_message;
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
            TextComponent txt = msg
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

    public static void showMessageDetails(Context context, CompositeMessage msg, String decodedPeer, String decodedName) {
        CharSequence messageDetails = MessageUtils.getMessageDetails(
            context, msg, decodedPeer, decodedName);
        new MaterialDialog.Builder(context)
            .title(R.string.title_message_details)
            .content(messageDetails)
            .cancelable(true)
            .show();
    }

    private static CharSequence getMessageDetails(Context context, CompositeMessage msg, String decodedPeer, String decodedName) {
        SpannableStringBuilder details = new SpannableStringBuilder();
        Resources res = context.getResources();
        int direction = msg.getDirection();

        // Message type
        details.append(res.getString(R.string.message_type_label));

        int resId = R.string.text_message;

        if (msg.hasComponent(LocationComponent.class)) {
            resId = R.string.location_message;
        }

        AttachmentComponent attachment = msg
                .getComponent(AttachmentComponent.class);

        if (attachment != null) {
            if (attachment instanceof ImageComponent)
                resId = R.string.image_message;
            else if (attachment instanceof VCardComponent)
                resId = R.string.vcard_message;
            else if (attachment instanceof AudioComponent)
                resId = R.string.audio_message;
        }

        details.append(res.getString(resId));

        // To/From
        if (!msg.hasComponent(GroupComponent.class) || direction == Messages.DIRECTION_IN) {
            details.append('\n');
            if (direction == Messages.DIRECTION_OUT)
                details.append(res.getString(R.string.to_address_label));
            else
                details.append(res.getString(R.string.from_label));

            String displayName = (decodedName != null) ?
                decodedName + "\n<" + decodedPeer + ">" :
                decodedPeer;
            details.append(displayName);
        }

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
            TextComponent txt = msg
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
     * http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
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

    public static String messageId() {
        return StringUtils.randomString(30);
    }

    public static File encryptFile(Context context, InputStream in, String[] users)
            throws GeneralSecurityException, IOException, PGPException {
        PersonalKey key = Kontalk.get(context).getPersonalKey();
        EndpointServer server = Preferences.getEndpointServer(context);
        Coder coder = Keyring.getEncryptCoder(context, server, key, users);
        // create a temporary file to store encrypted data
        File temp = File.createTempFile("media", null, context.getCacheDir());
        FileOutputStream out = new FileOutputStream(temp);
        coder.encryptFile(in, out);
        // close encrypted file
        out.close();
        return temp;
    }

    /** Fills in a {@link ContentValues} object from the given message. */
    public static void fillContentValues(ContentValues values, CompositeMessage msg) {
        byte[] content = null;
        String mime = null;
        boolean checkAttachment;

        // message still encrypted - use whole body of raw component
        if (msg.isEncrypted()) {

            RawComponent raw = msg.getComponent(RawComponent.class);
            // if raw it's null it's a bug
            content = raw.getContent();
            mime = null;
            checkAttachment = false;

        }

        else {
            GroupCommandComponent group = msg.getComponent(GroupCommandComponent.class);
            if (group != null) {
                content = group.getTextContent().getBytes();
                mime = GroupCommandComponent.MIME_TYPE;
            }
            else {
                TextComponent txt = msg.getComponent(TextComponent.class);

                if (txt != null) {
                    content = txt.getContent().getBytes();
                    mime = TextComponent.MIME_TYPE;
                }
            }

            checkAttachment = true;
        }

        // selective components detection

        if (checkAttachment) {
            @SuppressWarnings("unchecked")
            Class<AttachmentComponent>[] tryComponents = new Class[] {
                ImageComponent.class,
                VCardComponent.class,
                AudioComponent.class,
                DefaultAttachmentComponent.class,
            };

            for (Class<AttachmentComponent> klass : tryComponents) {
                AttachmentComponent att = msg.getComponent(klass);
                if (att != null) {

                    values.put(Messages.ATTACHMENT_MIME, att.getMime());
                    values.put(Messages.ATTACHMENT_FETCH_URL, att.getFetchUrl());
                    values.put(Messages.ATTACHMENT_LENGTH, att.getLength());
                    values.put(Messages.ATTACHMENT_ENCRYPTED, att.isEncrypted());
                    values.put(Messages.ATTACHMENT_SECURITY_FLAGS, att.getSecurityFlags());

                    File previewFile = att.getPreviewFile();
                    if (previewFile != null)
                        values.put(Messages.ATTACHMENT_PREVIEW_PATH, previewFile.getAbsolutePath());

                    // only one attachment is supported
                    break;
                }
            }
        }

        values.put(Messages.BODY_CONTENT, content);
        values.put(Messages.BODY_LENGTH, content != null ? content.length : 0);
        values.put(Messages.BODY_MIME, mime);

        values.put(Messages.ENCRYPTED, msg.isEncrypted());
        values.put(Messages.SECURITY_FLAGS, msg.getSecurityFlags());

        values.put(Messages.SERVER_TIMESTAMP, msg.getServerTimestamp());
    }

    @NonNull
    public static Bitmap drawableToBitmap(@NonNull Drawable drawable) {
        Bitmap bitmap;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if(bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static String toString(byte[] text) {
        return new String(trimNul(text));
    }

    public static byte[] trimNul(byte[] text) {
        if (text.length > 0 && text[text.length - 1] == '\0') {
            byte[] nulBody = new byte[text.length - 1];
            System.arraycopy(text, 0, nulBody, 0, nulBody.length);
            text = nulBody;
        }
        return text;
    }

    public static boolean convertSmileys(Editable input) {
        for (String key : sEmojiConverterMap.keySet()) {
            if (replaceEditable(input, key, sEmojiConverterMap.get(key))){
                return true;
            }
        }
        return false;
    }

    private static boolean replaceEditable(Editable text, String in, String out) {
        int position = text.toString().indexOf(in);
        if (position >= 0) {
            text.replace(position, position + in.length(), out);
            return true;
        }
        return false;
    }

    public static boolean sendEncrypted(Context context, boolean chatEncryptionEnabled) {
        return Preferences.getEncryptionEnabled(context) && chatEncryptionEnabled;
    }

}
