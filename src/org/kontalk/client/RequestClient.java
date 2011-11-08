package org.kontalk.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.apache.http.HttpResponse;
import org.kontalk.crypto.Coder;
import org.kontalk.service.RequestListener;
import org.kontalk.ui.MessagingPreferences;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;


/**
 * A generic request endpoint client.
 * @author Daniele Ricci
 * @version 1.0
 */
public class RequestClient extends AbstractClient {

    public RequestClient(Context context, EndpointServer server, String token) {
        super(context, server, token);
    }

    public Protocol.MessageSent message(final String[] group, final String mime,
                final byte[] content, final MessageSender job, final RequestListener listener)
            throws IOException {

        try {
            byte[] toMessage = null;
            Coder coder = null;
            boolean encrypted = false;
            // check if we have to encrypt the message
            if (job.getEncryptKey() != null) {
                coder = MessagingPreferences.getEncryptCoder(job.getEncryptKey());
                if (coder != null) {
                    toMessage = coder.encrypt(content);
                    encrypted = true;
                }
            }

            if (coder == null) {
                toMessage = content;
            }

            // http request!
            currentRequest = mServer.prepareMessage(job, listener, mAuthToken, group, mime,
                new ByteArrayInputStream(toMessage), toMessage.length, encrypted);

            HttpResponse response = mServer.execute(currentRequest);
            return Protocol.MessageSent.parseFrom(response.getEntity().getContent());
        }
        catch (Exception e) {
            throw innerException("post message error", e);
        }
        finally {
            currentRequest = null;
        }
    }

    public Protocol.MessageSent message(final String[] group, final String mime, final Uri uri,
            final Context context, final MessageSender job, final RequestListener listener)
                throws IOException {

        try {
            AssetFileDescriptor stat = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            long length = stat.getLength();
            InputStream in = context.getContentResolver().openInputStream(uri);

            InputStream toMessage = null;
            long toLength = 0;
            Coder coder = null;
            boolean encrypted = false;
            // check if we have to encrypt the message
            if (job.getEncryptKey() != null) {
                coder = MessagingPreferences.getEncryptCoder(job.getEncryptKey());
                if (coder != null) {
                    toMessage = coder.wrapInputStream(in);
                    toLength = Coder.getEncryptedLength(length);
                    encrypted = true;
                }
            }

            if (coder == null) {
                toMessage = in;
                toLength = length;
            }

            // http request!
            currentRequest = mServer.prepareMessage(job, listener, mAuthToken, group, mime, toMessage, toLength, encrypted);
            HttpResponse response = mServer.execute(currentRequest);
            return Protocol.MessageSent.parseFrom(response.getEntity().getContent());
        }
        catch (Exception e) {
            throw innerException("post message error", e);
        }
        finally {
            currentRequest = null;
        }
    }

    public Protocol.Received received(String[] msgId) throws IOException {
        try {
            currentRequest = mServer.prepareReceived(mAuthToken, msgId);
            HttpResponse response = mServer.execute(currentRequest);
            return Protocol.Received.parseFrom(response.getEntity().getContent());
        }
        catch (Exception e) {
            throw innerException("message received error", e);
        }
        finally {
            currentRequest = null;
        }
    }

    public Protocol.PhoneValidation validate(String phone) throws IOException {
        try {
            currentRequest = mServer.prepareValidation(phone);
            HttpResponse response = mServer.execute(currentRequest);
            return Protocol.PhoneValidation.parseFrom(response.getEntity().getContent());
        }
        catch (Exception e) {
            throw innerException("phone validation error", e);
        }
        finally {
            currentRequest = null;
        }
    }

    public Protocol.Authentication authenticate(String validationCode) throws IOException {
        try {
            currentRequest = mServer.prepareAuthentication(validationCode);
            HttpResponse response = mServer.execute(currentRequest);
            return Protocol.Authentication.parseFrom(response.getEntity().getContent());
        }
        catch (Exception e) {
            throw innerException("validation code error", e);
        }
        finally {
            currentRequest = null;
        }
    }

    public Protocol.LookupResponse lookup(Collection<String> userId) throws IOException {
        try {
            currentRequest = mServer.prepareLookup(mAuthToken, userId);
            HttpResponse response = mServer.execute(currentRequest);
            return Protocol.LookupResponse.parseFrom(response.getEntity().getContent());
        }
        catch (Exception e) {
            throw innerException("user lookup error", e);
        }
        finally {
            currentRequest = null;
        }
    }

    public Protocol.LookupResponse lookup(String userId) throws IOException {
        try {
            currentRequest = mServer.prepareLookup(mAuthToken, userId);
            HttpResponse response = mServer.execute(currentRequest);
            return Protocol.LookupResponse.parseFrom(response.getEntity().getContent());
        }
        catch (Exception e) {
            throw innerException("user lookup error", e);
        }
        finally {
            currentRequest = null;
        }
    }

    public Protocol.UserUpdate update(String statusMessage, String googleRegId) throws IOException {
        try {
            currentRequest = mServer.prepareUpdate(mAuthToken, statusMessage, googleRegId);
            HttpResponse response = mServer.execute(currentRequest);
            return Protocol.UserUpdate.parseFrom(response.getEntity().getContent());
        }
        catch (Exception e) {
            throw innerException("user update error", e);
        }
        finally {
            currentRequest = null;
        }
    }

    private IOException innerException(String detail, Throwable cause) {
        IOException ie = new IOException(detail);
        ie.initCause(cause);
        return ie;
    }
}
