package org.kontalk.xmpp.client;

import java.io.File;

import org.jivesoftware.smack.ConnectionConfiguration;

import android.os.Build;


public class AndroidConnectionConfiguration extends ConnectionConfiguration {

    public AndroidConnectionConfiguration(String serviceName) {
        super(serviceName);
        AndroidInit();
    }

    public AndroidConnectionConfiguration(String host, int port) {
        super(host, port);
        AndroidInit();
    }

    public AndroidConnectionConfiguration(String host, int port, String name) {
        super(host, port, name);
	    AndroidInit();
    }

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

}
