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

import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;

/**
 * Helper class for encrypting and decrypting payloads using arbitrary string passphrases.
 * This requires converting the passphrase into a byte array key.
 * This class also includes utilities for encoding that byte array in a string-safe way
 * for storage.
 *
 * @author Rodrigo Damazio
 */
public class Coder {

  static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";

  private final PassKey key;

  public Coder(PassKey key) {
    this.key = key;
  }

  public byte[] encrypt(byte[] unencrypted) throws GeneralSecurityException {
    return doCipher(unencrypted, Cipher.ENCRYPT_MODE);
  }

  public byte[] decrypt(byte[] encrypted) throws GeneralSecurityException {
    return doCipher(encrypted, Cipher.DECRYPT_MODE);
  }

  private byte[] doCipher(byte[] original, int mode) throws GeneralSecurityException {
    Cipher cipher = createCipher(mode);
    return cipher.doFinal(original);
  }

  public InputStream wrapInputStream(InputStream inputStream) throws GeneralSecurityException {
    return new CipherInputStream(inputStream, createCipher(Cipher.DECRYPT_MODE));
  }

  public OutputStream wrapOutputStream(OutputStream outputStream) throws GeneralSecurityException {
    return new CipherOutputStream(outputStream, createCipher(Cipher.ENCRYPT_MODE));
  }

  private Cipher createCipher(int mode) throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
    cipher.init(mode, key.getKeySpec(), new IvParameterSpec(key.getInitVector()));
    return cipher;
  }

  private static final int BLOCK_SIZE = 16;

  public static long getEncryptedLength(long decryptedLength) {
      long cipherLen = (decryptedLength/BLOCK_SIZE + 1) * BLOCK_SIZE;

      // if data is block-padded, add another block
      if (decryptedLength % BLOCK_SIZE == 0)
          cipherLen += BLOCK_SIZE;

      return cipherLen;
  }
}
