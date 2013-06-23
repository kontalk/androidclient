package org.kontalk.xmpp.crypto;

import java.io.File;
import java.io.FileInputStream;

import org.spongycastle.openpgp.PGPPublicKeyRingCollection;
import org.spongycastle.openpgp.PGPSecretKeyRingCollection;
import org.spongycastle.openpgp.PGPUtil;

import android.content.Context;
import android.util.Log;

public class Keyring {

    private static final String SECRET_KEYRING = "secring.gpg";
    private static final String PUBLIC_KEYRING = "pubring.gpg";

    /** The singleton instance. */
    private static Keyring sInstance;

    /** The secret keyring. */
    private PGPSecretKeyRingCollection mSecretRing;
    /** The public keyring. */
    private PGPPublicKeyRingCollection mPublicRing;

    private final File mSecRingFile;
    private final File mPubRingFile;

    public Keyring(Context context) {
        mSecRingFile = new File(context.getFilesDir(), SECRET_KEYRING);
        mPubRingFile = new File(context.getFilesDir(), PUBLIC_KEYRING);

        try {
            FileInputStream in = new FileInputStream(mSecRingFile);
            // load stuff
            mSecretRing = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(in));
            in.close();
        }
        catch (Exception e) {
            // no secret keyring found
            Log.w("Keyring", "no secret keyring found", e);
        }

        try {
            FileInputStream in = new FileInputStream(mPubRingFile);
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


}
