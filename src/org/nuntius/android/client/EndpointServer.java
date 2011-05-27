package org.nuntius.android.client;


/**
 * Defines an endpoint server.
 * @author Daniele Ricci
 * @version 1.0
 */
public class EndpointServer {

    /**
     * Polling service path.
     */
    private static final String POLLING_PATH = "/polling.php";

    /**
     * Request service path.
     * The command will be appended.
     */
    private static final String REQUEST_PATH = "/request.php?cmd=";

    private final String requestURL;
    private final String pollingURL;

    public EndpointServer(String baseURL) {
        this.requestURL = baseURL + REQUEST_PATH;
        this.pollingURL = baseURL + POLLING_PATH;
    }

    public String getPollingURL() {
        return pollingURL;
    }

    public String getRequestURL(String cmd) {
        return requestURL + cmd;
    }
}
