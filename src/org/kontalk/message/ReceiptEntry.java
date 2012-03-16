package org.kontalk.message;

import java.util.ArrayList;
import java.util.Collection;

import org.kontalk.client.Protocol;


/**
 * Special class used by {@link ReceiptEntryList}.
 * @author Daniele Ricci
 */
public final class ReceiptEntry {
    public final int status;
    public final String messageId;
    public String timestamp;

    public ReceiptEntry(int status, String messageId) {
        this.status = status;
        this.messageId = messageId;
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
