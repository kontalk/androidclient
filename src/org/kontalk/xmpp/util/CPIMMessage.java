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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.util.Date;

import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.xmpp.message.TextComponent;

import android.text.TextUtils;


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
        this(from, to, date, TextComponent.MIME_TYPE, body);
    }

    public CPIMMessage(String from, String to, Date date, String mime, String body) {
        mFrom = from;
        mTo = to;
        mDate = date;
        mMime = mime;
        mBody = body;
    }

    public String getFrom() {
		return mFrom;
	}

    public String getTo() {
		return mTo;
	}

    public Date getDate() {
		return mDate;
	}

    public String getMime() {
		return mMime;
	}

    public String getBody() {
		return mBody;
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

    /** A very bad CPIM parser. */
    public static CPIMMessage parse(String data) throws ParseException {
    	CPIMParser p = new CPIMParser(data);

    	String from = null,
    		to = null,
    		date = null,
    		type = null,
    		contents;

    	// first pass: CPIM content type
    	CPIMParser.CPIMHeader h;
    	boolean typeOk = false;
    	while ((h = p.nextHeader()) != null || !typeOk) {
    		if ("Content-type".equalsIgnoreCase(h.name) && TYPE.equalsIgnoreCase(h.value))
    			typeOk = true;
    	}

    	if (!typeOk)
    		throw new ParseException("Invalid content type", 0);

    	// second pass: message headers
    	while ((h = p.nextHeader()) != null) {
    		if ("From".equalsIgnoreCase(h.name)) {
    			from = h.value;
    		}

    		else if ("To".equalsIgnoreCase(h.name)) {
    			to = h.value;

    			int pos = to.indexOf(';');
    			if (pos >= 0)
    				to = to.substring(0, pos).trim();
    		}

    		else if ("DateTime".equalsIgnoreCase(h.name)) {
    			date = h.value;
    		}
    	}

    	// third pass: message content type
    	while ((h = p.nextHeader()) != null) {
    		if ("Content-type".equalsIgnoreCase(h.name)) {
    			type = h.value;

    			int pos = type.indexOf(';');
    			if (pos >= 0)
    				type = type.substring(0, pos).trim();
    		}
    	}

    	// fourth pass: message content
    	contents = p.getData();

    	Date parsedDate = StringUtils.parseDate(date);
    	return new CPIMMessage(from, to, parsedDate, type, contents);
    }

    private static class CPIMParser {

    	public static final class CPIMHeader {
    		public final String name;
    		public final String value;

    		public CPIMHeader(String name, String value) {
    			this.name = name;
    			this.value = value;
    		}

    		public String toString() {
    			return this.name + "=" + this.value;
    		}
    	}

    	private BufferedReader mData;

    	public CPIMParser(String data) {
    		internalSetData(data);
    	}

    	public void internalSetData(String data) {
    		mData = new BufferedReader(new StringReader(data));
    	}

    	public CPIMHeader nextHeader() {
    		try {
	    		String line = mData.readLine();

	    		if (!TextUtils.isEmpty(line)) {
	    			int sep = line.indexOf(':');
	    			if (sep >= 0) {
	    				String name = line.substring(0, sep).trim();
	    				String value = line.substring(sep + 1).trim();
	    				return new CPIMHeader(name, value);
	    			}
	    		}

    		}
    		catch (IOException e) {
    			// not going to happen.
    		}

    		return null;
    	}

    	public String getData() {
    		// read up all data
    		StringBuilder buf = new StringBuilder();
    		int c;
    		try {
	    		while ((c = mData.read()) >= 0)
	    			buf.append((char) c);

	    		// reader is no more needed
	    		mData.close();
    		}
    		catch (IOException e) {
    			// not going to happen.
    		}

    		return buf.toString();
    	}
    }

}
