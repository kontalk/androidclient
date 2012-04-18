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
