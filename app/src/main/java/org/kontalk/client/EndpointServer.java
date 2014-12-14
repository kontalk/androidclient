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


import java.util.regex.Pattern;

/**
 * Defines a server address and features.
 * @author Daniele Ricci
 * @version 1.0
 * TODO http port is deprecated
 */
public class EndpointServer {
    /** Default Kontalk client port. */
    public static final int DEFAULT_PORT = 5222;

    /** Validation pattern. Very basic. */
    // TODO use this for parsing
    private static final Pattern sPattern = Pattern.compile("^[A-Za-z0-9\\-\\.]+\\|[A-Za-z0-9\\-\\.]+(:\\d+)?$");

    private String mHost;
    private int mPort;
    private String mNetwork;

    public EndpointServer(String host) {
        this(null, host, DEFAULT_PORT);
        if (host.contains("|")) {
            String[] parsed = host.split("\\|");
            mNetwork = parsed[0];
            mHost = parsed[1];
        }
        if (mHost.contains(":")) {
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
        return o instanceof EndpointServer &&
            ((EndpointServer) o).mHost.equalsIgnoreCase(mHost) &&
            ((EndpointServer) o).mNetwork.equalsIgnoreCase(mNetwork) &&
            ((EndpointServer) o).mPort == mPort;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        return mNetwork + "|" + mHost + ":" + mPort;
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

    /** Interface for providing a server. */
    public interface EndpointServerProvider {
        /** Returns the next server that hasn't been picked yet. */
        public EndpointServer next();
        /** Resets the provider to its initial state. */
        public void reset();
    }

    /** A basic server provider for a single server. */
    public static class SingleServerProvider implements EndpointServerProvider {
        private String mUri;
        private boolean mCalled;

        public SingleServerProvider(String uri) {
            mUri = uri;
        }

        @Override
        public EndpointServer next() {
            if (mCalled) {
                return null;
            }
            else {
                mCalled = true;
                try {
                    return new EndpointServer(mUri);
                }
                catch (Exception e) {
                    // custom is not valid
                    return null;
                }
            }
        }

        @Override
        public void reset() {
            mCalled = false;
        }
    }

    /** Returns true if the input value is a valid endpoint address. */
    public static boolean validate(String value) {
        return sPattern.matcher(value).matches();
    }

}
