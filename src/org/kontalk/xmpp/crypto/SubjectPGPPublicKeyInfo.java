package org.kontalk.xmpp.crypto;

import java.io.IOException;

import org.spongycastle.asn1.ASN1Encodable;
import org.spongycastle.asn1.ASN1Object;
import org.spongycastle.asn1.ASN1Primitive;
import org.spongycastle.asn1.DERBitString;


/**
 * A custom X.509 extension for a PGP public key.
 * @author Daniele Ricci
 */
public class SubjectPGPPublicKeyInfo extends ASN1Object {

    // FIXME just for testing
    public static final String OID = "1.2.3.4.5";

    private DERBitString            keyData;

    public SubjectPGPPublicKeyInfo(ASN1Encodable publicKey) throws IOException {
        keyData = new DERBitString(publicKey);
    }

    public SubjectPGPPublicKeyInfo(byte[] publicKey) {
        keyData = new DERBitString(publicKey);
    }

    public DERBitString getPublicKeyData()
    {
        return keyData;
    }

    @Override
    public ASN1Primitive toASN1Primitive() {
        return keyData;
    }

}
