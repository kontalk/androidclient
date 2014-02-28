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

package org.kontalk.crypto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Vector;

import org.spongycastle.asn1.DERObjectIdentifier;
import org.spongycastle.asn1.misc.MiscObjectIdentifiers;
import org.spongycastle.asn1.misc.NetscapeCertType;
import org.spongycastle.asn1.x509.AuthorityKeyIdentifier;
import org.spongycastle.asn1.x509.BasicConstraints;
import org.spongycastle.asn1.x509.GeneralName;
import org.spongycastle.asn1.x509.GeneralNames;
import org.spongycastle.asn1.x509.KeyUsage;
import org.spongycastle.asn1.x509.SubjectKeyIdentifier;
import org.spongycastle.asn1.x509.X509Extensions;
import org.spongycastle.asn1.x509.X509Name;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPPrivateKey;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;
import org.spongycastle.openpgp.PGPSecretKey;
import org.spongycastle.openpgp.PGPSecretKeyRing;
import org.spongycastle.openpgp.operator.KeyFingerPrintCalculator;
import org.spongycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.spongycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.spongycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.spongycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.spongycastle.x509.X509V3CertificateGenerator;
import org.spongycastle.x509.extension.AuthorityKeyIdentifierStructure;
import org.spongycastle.x509.extension.SubjectKeyIdentifierStructure;

import android.os.Parcel;


/**
 * Utility methods for bridging OpenPGP keys with X.509 certificates.<br>
 * Inspired by the Foaf server project.
 * @author Daniele Ricci
 * @see https://svn.java.net/svn/sommer~svn/trunk/misc/FoafServer/pgpx509/src/net/java/dev/sommer/foafserver/utils/PgpX509Bridge.java
 */
public class X509Bridge {

    private final static String DN_COMMON_PART_O = "OpenPGP to X.509 Bridge";

    private X509Bridge() {
    }

    public static X509Certificate createCertificate(byte[] publicKeyData, PGPSecretKey secretKey, String passphrase, String subjectAltName)
            throws PGPException, InvalidKeyException, IllegalStateException,
            NoSuchAlgorithmException, SignatureException, CertificateException,
            NoSuchProviderException, IOException {

        KeyFingerPrintCalculator fpr = new BcKeyFingerprintCalculator();
        PGPPublicKeyRing pubRing = new PGPPublicKeyRing(publicKeyData, fpr);

        return createCertificate(pubRing, secretKey, passphrase, subjectAltName);

    }

    public static X509Certificate createCertificate(PGPPublicKeyRing publicKeyring, PGPSecretKey secretKey, String passphrase, String subjectAltName)
            throws PGPException, InvalidKeyException, IllegalStateException,
            NoSuchAlgorithmException, SignatureException, CertificateException,
            NoSuchProviderException, IOException {

        // extract the private key
        PGPDigestCalculatorProvider sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build();
        PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder(sha1Calc)
            .setProvider(PGP.PROVIDER)
            .build(passphrase.toCharArray());

        PGPPrivateKey privateKey = secretKey.extractPrivateKey(decryptor);
        return createCertificate(publicKeyring, privateKey, subjectAltName);

    }

    public static X509Certificate createCertificate(byte[] privateKeyData, byte[] publicKeyData, String passphrase, String subjectAltName)
        throws PGPException, IOException, InvalidKeyException, IllegalStateException,
        NoSuchAlgorithmException, SignatureException, CertificateException, NoSuchProviderException {

        KeyFingerPrintCalculator fpr = new BcKeyFingerprintCalculator();
        PGPSecretKeyRing secRing = new PGPSecretKeyRing(privateKeyData, fpr);
        PGPPublicKeyRing pubRing = new PGPPublicKeyRing(publicKeyData, fpr);

        PGPDigestCalculatorProvider sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build();
        PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder(sha1Calc)
            .setProvider(PGP.PROVIDER)
            .build(passphrase.toCharArray());

        // secret key
        PGPSecretKey secKey = secRing.getSecretKey();

        return createCertificate(pubRing, secKey.extractPrivateKey(decryptor), subjectAltName);
    }

    public static X509Certificate createCertificate(byte[] publicKeyData, PGPPrivateKey privateKey, String subjectAltName)
            throws InvalidKeyException, IllegalStateException, NoSuchAlgorithmException,
                SignatureException, CertificateException, NoSuchProviderException, PGPException, IOException {

        KeyFingerPrintCalculator fpr = new BcKeyFingerprintCalculator();
        PGPPublicKeyRing pubRing = new PGPPublicKeyRing(publicKeyData, fpr);

        return createCertificate(pubRing, privateKey, subjectAltName);
    }

    public static X509Certificate createCertificate(PGPPublicKeyRing publicKeyRing, PGPPrivateKey privateKey, String subjectAltName)
            throws InvalidKeyException, IllegalStateException, NoSuchAlgorithmException,
                SignatureException, CertificateException, NoSuchProviderException, PGPException, IOException {

        /*
         * The X.509 Name to be the subject DN is prepared.
         * The CN is extracted from the Secret Key user ID.
         */
        Vector<DERObjectIdentifier> x509NameOids = new Vector<DERObjectIdentifier>();
        Vector<String> x509NameValues = new Vector<String>();

        x509NameOids.add(X509Name.O);
        x509NameValues.add(DN_COMMON_PART_O);

        PGPPublicKey publicKey = publicKeyRing.getPublicKey();

        for (@SuppressWarnings("unchecked") Iterator<Object> it = publicKey.getUserIDs(); it.hasNext();) {
            Object attrib = it.next();
            x509NameOids.add(X509Name.CN);
            x509NameValues.add(attrib.toString());
        }

        X509Name x509name = new X509Name(x509NameOids, x509NameValues);

        /*
         * To check the signature from the certificate on the recipient side,
         * the creation time needs to be embedded in the certificate.
         * It seems natural to make this creation time be the "not-before"
         * date of the X.509 certificate.
         * Unlimited PGP keys have a validity of 0 second. In this case,
         * the "not-after" date will be the same as the not-before date.
         * This is something that needs to be checked by the service
         * receiving this certificate.
         */
        Date creationTime = publicKey.getCreationTime();
        Date validTo=null;
        if (publicKey.getValidSeconds()>0)
           validTo=new Date(creationTime.getTime() + 1000L * publicKey.getValidSeconds());

        return createCertificate(
                publicKey.getKey(PGP.PROVIDER), privateKey.getKey(), x509name,
                creationTime, validTo,
                subjectAltName,
                publicKeyRing.getEncoded());
    }

    /**
     * Creates a self-signed certificate from a public and private key. The
     * (critical) key-usage extension is set up with: digital signature,
     * non-repudiation, key-encipherment, key-agreement and certificate-signing.
     * The (non-critical) Netscape extension is set up with: SSL client and
     * S/MIME. A URI subjectAltName may also be set up.
     *
     * @param pubKey
     *            public key
     * @param privKey
     *            private key
     * @param subject
     *            subject (and issuer) DN for this certificate, RFC 2253 format
     *            preferred.
     * @param startDate
     *            date from which the certificate will be valid
     *            (defaults to current date and time if null)
     * @param endDate
     *            date until which the certificate will be valid
     *            (defaults to current date and time if null)     *
     * @param subjAltNameURI
     *            URI to be placed in subjectAltName
     * @return self-signed certificate
     * @throws InvalidKeyException
     * @throws SignatureException
     * @throws NoSuchAlgorithmException
     * @throws IllegalStateException
     * @throws NoSuchProviderException
     * @throws CertificateException
     * @throws Exception
     */
    private static X509Certificate createCertificate(PublicKey pubKey,
            PrivateKey privKey, X509Name subject,
            Date startDate, Date endDate, String subjectAltName, byte[] publicKeyData)
            throws InvalidKeyException, IllegalStateException,
            NoSuchAlgorithmException, SignatureException, CertificateException,
            NoSuchProviderException {

        X509V3CertificateGenerator certGenerator = new X509V3CertificateGenerator();

        certGenerator.reset();
        /*
         * Sets up the subject distinguished name.
         * Since it's a self-signed certificate, issuer and subject are the
         * same.
         */
        certGenerator.setIssuerDN(subject);
        certGenerator.setSubjectDN(subject);

        /*
         * Sets up the validity dates.
         */
        if (startDate == null) {
            startDate = new Date(System.currentTimeMillis());
        }
        certGenerator.setNotBefore(startDate);
        if (endDate == null) {
            endDate = new Date(startDate.getTime()+(365L*24L*60L*60L*1000L));
               System.out.println("end date is="+ DateFormat.getDateInstance().format(endDate));
        }

        certGenerator.setNotAfter(endDate);

        /*
         * The serial-number of this certificate is 1. It makes sense
         * because it's self-signed.
         */
        certGenerator.setSerialNumber(BigInteger.ONE);
        /*
         * Sets the public-key to embed in this certificate.
         */
        certGenerator.setPublicKey(pubKey);
        /*
         * Sets the signature algorithm.
         */
        String pubKeyAlgorithm = pubKey.getAlgorithm();
        if (pubKeyAlgorithm.equals("DSA")) {
            certGenerator.setSignatureAlgorithm("SHA1WithDSA");
        }
        else if (pubKeyAlgorithm.equals("RSA")) {
            certGenerator.setSignatureAlgorithm("SHA1WithRSAEncryption");
        }
        else if (pubKeyAlgorithm.equals("ECDSA")) {
            // TODO is this even legal?
            certGenerator.setSignatureAlgorithm("SHA1WithECDSA");
        }
        else {
            throw new RuntimeException(
                    "Algorithm not recognised: " + pubKeyAlgorithm);
        }

        /*
         * Adds the Basic Constraint (CA: true) extension.
         */
        certGenerator.addExtension(X509Extensions.BasicConstraints, true,
                new BasicConstraints(true));

        /*
         * Adds the Key Usage extension.
         */
        certGenerator.addExtension(X509Extensions.KeyUsage, true, new KeyUsage(
                KeyUsage.digitalSignature | KeyUsage.nonRepudiation | KeyUsage.keyEncipherment | KeyUsage.keyAgreement | KeyUsage.keyCertSign));

        /*
         * Adds the Netscape certificate type extension.
         */
        certGenerator.addExtension(MiscObjectIdentifiers.netscapeCertType,
                false, new NetscapeCertType(
                NetscapeCertType.sslClient | NetscapeCertType.smime));

        /*
         * Adds the subject key identifier extension.
         */
        SubjectKeyIdentifier subjectKeyIdentifier =
                new SubjectKeyIdentifierStructure(pubKey);
        certGenerator.addExtension(X509Extensions.SubjectKeyIdentifier,
                false, subjectKeyIdentifier);

        /*
         * Adds the authority key identifier extension.
         */
        AuthorityKeyIdentifier authorityKeyIdentifier =
                new AuthorityKeyIdentifierStructure(pubKey);
        certGenerator.addExtension(X509Extensions.AuthorityKeyIdentifier,
                false, authorityKeyIdentifier);

        /*
         * Adds the subject alternative-name extension.
         */
        if (subjectAltName != null) {
            GeneralNames subjectAltNames = new GeneralNames(new GeneralName(
                    GeneralName.otherName, subjectAltName));
            certGenerator.addExtension(X509Extensions.SubjectAlternativeName,
                    false, subjectAltNames);
        }

        /*
         * Adds the PGP public key block extension.
         */
        SubjectPGPPublicKeyInfo publicKeyExtension =
            new SubjectPGPPublicKeyInfo(publicKeyData);
        certGenerator.addExtension(SubjectPGPPublicKeyInfo.OID, false, publicKeyExtension);

        /*
         * Creates and sign this certificate with the private key
         * corresponding to the public key of the certificate
         * (hence the name "self-signed certificate").
         */
        X509Certificate cert = certGenerator.generate(privKey);

        /*
         * Checks that this certificate has indeed been correctly signed.
         */
        cert.verify(pubKey);

        return cert;
    }

    public static X509Certificate fromParcel(Parcel in) throws PGPException {
        // TODO
        return null;
    }

    public static X509Certificate load(byte[] certData)
    		throws CertificateException, NoSuchProviderException {

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509", PGP.PROVIDER);
        InputStream in = new ByteArrayInputStream(certData);
        return (X509Certificate) certFactory.generateCertificate(in);
    }

    public static KeyStore exportCertificate(X509Certificate certificate, PrivateKey privateKey)
    		throws KeyStoreException, NoSuchProviderException, NoSuchAlgorithmException, CertificateException, IOException {

        KeyStore store = KeyStore.getInstance("PKCS12", PGP.PROVIDER);

        store.load(null, null);

        store.setKeyEntry("Kontalk Personal Key", privateKey, null, new Certificate[] { certificate });

        return store;
    }

}
