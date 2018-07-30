/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.StringReader;
import java.util.Date;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jxmpp.util.XmppStringUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.ColorInt;

import org.kontalk.client.EndpointServer;


/**
 * XMPP related functions.
 * @author Daniele Ricci
 */
public class XMPPUtils {

    public static final String XML_XMPP_TYPE = "application/xmpp+xml";

    private XMPPUtils() {}

    private static XmlPullParserFactory _xmlFactory;

    private static XmlPullParser getPullParser(String data) throws XmlPullParserException {
        if (_xmlFactory == null) {
            _xmlFactory = XmlPullParserFactory.newInstance();
            _xmlFactory.setNamespaceAware(true);
        }

        XmlPullParser parser = _xmlFactory.newPullParser();
        parser.setInput(new StringReader(data));

        return parser;
    }

    /** Parses a &lt;xmpp&gt;-wrapped message stanza. */
    public static Message parseMessageStanza(String data) throws Exception {

        XmlPullParser parser = getPullParser(data);
        boolean done = false, in_xmpp = false;
        Message msg = null;

        while (!done) {
            int eventType = parser.next();

            if (eventType == XmlPullParser.START_TAG) {

                if ("xmpp".equals(parser.getName()))
                    in_xmpp = true;

                else if ("message".equals(parser.getName()) && in_xmpp) {
                    msg = PacketParserUtils.parseMessage(parser);
                }
            }

            else if (eventType == XmlPullParser.END_TAG) {

                if ("xmpp".equals(parser.getName()))
                    done = true;
            }
        }

        return msg;
    }

    public static Date getStanzaDelay(Stanza packet) {
        ExtensionElement _delay = packet.getExtension("delay", "urn:xmpp:delay");
        if (_delay == null)
            _delay = packet.getExtension("x", "jabber:x:delay");

        Date stamp = null;
        if (_delay != null) {
            if (_delay instanceof DelayInformation) {
                stamp = ((DelayInformation) _delay).getStamp();
            }
        }

        return stamp;
    }

    public static XMPPError.Condition getErrorCondition(Stanza packet) {
        return packet.getError() != null ? packet.getError().getCondition() : null;
    }

    public static boolean checkError(Stanza packet, XMPPError.Condition condition) {
        return packet.getError() != null && packet.getError().getCondition() == condition;
    }

    public static boolean isLocalJID(String jid, String host) {
        return XmppStringUtils.parseDomain(jid).equalsIgnoreCase(host);
    }

    public static String createLocalJID(Context context, String name) {
        EndpointServer server = Preferences.getEndpointServer(context);
        if (server == null)
            throw new IllegalArgumentException("server is null");
        return XmppStringUtils.completeJidFrom(name, server.getNetwork());
    }

    public static boolean equalsBareJID(String full, String bare) {
        return XmppStringUtils.parseBareJid(full).equalsIgnoreCase(bare);
    }

    public static String createLocalpart(String uid) {
        return MessageUtils.sha1(uid);
    }

    /** Returns true if the given JID is a domain JID (e.g. beta.kontalk.net). */
    public static boolean isDomainJID(String jid) {
        return XmppStringUtils.parseDomain(jid)
            .equalsIgnoreCase(jid);
    }

    /**
     * Returns a color for the given JID calculated with the rules of XEP-0392
     * @see <a href="https://xmpp.org/extensions/xep-0392.html">XEP-0392</a>
     */
    @ColorInt
    public static int getJIDColor(String jid) {
        // TODO get correction from accessibility settings
        double[] rgb = ConsistentColorGeneration.getRGB(jid,
            ConsistentColorGeneration.CORRECTION_NONE);

        return Color.argb(0xff,
            (int) (rgb[0] * 255),
            (int) (rgb[1] * 255),
            (int) (rgb[2] * 255));
    }

}
