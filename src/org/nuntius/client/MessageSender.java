package org.nuntius.client;

import java.io.IOException;
import java.io.StringWriter;

import org.nuntius.service.RequestJob;
import org.xmlpull.v1.XmlSerializer;

import android.net.Uri;
import android.util.Log;
import android.util.Xml;


/**
 * A {@link RequestJob} for sending plain text messages.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageSender extends RequestJob {

    private final String mPeer;
    private final String mText;
    private final Uri mUri;

    public MessageSender(String userId, String text, Uri uri) {
        super("message", null, null);

        mPeer = userId;
        mText = text;
        mUri = uri;

        try {
            // build xml in a proper manner
            XmlSerializer xml = Xml.newSerializer();
            StringWriter xmlString = new StringWriter();

            xml.setOutput(xmlString);
            xml.startDocument("UTF-8", Boolean.TRUE);
            xml
                .startTag(null, "body")
                .startTag(null, "m")
                .startTag(null, "t")
                .text(mPeer)
                .endTag(null, "t")
                .startTag(null, "c")
                .attribute(null, "t", "text/plain")
                .cdsect(mText);
            xml
                .endTag(null, "c")
                .endTag(null, "m")
                .endTag(null, "body")
                .endDocument();

            mContent = xmlString.toString();
            xmlString.close();
        }
        catch (IOException e) {
            // this should be impossible, since the only IOException that
            // could be thrown would be because of a OutOfMemoryError,
            // so no way...
            Log.e("XMLWriter", "error in XML message", e);
        }
    }

    public Uri getUri() {
        return mUri;
    }

    public String getUserId() {
        return mPeer;
    }
}
