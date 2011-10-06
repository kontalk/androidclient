package org.kontalk.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Random;


/**
 * A convenient server list using {@link EndpointServer}.
 * @author Daniele Ricci
 */
public class ServerList extends ArrayList<EndpointServer> {
    private static final long serialVersionUID = -8798498388829449277L;

    protected final Date mDate;

    private Random mSeed;

    public ServerList(Date date) {
        super();
        mDate = date;
    }

    public ServerList(Date date, Collection<EndpointServer> list) {
        super(list);
        mDate = date;
    }

    public Date getDate() {
        return mDate;
    }

    /** Returns a random entry in the list. */
    public EndpointServer random() {
        if (mSeed == null)
            mSeed = new Random();
        return get(mSeed.nextInt(size()));
    }

}
