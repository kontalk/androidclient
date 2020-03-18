/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.operator.KeyFingerPrintCalculator;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcContentSignerBuilder;
import org.bouncycastle.operator.bc.BcDSAContentSignerBuilder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;

import android.os.Build;
import android.os.Parcel;


/**
 * Utility methods for bridging OpenPGP keys with X.509 certificates.<br>
 * Inspired by the Foaf server project.
 * https://svn.java.net/svn/sommer~svn/trunk/misc/FoafServer/pgpx509/src/net/java/dev/sommer/foafserver/utils/PgpX509Bridge.java
 * @author Daniele Ricci
 */
public class X509Bridge {

    private static final KeyFingerPrintCalculator sFingerprintCalculator =
        PGP.sFingerprintCalculator;

    public static final String PEM_TYPE_PRIVATE_KEY = "RSA PRIVATE KEY";
    public static final String PEM_TYPE_CERTIFICATE = "CERTIFICATE";

    private final static String DN_COMMON_PART_O = "OpenPGP to X.509 Bridge";

    private X509Bridge() {
    }

    public static X509Certificate createCertificate(byte[] publicKeyData, PGPSecretKey secretKey, String passphrase)
        throws PGPException, InvalidKeyException, IllegalStateException,
        NoSuchAlgorithmException, SignatureException, CertificateException,
        NoSuchProviderException, IOException, OperatorCreationException {

        PGPPublicKeyRing pubRing = new PGPPublicKeyRing(publicKeyData, sFingerprintCalculator);

        return createCertificate(pubRing, secretKey, passphrase);

    }

    public static X509Certificate createCertificate(PGPPublicKeyRing publicKeyring, PGPSecretKey secretKey, String passphrase)
        throws PGPException, InvalidKeyException, IllegalStateException,
        NoSuchAlgorithmException, SignatureException, CertificateException,
        NoSuchProviderException, IOException, OperatorCreationException {

        // extract the private key
        PGPDigestCalculatorProvider sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build();
        PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder(sha1Calc)
            .setProvider(PGP.PROVIDER)
            .build(passphrase.toCharArray());

        PGPPrivateKey privateKey = secretKey.extractPrivateKey(decryptor);
        return createCertificate(publicKeyring, privateKey);

    }

    public static X509Certificate createCertificate(byte[] privateKeyData, byte[] publicKeyData, String passphrase)
        throws PGPException, IOException, InvalidKeyException, IllegalStateException,
        NoSuchAlgorithmException, SignatureException, CertificateException, NoSuchProviderException, OperatorCreationException {

        PGPSecretKeyRing secRing = new PGPSecretKeyRing(privateKeyData, sFingerprintCalculator);
        PGPPublicKeyRing pubRing = new PGPPublicKeyRing(publicKeyData, sFingerprintCalculator);

        PGPDigestCalculatorProvider sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build();
        PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder(sha1Calc)
            .setProvider(PGP.PROVIDER)
            .build(passphrase.toCharArray());

        // secret key
        PGPSecretKey secKey = secRing.getSecretKey();

        return createCertificate(pubRing, secKey.extractPrivateKey(decryptor));
    }

    public static X509Certificate createCertificate(byte[] publicKeyData, PGPPrivateKey privateKey)
        throws InvalidKeyException, IllegalStateException, NoSuchAlgorithmException,
        SignatureException, CertificateException, NoSuchProviderException, PGPException, IOException, OperatorCreationException {

        PGPPublicKeyRing pubRing = new PGPPublicKeyRing(publicKeyData, sFingerprintCalculator);

        return createCertificate(pubRing, privateKey);
    }

    static X509Certificate createCertificate(PGPPublicKeyRing publicKeyRing, PGPPrivateKey privateKey)
        throws InvalidKeyException, IllegalStateException, NoSuchAlgorithmException,
        SignatureException, CertificateException, NoSuchProviderException, PGPException, IOException, OperatorCreationException {

        X500NameBuilder x500NameBuilder = new X500NameBuilder();

        /*
         * The X.509 Name to be the subject DN is prepared.
         * The CN is extracted from the Secret Key user ID.
         */

        x500NameBuilder.addRDN(BCStyle.O, DN_COMMON_PART_O);

        PGPPublicKey publicKey = null;

        Iterator<PGPPublicKey> iter = publicKeyRing.getPublicKeys();
        while (iter.hasNext()) {
            PGPPublicKey pk = iter.next();
            if (pk.isMasterKey()) {
                publicKey = pk;
                break;
            }
        }

        if (publicKey == null)
            throw new IllegalArgumentException("no master key found");

        List<String> xmppAddrs = new LinkedList<>();
        for (Iterator<String> it = publicKey.getUserIDs(); it.hasNext();) {
            String attrib = it.next();
            x500NameBuilder.addRDN(BCStyle.CN, attrib);
            // extract email for the subjectAltName
            PGPUserID uid = PGPUserID.parse(attrib);
            if (uid != null && uid.getEmail() != null)
                xmppAddrs.add(uid.getEmail());
        }

        X500Name x509name = x500NameBuilder.build();

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
        Date validTo = null;
        if (publicKey.getValidSeconds()>0)
           validTo = new Date(creationTime.getTime() + 1000L * publicKey.getValidSeconds());

        return createCertificate(
                PGP.convertPublicKey(publicKey),
                PGP.convertPrivateKey(privateKey),
                x509name,
                creationTime, validTo,
                xmppAddrs,
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
     *            (defaults to start date and time if null)
     * @param subjectAltNames
     *            URI to be placed in subjectAltName
     * @return self-signed certificate
     */
    private static X509Certificate createCertificate(PublicKey pubKey,
            PrivateKey privKey, X500Name subject,
            Date startDate, Date endDate, List<String> subjectAltNames, byte[] publicKeyData)
        throws InvalidKeyException, IllegalStateException,
        NoSuchAlgorithmException, SignatureException, CertificateException,
        NoSuchProviderException, IOException, OperatorCreationException {

        /*
         * Sets the signature algorithm.
         */
        BcContentSignerBuilder signerBuilder;
        String pubKeyAlgorithm = pubKey.getAlgorithm();
        if (pubKeyAlgorithm.equals("DSA")) {
            AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder()
                .find("SHA1WithDSA");
            AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder()
                .find(sigAlgId);
            signerBuilder = new BcDSAContentSignerBuilder(sigAlgId, digAlgId);
        }
        else if (pubKeyAlgorithm.equals("RSA")) {
            AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder()
                .find("SHA1WithRSAEncryption");
            AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder()
                .find(sigAlgId);
            signerBuilder = new BcRSAContentSignerBuilder(sigAlgId, digAlgId);
        }
        else {
            throw new RuntimeException(
                    "Algorithm not recognised: " + pubKeyAlgorithm);
        }


        AsymmetricKeyParameter keyp = PrivateKeyFactory.createKey(privKey.getEncoded());
        ContentSigner signer = signerBuilder.build(keyp);

        /*
         * Sets up the validity dates.
         */
        if (startDate == null) {
            startDate = new Date(System.currentTimeMillis());
        }
        if (endDate == null) {
            endDate = startDate;
        }

        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
            /*
             * Sets up the subject distinguished name.
             * Since it's a self-signed certificate, issuer and subject are the
             * same.
             */
            subject,
            /*
             * The serial-number of this certificate is 1. It makes sense
             * because it's self-signed.
             */
            BigInteger.ONE,
            startDate,
            endDate,
            Locale.US,
            subject,
            /*
             * Sets the public-key to embed in this certificate.
             */
            SubjectPublicKeyInfo.getInstance(pubKey.getEncoded())
        );

        /*
         * Adds the Basic Constraint (CA: true) extension.
         */
        certBuilder.addExtension(Extension.basicConstraints, true,
                new BasicConstraints(true));

        /*
         * Adds the Key Usage extension.
         */
        certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(
                KeyUsage.digitalSignature | KeyUsage.nonRepudiation | KeyUsage.keyEncipherment | KeyUsage.keyAgreement | KeyUsage.keyCertSign));

        /*
         * Adds the Netscape certificate type extension.
         */
        certBuilder.addExtension(MiscObjectIdentifiers.netscapeCertType,
                false, new NetscapeCertType(
                NetscapeCertType.sslClient | NetscapeCertType.smime));

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

        /*
         * Adds the subject key identifier extension.
         */
        SubjectKeyIdentifier subjectKeyIdentifier =
                extUtils.createSubjectKeyIdentifier(pubKey);
        certBuilder.addExtension(Extension.subjectKeyIdentifier,
                false, subjectKeyIdentifier);

        /*
         * Adds the authority key identifier extension.
         */
        AuthorityKeyIdentifier authorityKeyIdentifier =
                extUtils.createAuthorityKeyIdentifier(pubKey);
        certBuilder.addExtension(Extension.authorityKeyIdentifier,
                false, authorityKeyIdentifier);

        /*
         * Adds the subject alternative-name extension.
         */
        if (subjectAltNames != null && subjectAltNames.size() > 0) {
            GeneralName[] names = new GeneralName[subjectAltNames.size()];
            for (int i = 0; i < names.length; i++)
                names[i] = new GeneralName(GeneralName.otherName,
                    new XmppAddrIdentifier(subjectAltNames.get(i)));

            certBuilder.addExtension(Extension.subjectAlternativeName,
                    false, new GeneralNames(names));
        }

        /*
         * Adds the PGP public key block extension.
         */
        SubjectPGPPublicKeyInfo publicKeyExtension =
            new SubjectPGPPublicKeyInfo(publicKeyData);
        certBuilder.addExtension(SubjectPGPPublicKeyInfo.OID, false, publicKeyExtension);

        /*
         * Creates and sign this certificate with the private key
         * corresponding to the public key of the certificate
         * (hence the name "self-signed certificate").
         */
        X509CertificateHolder holder = certBuilder.build(signer);

        /*
         * Checks that this certificate has indeed been correctly signed.
         */
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(holder);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cert.verify(pubKey, PGP.PROVIDER);
        }
        else {
            cert.verify(pubKey);
        }

        return cert;
    }

    public static X509Certificate fromParcel(Parcel in) throws PGPException {
        // TODO
        return null;
    }

    public static X509Certificate load(byte[] certData)
            throws CertificateException {
        return load(new ByteArrayInputStream(certData));
    }

    public static X509Certificate load(InputStream certData)
            throws CertificateException {

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509", PGP.PROVIDER);
        return (X509Certificate) certFactory.generateCertificate(certData);
    }

    public static KeyStore exportCertificate(X509Certificate certificate, PrivateKey privateKey)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {

        KeyStore store = KeyStore.getInstance("PKCS12", PGP.PROVIDER);

        store.load(null, null);

        store.setKeyEntry("Kontalk Personal Key", privateKey, null, new Certificate[] { certificate });

        return store;
    }

}
