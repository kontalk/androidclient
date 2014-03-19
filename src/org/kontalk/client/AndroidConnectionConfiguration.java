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

import org.jivesoftware.smack.ConnectionConfiguration;


public class AndroidConnectionConfiguration extends ConnectionConfiguration {

    public AndroidConnectionConfiguration(String serviceName) {
        super(serviceName);
        //AndroidInit();
    }

    public AndroidConnectionConfiguration(String host, int port) {
        super(host, port);
        //AndroidInit();
    }

    public AndroidConnectionConfiguration(String host, int port, String name) {
        super(host, port, name);
	    //AndroidInit();
    }

    /* TODO move to custom SSL context
    private void AndroidInit() {
    	// API 14 is Ice Cream Sandwich
        if (Build.VERSION.SDK_INT >= 14) {
            setTruststoreType("AndroidCAStore");
            setTruststorePassword(null);
            setTruststorePath(null);
        } else {
            setTruststoreType("BKS");
            String path = System.getProperty("javax.net.ssl.trustStore");
            if (path == null)
        	path = System.getProperty("java.home") + File.separator + "etc"
        	    + File.separator + "security" + File.separator
        	    + "cacerts.bks";
            setTruststorePath(path);
        }
    }
    */

}
