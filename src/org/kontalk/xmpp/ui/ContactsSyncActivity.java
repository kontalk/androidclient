package org.kontalk.xmpp.ui;


/** Interface for activities embedding a {@link ContactsListFragment}. */
public interface ContactsSyncActivity {

    /** Alerts the user in some way that a contacts sync is under way. */
    public void setSyncing(boolean syncing);
}
