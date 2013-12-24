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

    // based on UUID 24e844a0-6cbc-11e3-8997-0002a5d5c51b
    public static final String OID = "2.25.49058212633447845622587297037800555803";

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
