package org.kontalk.xmpp.client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.xmlpull.v1.XmlPullParser;

import android.util.Base64;


/**
 * XEP-0231: Bits of Binary
 * @author Daniele Ricci
 */
public class BitsOfBinary implements PacketExtension {
    public static final String ELEMENT_NAME = "data";
    public static final String NAMESPACE = "urn:xmpp:bob";

    private final String mMime;
    private final File mFile;

    /** Cache of Base64-encoded data. */
    private String mCache;

    public BitsOfBinary(String mime, File path) {
        mMime = mime;
        mFile = path;
    }

    public BitsOfBinary(String mime, String contents) {
        this(mime, (File) null);
        mCache = contents;
    }

    @Override
    public String getElementName() {
        return ELEMENT_NAME;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    public byte[] getContents() {
        updateContents();
        if (mCache != null)
            return Base64.decode(mCache, Base64.DEFAULT);

        return null;
    }

    public String getType() {
        return mMime;
    }

    private void updateContents() {
        if (mCache == null) {
            FileInputStream source = null;
            try {
                source = new FileInputStream(mFile);
                ByteArrayOutputStream bos = new ByteArrayOutputStream((int) mFile.length());
                byte[] buffer = new byte[1024];
                int len;
                while ((len = source.read(buffer)) != -1)
                    bos.write(buffer, 0, len);
                mCache = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                bos.close();
            }
            catch (IOException e) {
                // error! Invalidate cache
                mCache = null;
            }
            finally {
                try { source.close(); } catch (Exception e) {}
            }
        }
    }

    @Override
    public String toXML() {
        updateContents();
        if (mCache == null) return null;

        return new StringBuilder("<")
            .append(ELEMENT_NAME)
            .append(" xmlns='")
            .append(NAMESPACE)
            .append("' type='")
            .append(mMime)
            .append("'>")
            .append(mCache)
            .append("</")
            .append(ELEMENT_NAME)
            .append('>')
            .toString();
    }

    public static final class Provider implements PacketExtensionProvider {

        @Override
        public PacketExtension parseExtension(XmlPullParser parser) throws Exception {
            String contents = null, mime = null;
            boolean done = false;

            while (!done)
            {
                int eventType = parser.next();

                if (eventType == XmlPullParser.START_TAG)
                {
                    if (ELEMENT_NAME.equals(parser.getName())) {
                        mime = parser.getAttributeValue(null, "type");
                    }

                }
                else if (eventType == XmlPullParser.END_TAG)
                {
                    if (ELEMENT_NAME.equals(parser.getName())) {
                        done = true;
                    }
                }
                else if (eventType == XmlPullParser.TEXT) {
                    contents = parser.getText();
                }
            }

            if (contents != null)
                return new BitsOfBinary(mime, contents);
            else
                return null;
        }

    }

}
