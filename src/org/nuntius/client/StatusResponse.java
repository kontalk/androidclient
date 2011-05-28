package org.nuntius.client;

import java.util.Map;


/**
 * Represents a status tag.
 * @author Daniele Ricci
 * @version 1.0
 */
public class StatusResponse {

    public int code;
    public Map<String, String> extra;

    public StatusResponse(int code) {
        this.code = code;
    }
}
