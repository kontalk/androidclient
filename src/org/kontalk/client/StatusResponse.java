package org.kontalk.client;

import java.util.Map;


/**
 * Represents a status tag.
 * @author Daniele Ricci
 * @version 1.0
 */
public class StatusResponse {

    /** Operation completed successfully. */
    public static final int STATUS_SUCCESS = 0;

    /** Generic error occured. */
    public static final int STATUS_ERROR = 1;

    /** Server is busy - try again later. */
    public static final int STATUS_BUSY = 2;

    /** Code verification failed. */
    public static final int STATUS_VERIFICATION_FAILED = 3;

    /** Invalid phone number. */
    public static final int STATUS_INVALID_PHONE_NUMBER = 4;

    /** Message TTL expired. */
    public static final int STATUS_TTL_EXPIRED = 5;

    public int code;
    public Map<String, Object> extra;

    public StatusResponse(int code) {
        this.code = code;
    }
}
