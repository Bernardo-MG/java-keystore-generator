/**
 * The MIT License (MIT)
 * <p>
 * Copyright (c) 2015 the original author or authors.
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.wandrell.util.ksgen;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.SecretKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.bc.BcX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default factory for generating key stores.
 *
 * @author Bernardo Martínez Garrido
 */
public final class DefaultKeystoreFactory implements KeystoreFactory {

    /**
     * The logger used for logging the key store creation.
     */
    private static final Logger LOGGER = LoggerFactory
            .getLogger(DefaultKeystoreFactory.class);

    /**
     * Default constructor.
     */
    public DefaultKeystoreFactory() {
        super();
    }

    @Override
    public final KeyStore getJavaCryptographicExtensionKeyStore(
            final String password, final String alias) throws Exception {
        final KeyStore kstore; // Generated key store

        kstore = getKeystore(password, "JCEKS");
        addSecretKey(kstore, alias, password);

        return kstore;
    }

    @Override
    public final KeyStore getJavaKeyStore(final String password,
            final String alias, final String issuer) throws Exception {
        final KeyStore kstore; // Generated key store

        kstore = getKeystore(password);
        addCertificate(kstore, password, alias, issuer);

        return kstore;
    }

    /**
     * Adds a certificate to a key store.
     *
     * @param kstore
     *            key store where the certificate will be added
     * @param password
     *            password for the certificate
     * @param alias
     *            alias for the certificate
     * @param issuer
     *            certificate issuer
     * @throws NoSuchAlgorithmException
     *             if an algorithm required to create the key store could not be
     *             found
     * @throws NoSuchProviderException
     *             if a required provider is missing
     * @throws InvalidKeyException
     *             if there was a problem with the key
     * @throws OperatorCreationException
     *             if there was a problem creation a bouncy castle operator
     * @throws CertificateException
     *             if any of the certificates in the keystore could not be
     *             loaded
     * @throws IOException
     *             if there is an I/O or format problem with the key store data
     * @throws KeyStoreException
     *             if a key store related error ocurred
     * @throws SignatureException
     *             if any problem occurs while signing the certificate
     */
    private final void addCertificate(final KeyStore kstore,
            final String password, final String alias, final String issuer)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidKeyException, OperatorCreationException,
            CertificateException, IOException, KeyStoreException,
            SignatureException {
        final KeyPair keypair;          // Key pair for the certificate
        final Certificate certificate;  // Generated certificate
        final Certificate[] chain;      // Certificate chain

        keypair = getKeyPair();
        certificate = getCertificate(keypair, issuer);

        chain = new Certificate[] { certificate };

        kstore.setKeyEntry(alias, keypair.getPrivate(), password.toCharArray(),
                chain);

        LOGGER.debug(String.format(
                "Added certificate with alias %s and password %s for issuer %s",
                alias, password, issuer));
    }

    /**
     * Adds a secret key to the received key store.
     *
     * @param kstore
     *            key store where the secret key will be added
     * @param alias
     *            alias for the secret key
     * @param password
     *            password for the secret key
     * @throws KeyStoreException
     *             if a key store related error occurred
     */
    private final void addSecretKey(final KeyStore kstore, final String alias,
            final String password) throws KeyStoreException {
        final SecretKeyEntry secretKeyEntry;  // Secret key entry
        final PasswordProtection keyPassword; // Secret key password protection
        final SecretKey secretKey;            // Secret key password
        final byte[] key;                     // Secret key as array

        key = new byte[] { 1, 2, 3, 4, 5 };
        secretKey = new SecretKeySpec(key, "DES");

        LOGGER.debug(String.format("Created secret key %s with format %s",
                Arrays.asList(secretKey.getEncoded()), secretKey.getFormat()));

        secretKeyEntry = new SecretKeyEntry(secretKey);
        keyPassword = new PasswordProtection(password.toCharArray());
        kstore.setEntry(alias, secretKeyEntry, keyPassword);

        LOGGER.debug(
                String.format("Added secret key with alias %s and password %s",
                        alias, password));
    }

    /**
     * Returns a {@code SubjectKeyIdentifier} for the received {@code Key}.
     *
     * @param key
     *            the key for generating the identifier
     * @return a {@code SubjectKeyIdentifier} for the received {@code Key}
     * @throws IOException
     *             if any problem occurs while reading the key
     */
    private final SubjectKeyIdentifier createSubjectKeyIdentifier(final Key key)
            throws IOException {
        final ASN1Sequence seq;        // Sequence for the key info
        ASN1InputStream stream = null; // Stream for reading the key

        try {
            stream = new ASN1InputStream(
                    new ByteArrayInputStream(key.getEncoded()));
            seq = (ASN1Sequence) stream.readObject();
        } finally {
            IOUtils.closeQuietly(stream);
        }

        return new BcX509ExtensionUtils()
                .createSubjectKeyIdentifier(new SubjectPublicKeyInfo(seq));
    }

    /**
     * Returns a {@code Certificate} with the received data.
     *
     * @param keypair
     *            key pair for the certificate
     * @param issuer
     *            issuer for the certificate
     * @return a {@code Certificate} with the received data
     * @throws IOException
     *             if there is an I/O or format problem with the certificate
     *             data
     * @throws OperatorCreationException
     *             if there was a problem creation a bouncy castle operator
     * @throws CertificateException
     *             if any of the certificates in the keystore could not be
     *             loaded
     * @throws InvalidKeyException
     *             if there was a problem with the key
     * @throws NoSuchAlgorithmException
     *             if an algorithm required to create the key store could not be
     *             found
     * @throws NoSuchProviderException
     *             if a required provider is missing
     * @throws SignatureException
     *             if any problem occurs while signing the certificate
     */
    private final Certificate getCertificate(final KeyPair keypair,
            final String issuer) throws IOException, OperatorCreationException,
            CertificateException, InvalidKeyException, NoSuchAlgorithmException,
            NoSuchProviderException, SignatureException {
        final X509v3CertificateBuilder builder; // Certificate builder
        final X509Certificate certificate;      // Certificate

        builder = getCertificateBuilder(keypair.getPublic(), issuer);

        certificate = getSignedCertificate(builder, keypair.getPrivate());

        certificate.checkValidity(new Date());
        certificate.verify(keypair.getPublic());

        LOGGER.debug(String.format(
                "Created certificate of type %s with encoded value %s",
                certificate.getType(),
                Arrays.asList(certificate.getEncoded())));
        LOGGER.debug(String.format("Created certificate with public key:%n%s",
                certificate.getPublicKey()));

        return certificate;
    }

    /**
     * Returns a certificate builder.
     *
     * @param publicKey
     *            public key for the certificate builder
     * @param issuer
     *            issuer for the certificate builder
     * @return a certificate builder
     * @throws IOException
     *             if any format error occurrs while creating the certificate
     */
    private final X509v3CertificateBuilder getCertificateBuilder(
            final PublicKey publicKey, final String issuer) throws IOException {
        final X500Name issuerName;              // Issuer name
        final X500Name subjectName;             // Subject name
        final BigInteger serial;                // Serial number
        final X509v3CertificateBuilder builder; // Certificate builder
        final Date start;                       // Certificate start date
        final Date end;                         // Certificate end date
        final KeyUsage usage;                   // Key usage
        final ASN1EncodableVector purposes;     // Certificate purposes

        issuerName = new X500Name(issuer);
        subjectName = issuerName;
        serial = BigInteger.valueOf(new Random().nextInt());

        start = new Date(System.currentTimeMillis() - 86400000L * 365);
        end = new Date(System.currentTimeMillis() + 86400000L * 365 * 100);

        builder = new JcaX509v3CertificateBuilder(issuerName, serial, start,
                end, subjectName, publicKey);

        builder.addExtension(Extension.subjectKeyIdentifier, false,
                createSubjectKeyIdentifier(publicKey));
        builder.addExtension(Extension.basicConstraints, true,
                new BasicConstraints(true));

        usage = new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature
                | KeyUsage.keyEncipherment | KeyUsage.dataEncipherment
                | KeyUsage.cRLSign);
        builder.addExtension(Extension.keyUsage, false, usage);

        purposes = new ASN1EncodableVector();
        purposes.add(KeyPurposeId.id_kp_serverAuth);
        purposes.add(KeyPurposeId.id_kp_clientAuth);
        purposes.add(KeyPurposeId.anyExtendedKeyUsage);
        builder.addExtension(Extension.extendedKeyUsage, false,
                new DERSequence(purposes));

        return builder;

    }

    /**
     * Creates a key pair.
     *
     * @return the key pair
     * @throws NoSuchAlgorithmException
     *             if the required algorithm for the key pair does not exist
     */
    private final KeyPair getKeyPair() throws NoSuchAlgorithmException {
        final KeyPairGenerator keyPairGenerator; // Key pair generator
        final KeyPair keypair;                   // Key pair

        keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024, new SecureRandom());

        keypair = keyPairGenerator.generateKeyPair();

        LOGGER.debug(String.format(
                "Created key pair with private key %3$s %1$s and public key %4$s %2$s",
                Arrays.asList(keypair.getPrivate().getEncoded()),
                Arrays.asList(keypair.getPublic().getEncoded()),
                keypair.getPrivate().getAlgorithm(),
                keypair.getPublic().getAlgorithm()));

        return keypair;
    }

    /**
     * Generates a default JKS key store.
     *
     * @param password
     *            the password for the key store
     * @return the JKS key store
     * @throws NoSuchAlgorithmException
     *             if an algorithm required to create the key store could not be
     *             found
     * @throws CertificateException
     *             if any of the certificates in the keystore could not be
     *             loaded
     * @throws IOException
     *             if there is an I/O or format problem with the key store data
     * @throws KeyStoreException
     *             if a key store related error ocurred
     */
    private final KeyStore getKeystore(final String password)
            throws NoSuchAlgorithmException, CertificateException, IOException,
            KeyStoreException {
        return getKeystore(password, KeyStore.getDefaultType());
    }

    /**
     * Generates a key store of the specified type.
     *
     * @param password
     *            the password for the key store
     * @param type
     *            the type of the key store
     * @return a key store of the specified type
     * @throws NoSuchAlgorithmException
     *             if an algorithm required to create the key store could not be
     *             found
     * @throws CertificateException
     *             if any of the certificates in the keystore could not be
     *             loaded
     * @throws IOException
     *             if there is an I/O or format problem with the key store data
     * @throws KeyStoreException
     *             if a key store related error ocurred
     */
    private final KeyStore getKeystore(final String password, final String type)
            throws NoSuchAlgorithmException, CertificateException, IOException,
            KeyStoreException {
        final KeyStore kstore; // The returned key store
        final char[] pass;     // The key store password

        kstore = KeyStore.getInstance(type);

        pass = password.toCharArray();
        kstore.load(null, pass);

        LOGGER.debug(String.format("Created %s key store with password %s",
                type, password));

        return kstore;
    }

    /**
     * Returns a signed certificate.
     *
     * @param builder
     *            builder to create the certificate
     * @param key
     *            private key for the certificate
     * @return a signed certificate
     * @throws OperatorCreationException
     *             if there was a problem creation a bouncy castle operator
     * @throws CertificateException
     *             if any of the certificates in the keystore could not be
     *             loaded
     */
    private final X509Certificate getSignedCertificate(
            final X509v3CertificateBuilder builder, final PrivateKey key)
            throws OperatorCreationException, CertificateException {
        final ContentSigner signer;   // Content signer
        final String provider;        // Provider
        final X509Certificate signed; // Signed certificate

        provider = BouncyCastleProvider.PROVIDER_NAME;
        signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .setProvider(provider).build(key);

        signed = new JcaX509CertificateConverter().setProvider(provider)
                .getCertificate(builder.build(signer));

        LOGGER.debug(String.format(
                "Signed certificate with %1$s private key %3$s, using algorithm %2$s",
                key.getAlgorithm(), key.getFormat(),
                Arrays.asList(key.getEncoded())));

        return signed;
    }

}
