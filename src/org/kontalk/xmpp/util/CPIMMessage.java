/*
 * Kontalk Android client
 * Copyright (C) 2013 Kontalk Devteam <devteam@kontalk.org>

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
package org.kontalk.xmpp.util;

import java.util.Date;

import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.xmpp.message.PlainTextMessage;


/**
 * Generates a Message/CPIM body.
 * @author Daniele Ricci
 */
public class CPIMMessage {

    private static final String TYPE = "Message/CPIM";
    private static final String CHARSET = "utf-8";

    private final String mFrom;
    private final String mTo;
    private final Date mDate;
    private final String mMime;
    private final String mBody;

    private StringBuilder mBuf;

    /** Constructs a new plain text message. */
    public CPIMMessage(String from, String to, Date date, String body) {
        mFrom = from;
        mTo = to;
        mDate = date;
        mMime = PlainTextMessage.MIME_TYPE;
        mBody = body;
    }

    @SuppressWarnings("deprecation")
	public String toString() {
        if (mBuf == null) {
        	String date;
        	// we must use this because StringUtils#formatXEP0082Date doesn't seem right
        	synchronized (StringUtils.XEP_0082_UTC_FORMAT) {
        		date = StringUtils.XEP_0082_UTC_FORMAT.format(mDate);
        	}

            mBuf = new StringBuilder("Content-type: ")
                .append(TYPE)
                .append("\n\nFrom: ")
                .append(mFrom)
                .append("\nTo: ")
                .append(mTo)
                .append("\nDateTime: ")
                .append(date)
                .append("\n\nContent-type: ")
                .append(mMime)
                .append("; charset=")
                .append(CHARSET)
                .append("\n\n")
                .append(mBody);
        }

        return mBuf.toString();
    }

    public byte[] toByteArray() {
        return toString().getBytes();
    }

}
