package org.kontalk.xmpp.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.spongycastle.openpgp.PGPPublicKeyRingCollection;
import org.spongycastle.openpgp.PGPUtil;

import android.content.Context;
import android.util.Log;


/**
 * Users public keyring.
 * TODO this might not be needed. In fact, public keys can be stored in users
 * database and retrieved (cached) on demand.
 */
public class Keyring {

    private static final String PUBLIC_KEYRING = "pubring.gpg";

    /** The singleton instance. */
    private static Keyring sInstance;

    /** The public keyring. */
    private PGPPublicKeyRingCollection mPublicRing;

    public Keyring(Context context) {
        try {
            InputStream in = context.openFileInput(PUBLIC_KEYRING);
            // load stuff
            mPublicRing = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(in));
            in.close();
        }
        catch (Exception e) {
            // no public keyring found
            Log.w("Keyring", "no public keyring found", e);
        }

    }

    public static void init(Context context) {
        sInstance = new Keyring(context);
    }

    public static Keyring getInstance() {
        return sInstance;
    }

    public void store(Context context) throws IOException {
        OutputStream out = context.openFileOutput(PUBLIC_KEYRING, Context.MODE_PRIVATE);
        mPublicRing.encode(out);
        out.close();
    }

}
