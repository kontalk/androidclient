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

package org.kontalk.message;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;

import org.kontalk.client.Protocol;


/**
 * Special class used by {@link ReceiptEntryList}.
 * @author Daniele Ricci
 */
public final class ReceiptEntry {
    private static final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    static {
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public final int status;
    public final String messageId;
    public String timestamp;

    public ReceiptEntry(int status, String messageId) {
        this.status = status;
        this.messageId = messageId;
    }

    public Date getTimestamp() throws ParseException {
        return dateFormat.parse(timestamp);
    }

    /**
     * Special class used by {@link ReceiptMessage}.
     * @author Daniele Ricci
     */
    public static class ReceiptEntryList extends ArrayList<ReceiptEntry> {
        private static final long serialVersionUID = 1L;

        public ReceiptEntryList() {
            super();
        }

        public ReceiptEntryList(int capacity) {
            super(capacity);
        }

        public ReceiptEntryList(Collection<? extends ReceiptEntry> from) {
            super(from);
        }

        public void add(Protocol.ReceiptMessage.Entry entry) {
            ReceiptEntry e = new ReceiptEntry(entry.getStatus().getNumber(),
                    entry.getMessageId());
            if (entry.hasTimestamp())
                e.timestamp = entry.getTimestamp();

            super.add(e);
        }
    }
}
