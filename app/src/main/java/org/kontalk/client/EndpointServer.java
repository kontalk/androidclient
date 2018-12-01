/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

import java.util.regex.Pattern;


/**
 * Defines a server address.
 *
 * @author Daniele Ricci
 */
public class EndpointServer {
    /**
     * Default client port.
     */
    public static final int DEFAULT_PORT = 5222;

    /**
     * Validation pattern. Very basic.
     */
    // TODO use this also for parsing
    private static final Pattern sPattern = Pattern.compile("^[A-Za-z0-9\\-\\.]+(\\|[A-Za-z0-9\\-\\.]+(:\\d+)?)?$");

    private String mHost;
    private int mPort;
    private String mNetwork;

    public EndpointServer(String url) {
        this(url, null, DEFAULT_PORT);
        if (url.contains("|")) {
            String[] parsed = url.split("\\|");
            mNetwork = parsed[0];
            mHost = parsed[1];
        }
        if (mHost != null && mHost.contains(":")) {
            String[] parsed = mHost.split(":");
            mHost = parsed[0];
            mPort = Integer.parseInt(parsed[1]);
        }
    }

    public EndpointServer(String network, String host, int port) {
        mNetwork = network;
        mHost = host;
        mPort = port;
    }

    @Override
    public boolean equals(Object o) {
        return o != null && o instanceof EndpointServer &&
            (mHost == ((EndpointServer) o).mHost ||
                (mHost != null && mHost.equalsIgnoreCase(((EndpointServer) o).mHost))) &&
            (((EndpointServer) o).mNetwork == mNetwork ||
                ((EndpointServer) o).mNetwork.equalsIgnoreCase(mNetwork)) &&
            ((EndpointServer) o).mPort == mPort;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        if (mHost != null && (!mNetwork.equalsIgnoreCase(mHost) || mPort != DEFAULT_PORT)) {
            String out = mNetwork + "|" + mHost;
            if (mPort != DEFAULT_PORT)
                out += ":" + mPort;
            return out;
        }
        else {
            return mNetwork;
        }
    }

    public String getHost() {
        return mHost;
    }

    public int getPort() {
        return mPort;
    }

    public int getSecurePort() {
        return mPort + 1;
    }

    public String getNetwork() {
        return mNetwork;
    }

    /**
     * Interface for providing a server.
     */
    public interface EndpointServerProvider {
        /**
         * Returns the next server that hasn't been picked yet.
         */
        public EndpointServer next();

        /**
         * Resets the provider to its initial state.
         */
        public void reset();
    }

    /**
     * A basic server provider for a single server.
     */
    public static class SingleServerProvider implements EndpointServerProvider {
        private String mUri;
        private EndpointServer mProvided;
        private boolean mCalled;

        public SingleServerProvider(String uri) {
            mUri = uri;
        }

        public SingleServerProvider(EndpointServer server) {
            mProvided = server;
        }

        @Override
        public EndpointServer next() {
            if (mCalled) {
                return null;
            }
            else {
                mCalled = true;
                if (mProvided == null) {
                    try {
                        return new EndpointServer(mUri);
                    }
                    catch (Exception e) {
                        // custom is not valid
                        return null;
                    }
                }

                return mProvided;
            }
        }

        @Override
        public void reset() {
            mCalled = false;
        }
    }

    /**
     * Returns true if the input value is a valid endpoint address.
     */
    public static boolean validate(String value) {
        return sPattern.matcher(value).matches();
    }

}
