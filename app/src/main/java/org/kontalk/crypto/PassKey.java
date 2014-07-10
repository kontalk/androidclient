/*
 * Copyright 2011 Rodrigo Damazio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kontalk.crypto;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.spec.SecretKeySpec;

public class PassKey {
    private static final String HASH_ALGORITHM = "MD5";
    private static final String ENCRYPTION_KEY_TYPE = "AES";

    private final byte[] keyBytes;
    private byte[] iv;
    private SecretKeySpec keySpec;

    public PassKey(String passPhrase) {
        this(passPhrase, 3);
    }

    public PassKey(String passPhrase, int numHashes) {
        // Use an MD5 to generate an arbitrary initialization vector
        this.keyBytes = passPhraseToKey(passPhrase, HASH_ALGORITHM, numHashes);
    }

    public PassKey(byte[] keyBytes) {
        this.keyBytes = keyBytes;
    }

    public byte[] getKeyBytes() {
        return keyBytes;
    }

    public SecretKeySpec getKeySpec() {
        if (keySpec == null) {
            keySpec = new SecretKeySpec(keyBytes, ENCRYPTION_KEY_TYPE);
        }
        return keySpec;
    }

    public byte[] getInitVector() {
        if (iv == null) {
            iv = doDigest(keyBytes, "MD5");
        }
        return iv;
    }

    /**
     * Converts a user-entered pass phrase into a hashed binary value which is
     * used as the encryption key.
     */
    private byte[] passPhraseToKey(String passphrase, String hashAlgorithm, int numHashes) {
        if (numHashes < 1) {
            throw new IllegalArgumentException("Need a positive hash count");
        }

        byte[] passPhraseBytes;
        try {
            passPhraseBytes = passphrase.getBytes("UTF8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Bad passphrase encoding", e);
        }

        // Hash it multiple times to keep the paranoid people happy :)
        byte[] keyBytes = passPhraseBytes;
        for (int i = 0; i < numHashes; i++) {
            keyBytes = doDigest(keyBytes, hashAlgorithm);
        }

        return keyBytes;
    }


    private byte[] doDigest(byte[] data, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            md.update(data);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm not available", e);
        }
    }
}
