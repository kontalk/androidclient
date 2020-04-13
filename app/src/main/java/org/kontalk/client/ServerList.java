/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;


/**
 * A convenient server list using {@link EndpointServer}.
 * @author Daniele Ricci
 */
public class ServerList extends ArrayList<EndpointServer> {
    private static final long serialVersionUID = 1L;
    private static DateFormat sTimestampFormat =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US);

    private final Date mDate;

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

    public Properties toProperties() {
        Properties prop = new Properties();
        Date now = new Date();
        prop.setProperty("timestamp", sTimestampFormat.format(now));

        for (int i = 0; i < size(); i++) {
            String item = get(i).toString();
            prop.setProperty("server" + (i + 1), item);
        }
        return prop;
    }

    public static ServerList fromProperties(Properties props) throws IOException {
        try {
            Date date = sTimestampFormat.parse(props.getProperty("timestamp"));
            ServerList list = new ServerList(date);
            int i = 1;
            String server;
            while ((server = props.getProperty("server" + i)) != null) {
                list.add(new EndpointServer(server));
                i++;
            }

            return list;
        }
        catch (Exception e) {
            throw new IOException("parse error", e);
        }
    }

    /** A simple server provider backed by a server list. */
    public static class ServerListProvider implements EndpointServer.EndpointServerProvider {
        private ServerList mList;
        private List<EndpointServer> mUsed;

        public ServerListProvider(ServerList list) {
            mList = new ServerList(list.getDate(), list);
            mUsed = new LinkedList<>();
        }

        @Override
        public ServerList list() {
            ServerList list = new ServerList(mList.getDate());
            list.addAll(mList);
            list.addAll(mUsed);
            return list;
        }

        @Override
        public EndpointServer next() {
            if (mList.size() > 0) {
                EndpointServer s = mList.random();
                mList.remove(s);
                mUsed.add(s);
                return s;
            }
            // list exausted
            return null;
        }

        @Override
        public void reset() {
            mList.addAll(mUsed);
            mUsed.clear();
        }
    }

}
