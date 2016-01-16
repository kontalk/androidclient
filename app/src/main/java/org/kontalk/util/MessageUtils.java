/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.util.StringUtils;

import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.client.BitsOfBinary;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.OutOfBandData;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.DecryptException;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.message.AttachmentComponent;
import org.kontalk.message.AudioComponent;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.ImageComponent;
import org.kontalk.message.MessageComponent;
import org.kontalk.message.RawComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.message.VCardComponent;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.UsersProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_INVALID_TIMESTAMP;


public final class MessageUtils {
    // TODO convert these to XML styles
    public static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);
    private static final ForegroundColorSpan STYLE_RED = new ForegroundColorSpan(Color.RED);
    private static final ForegroundColorSpan STYLE_GREEN = new ForegroundColorSpan(Color.rgb(0, 0xAA, 0));

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

    public static String bytesToHex(byte[] data) {
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

    /** TODO move somewhere else */
    public static String sha1(String text) {
        try {
            MessageDigest md;
            md = MessageDigest.getInstance("SHA-1");
            md.update(text.getBytes(), 0, text.length());
            byte[] sha1hash = md.digest();

            return bytesToHex(sha1hash);
        }
        catch (NoSuchAlgorithmException e) {
            // no SHA-1?? WWWHHHHAAAAAATTTT???!?!?!?!?!
            throw new RuntimeException("no SHA-1 available. What the crap of a device do you have?");
        }
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
        AttachmentComponent attachment = (AttachmentComponent) msg
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

    public static void showMessageDetails(Context context, CompositeMessage msg, String decodedPeer) {
        CharSequence messageDetails = MessageUtils.getMessageDetails(
            context, msg, decodedPeer);
        new AlertDialogWrapper.Builder(context)
            .setTitle(R.string.title_message_details)
            .setMessage(messageDetails)
            .setCancelable(true).show();
    }

    private static CharSequence getMessageDetails(Context context, CompositeMessage msg, String decodedPeer) {
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
            else if (attachment instanceof AudioComponent)
                resId = R.string.audio_message;
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

    /** Decrypts a message, modifying the object <b>in place</b>. */
    public static void decryptMessage(Context context, EndpointServer server, CompositeMessage msg) throws Exception {
        // encrypted messages have a single encrypted raw component
        RawComponent raw = (RawComponent) msg
                .getComponent(RawComponent.class);

        if (raw != null)
            decryptMessage(context, server, msg, raw.getContent());
    }

    /** Decrypts a message, modifying the object <b>in place</b>. */
    public static void decryptMessage(Context context, EndpointServer server, CompositeMessage msg, byte[] encryptedData)
            throws Exception {

        // message stanza
        Message m = null;

        try {
            PersonalKey key = Kontalk.get(context).getPersonalKey();

            if (server == null)
                server = Preferences.getEndpointServer(context);

            Coder coder = UsersProvider.getDecryptCoder(context, server, key, msg.getSender(true));

            // decrypt
            Coder.DecryptOutput result = coder.decryptText(encryptedData, true);

            String contentText;

            if (XMPPUtils.XML_XMPP_TYPE.equalsIgnoreCase(result.mime)) {
                m = XMPPUtils.parseMessageStanza(result.cleartext);

                if (result.timestamp != null && !checkDriftedDelay(m, result.timestamp))
                    result.errors.add(new DecryptException(DECRYPT_EXCEPTION_INVALID_TIMESTAMP,
                        "Drifted timestamp"));

                contentText = m.getBody();
            }
            else {
                contentText = result.cleartext;
            }

            // clear componenets (we are adding new ones)
            msg.clearComponents();
            // decrypted text
            if (contentText != null)
                msg.addComponent(new TextComponent(contentText));

            if (result.errors.size() > 0) {

                int securityFlags = msg.getSecurityFlags();

                for (DecryptException err : result.errors) {

                    int code = err.getCode();
                    switch (code) {

                        case DecryptException.DECRYPT_EXCEPTION_INTEGRITY_CHECK:
                            securityFlags |= Coder.SECURITY_ERROR_INTEGRITY_CHECK;
                            break;

                        case DecryptException.DECRYPT_EXCEPTION_VERIFICATION_FAILED:
                            securityFlags |= Coder.SECURITY_ERROR_INVALID_SIGNATURE;
                            break;

                        case DecryptException.DECRYPT_EXCEPTION_INVALID_DATA:
                            securityFlags |= Coder.SECURITY_ERROR_INVALID_DATA;
                            break;

                        case DecryptException.DECRYPT_EXCEPTION_INVALID_SENDER:
                            securityFlags |= Coder.SECURITY_ERROR_INVALID_SENDER;
                            break;

                        case DecryptException.DECRYPT_EXCEPTION_INVALID_RECIPIENT:
                            securityFlags |= Coder.SECURITY_ERROR_INVALID_RECIPIENT;
                            break;

                        case DecryptException.DECRYPT_EXCEPTION_INVALID_TIMESTAMP:
                            securityFlags |= Coder.SECURITY_ERROR_INVALID_TIMESTAMP;
                            break;

                    }

                }

                msg.setSecurityFlags(securityFlags);
            }

            msg.setEncrypted(false);

        }
        catch (Exception exc) {
            // pass over the message even if encrypted
            // UI will warn the user about that and wait
            // for user decisions
            int securityFlags = msg.getSecurityFlags();

            if (exc instanceof DecryptException) {

                int code = ((DecryptException) exc).getCode();
                switch (code) {

                    case DecryptException.DECRYPT_EXCEPTION_DECRYPT_FAILED:
                    case DecryptException.DECRYPT_EXCEPTION_PRIVATE_KEY_NOT_FOUND:
                        securityFlags |= Coder.SECURITY_ERROR_DECRYPT_FAILED;
                        break;

                    case DecryptException.DECRYPT_EXCEPTION_INTEGRITY_CHECK:
                        securityFlags |= Coder.SECURITY_ERROR_INTEGRITY_CHECK;
                        break;

                    case DecryptException.DECRYPT_EXCEPTION_INVALID_DATA:
                        securityFlags |= Coder.SECURITY_ERROR_INVALID_DATA;
                        break;

                }

                msg.setSecurityFlags(securityFlags);
            }

            throw exc;
        }

        // we have a decrypted message stanza, process it
        if (m != null) {

            // TODO duplicated code (MessageListener#processPacket)

            // out of band data
            ExtensionElement _media = m.getExtension(OutOfBandData.ELEMENT_NAME, OutOfBandData.NAMESPACE);
            if (_media != null && _media instanceof OutOfBandData) {
                File previewFile = null;

                OutOfBandData media = (OutOfBandData) _media;
                String mime = media.getMime();
                String fetchUrl = media.getUrl();
                long length = media.getLength();
                boolean encrypted = media.isEncrypted();

                // bits-of-binary for preview
                ExtensionElement _preview = m.getExtension(BitsOfBinary.ELEMENT_NAME, BitsOfBinary.NAMESPACE);
                if (_preview != null && _preview instanceof BitsOfBinary) {
                    BitsOfBinary preview = (BitsOfBinary) _preview;
                    String previewMime = preview.getType();
                    if (previewMime == null)
                        previewMime = MediaStorage.THUMBNAIL_MIME_NETWORK;

                    String filename = null;

                    if (ImageComponent.supportsMimeType(mime)) {
                        filename = ImageComponent.buildMediaFilename(msg.getId(), previewMime);
                    }

                    else if (VCardComponent.supportsMimeType(mime)) {
                        filename = VCardComponent.buildMediaFilename(msg.getId(), previewMime);
                    }

                    try {
                        if (filename != null) previewFile =
                            MediaStorage.writeInternalMedia(context,
                                filename, preview.getContents());
                    }
                    catch (IOException e) {
                        Log.w(Kontalk.TAG, "error storing thumbnail", e);
                    }
                }

                MessageComponent<?> attachment = null;

                if (ImageComponent.supportsMimeType(mime)) {
                    // cleartext only for now
                    attachment = new ImageComponent(mime, previewFile, null, fetchUrl, length,
                        encrypted, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
                }

                else if (VCardComponent.supportsMimeType(mime)) {
                    // cleartext only for now
                    attachment = new VCardComponent(previewFile, null, fetchUrl, length,
                        encrypted, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
                }

                else if (AudioComponent.supportsMimeType(mime)) {
                    attachment = new AudioComponent(mime, null, fetchUrl, length,
                        encrypted, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
                }


                // TODO other types

                if (attachment != null)
                    msg.addComponent(attachment);

                // add a dummy body if none was found
                /*
                if (body == null) {
                    msg.addComponent(new TextComponent(CompositeMessage
                        .getSampleTextContent((Class<? extends MessageComponent<?>>)
                            attachment.getClass(), mime)));
                }
                */

            }

        }
    }

    private static boolean checkDriftedDelay(Message m, Date expected) {
        Date stamp = XMPPUtils.getStanzaDelay(m);
        if (stamp != null) {
            long time = stamp.getTime();
            long now = expected.getTime();
            long diff = Math.abs(now - time);
            return (diff < Coder.TIMEDIFF_THRESHOLD);
        }

        // no timestamp found
        return true;
    }

    /** Fills in a {@link ContentValues} object from the given message. */
    public static void fillContentValues(ContentValues values, CompositeMessage msg) {
        byte[] content = null;
        String mime = null;
        boolean checkAttachment;

        // message still encrypted - use whole body of raw component
        if (msg.isEncrypted()) {

            RawComponent raw = (RawComponent) msg.getComponent(RawComponent.class);
            // if raw it's null it's a bug
            content = raw.getContent();
            mime = null;
            checkAttachment = false;

        }

        else {

            TextComponent txt = (TextComponent) msg.getComponent(TextComponent.class);

            if (txt != null) {
                content = txt.getContent().getBytes();
                mime = TextComponent.MIME_TYPE;
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
            };

            for (Class<AttachmentComponent> klass : tryComponents) {
                AttachmentComponent att = (AttachmentComponent) msg.getComponent(klass);
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

    public static Bitmap drawableToBitmap(Drawable drawable) {
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

}
