package org.kontalk.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.kontalk.client.BoxProtocol.BoxContainer;
import org.kontalk.client.Protocol.AuthenticateRequest;
import org.kontalk.client.Protocol.RegistrationRequest;
import org.kontalk.client.Protocol.RegistrationResponse;
import org.kontalk.client.Protocol.ValidationRequest;
import org.kontalk.client.Protocol.ValidationResponse;
import org.kontalk.util.RandomString;

import android.content.Context;

import com.google.protobuf.MessageLite;


/**
 * Client connection class.
 * @author Daniele Ricci
 */
public class ClientConnection {
    private final EndpointServer mServer;
    private Context mContext;
    private Socket mSocket;

    private OutputStream out;
    private InputStream in;

    public ClientConnection(Context context, EndpointServer server) {
        mContext = context;
        mServer = server;
    }

    public synchronized void connect() throws IOException {
        if (mSocket == null) {
            mSocket = new Socket(mServer.getHost(), mServer.getPort());
            out = mSocket.getOutputStream();
            in = mSocket.getInputStream();
        }
    }

    public void authenticate(String token) throws IOException {
        AuthenticateRequest.Builder b = AuthenticateRequest.newBuilder();
        b.setToken(token);
        send(b.build());
    }

    /** Sends a registration request for a username (e.g. a phone number). */
    public RegistrationResponse registerWait(String username) throws IOException {
        RegistrationRequest.Builder b = RegistrationRequest.newBuilder();
        b.setUsername(username);
        send(b.build());

        // receive and parse data now
        BoxContainer box = recv();
        if (box != null && box.getName().equals(RegistrationResponse.class.getSimpleName()))
            return RegistrationResponse.parseFrom(box.getValue());

        return null;
    }

    /** Sends a validation (registration 2nd step). */
    public ValidationResponse validateWait(String code) throws IOException {
        ValidationRequest.Builder b = ValidationRequest.newBuilder();
        b.setValidationCode(code);
        send(b.build());

        // receive and parse data now
        BoxContainer box = recv();
        if (box != null && box.getName().equals(ValidationResponse.class.getSimpleName()))
            return ValidationResponse.parseFrom(box.getValue());

        return null;
    }

    public boolean isConnected() {
        return mSocket != null && mSocket.isConnected();
    }

    public String send(MessageLite pack) throws IOException {
        return send(pack, RandomString.generate(8));
    }

    /** Writes a pack to the connection (blocking). */
    public String send(MessageLite pack, String txId) throws IOException {
        BoxContainer.Builder b = BoxContainer.newBuilder();
        b.setTxId(txId);
        b.setName(pack.getClass().getSimpleName());
        b.setValue(pack.toByteString());
        b.build().writeDelimitedTo(out);
        return txId;
    }

    /** Reads a pack from the connection (blocking). */
    public BoxContainer recv() throws IOException {
        return BoxContainer.parseDelimitedFrom(in);
    }

    public synchronized void close() throws IOException {
        if (mSocket != null) {
            mSocket.close();
            mSocket = null;
            in = null;
            out = null;
        }
    }

}
