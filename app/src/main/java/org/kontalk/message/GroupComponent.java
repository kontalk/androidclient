package org.kontalk.message;

import org.kontalk.data.GroupInfo;


/**
 * Virtual component for group information. Not rendered in the UI.
 * @author Daniele Ricci
 */
public class GroupComponent extends MessageComponent<GroupInfo> {

    public GroupComponent(GroupInfo content) {
        super(content, 0, false, 0);
    }

}
