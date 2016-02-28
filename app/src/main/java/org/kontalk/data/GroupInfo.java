package org.kontalk.data;


/**
 * Group chat information.
 * @author Daniele Ricci
 */
public class GroupInfo {
    private final String mJid;
    private final String mSubject;
    private final String[] mMembers;

    public GroupInfo(String jid, String subject, String[] members) {
        mJid = jid;
        mSubject = subject;
        mMembers = members;
    }

    public String getJid() {
        return mJid;
    }

    public String getSubject() {
        return mSubject;
    }

    public String[] getMembers() {
        return mMembers;
    }
}
