package org.kontalk.service;


/**
 * Implement this interface to be notified of events happening in child threads.
 * Obviously a child thread must support this.
 * @author Daniele Ricci
 */
public interface ParentThread {

    /** Child thread has terminated. */
    public void childTerminated(int reason);

    /** Child is going to respawn itself. */
    public void childRespawning(int reason);
}
