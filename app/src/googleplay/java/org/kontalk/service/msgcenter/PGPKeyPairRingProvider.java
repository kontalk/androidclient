package org.kontalk.service.msgcenter;

import org.kontalk.crypto.PGP.PGPKeyPairRing;


/**
 * Interface for retrieving a key pair ring.
 * @author Daniele Ricci
 */
public interface PGPKeyPairRingProvider {

    public PGPKeyPairRing getKeyPair();
}
