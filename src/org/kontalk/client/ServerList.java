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
    private static final long serialVersionUID = 1L;

    protected final Date mDate;

    private final Random mSeed = new Random();

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
        return (size() > 0) ?
            get(mSeed.nextInt(size())) : null;
    }

}
