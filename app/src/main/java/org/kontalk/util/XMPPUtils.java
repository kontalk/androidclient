/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import java.util.Collection;
import java.util.Date;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smack.util.SHA1;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.util.XmppStringUtils;
import org.jivesoftware.smack.xml.XmlPullParser;

import android.graphics.Color;
import androidx.annotation.ColorInt;


/**
 * XMPP related functions.
 * @author Daniele Ricci
 */
public class XMPPUtils {

    public static final String XML_XMPP_TYPE = "application/xmpp+xml";

    private XMPPUtils() {}

    /** Parses a &lt;xmpp&gt;-wrapped message stanza. */
    public static Message parseMessageStanza(String data) throws Exception {

        XmlPullParser parser = XMPPParserUtils.getPullParser(data);
        boolean done = false, in_xmpp = false;
        Message msg = null;

        while (!done) {
            XmlPullParser.Event eventType = parser.next();

            if (eventType == XmlPullParser.Event.START_ELEMENT) {

                if ("xmpp".equals(parser.getName()))
                    in_xmpp = true;

                else if ("message".equals(parser.getName()) && in_xmpp) {
                    msg = PacketParserUtils.parseMessage(parser);
                }
            }

            else if (eventType == XmlPullParser.Event.END_ELEMENT) {

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

    public static StanzaError.Condition getErrorCondition(Stanza packet) {
        return packet.getError() != null ? packet.getError().getCondition() : null;
    }

    public static boolean checkError(Stanza packet, StanzaError.Condition condition) {
        return packet.getError() != null && packet.getError().getCondition() == condition;
    }

    public static boolean isLocalJID(String jid, String host) {
        return XmppStringUtils.parseDomain(jid).equalsIgnoreCase(host);
    }

    public static boolean equalsBareJID(String full, String bare) {
        return XmppStringUtils.parseBareJid(full).equalsIgnoreCase(bare);
    }

    public static String createLocalpart(String uid) {
        return SHA1.hex(uid);
    }

    /** Returns true if the given JID is a domain JID (e.g. beta.kontalk.net). */
    public static boolean isDomainJID(String jid) {
        return XmppStringUtils.parseDomain(jid)
            .equalsIgnoreCase(jid);
    }

    public static Jid[] parseJids(Collection<String> jids) {
        Jid[] list = new Jid[jids.size()];
        int i = 0;
        for (String jid : jids) {
            list[i++] = JidCreate.fromOrThrowUnchecked(jid);
        }
        return list;
    }

    public static Jid[] parseJids(String[] jids) {
        Jid[] list = new Jid[jids.length];
        int i = 0;
        for (String jid : jids) {
            list[i++] = JidCreate.fromOrThrowUnchecked(jid);
        }
        return list;
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
