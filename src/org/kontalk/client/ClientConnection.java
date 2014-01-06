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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.kontalk.Kontalk;
import org.kontalk.client.BoxProtocol.BoxContainer;
import org.kontalk.client.Protocol.AuthenticateRequest;
import org.kontalk.client.Protocol.LoginRequest;
import org.kontalk.client.Protocol.RegistrationRequest;
import org.kontalk.client.Protocol.RegistrationResponse;
import org.kontalk.client.Protocol.ServerInfoRequest;
import org.kontalk.client.Protocol.ServerInfoResponse;
import org.kontalk.client.Protocol.ValidationRequest;
import org.kontalk.client.Protocol.ValidationResponse;
import org.kontalk.util.RandomString;

import com.google.protobuf.MessageLite;


/**
 * Client connection class.
 * @author Daniele Ricci
 */
public class ClientConnection {
    protected final EndpointServer mServer;
    protected Socket mSocket;

    protected OutputStream out;
    protected InputStream in;

    protected String mAuthToken;

    public ClientConnection(EndpointServer server) {
        mServer = server;
        mSocket = new Socket();
    }

    public void connect() throws IOException {
        mSocket.connect(new InetSocketAddress(mServer.getHost(), mServer.getPort()));
        out = mSocket.getOutputStream();
        in = mSocket.getInputStream();
    }

    /** Recreates connection based on the parameters given to the constructor. */
    public void reconnect() throws IOException {
        if (!isConnected()) {
            mSocket = new Socket();
            connect();
        }
    }

    public void login(String token, int flags) throws IOException {
        mAuthToken = token;
        LoginRequest.Builder b = LoginRequest.newBuilder();
        b.setToken(token);
        b.setClientProtocol(Kontalk.CLIENT_PROTOCOL);
        b.setFlags(flags);
        send(b.build());
    }

    public void authenticate(String token) throws IOException {
        mAuthToken = token;
        AuthenticateRequest.Builder b = AuthenticateRequest.newBuilder();
        b.setToken(token);
        send(b.build());
    }

    /** Sends a serverinfo request and waits for response. */
    public ServerInfoResponse serverinfoWait() throws IOException {
        ServerInfoRequest.Builder b = ServerInfoRequest.newBuilder();
        // deprecated -- b.setClientProtocol(Kontalk.CLIENT_PROTOCOL);
        send(b.build());

        // receive and parse data now
        BoxContainer box = recv();
        if (box != null && box.getName().equals(ServerInfoResponse.class.getSimpleName()))
            return ServerInfoResponse.parseFrom(box.getValue());

        return null;
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

    public void close() {
        try {
            mSocket.shutdownInput();
            mSocket.shutdownOutput();
            /*
             * do not call {@link Socket#close}. This a synchronized method so
             * it might block.
             */
            //mSocket.close();
        }
        catch (Exception e) {
            // ignore exceptions
        }
        finally {
            // discard any reference
            in = null;
            out = null;
            mSocket = null;
        }
    }

}
