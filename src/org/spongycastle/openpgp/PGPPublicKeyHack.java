package org.spongycastle.openpgp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;


/**
 * This is just a ugly hack to access some package fields in PGPPublicKey.
 * @author Daniele Ricci
 */
public final class PGPPublicKeyHack {

	private PGPPublicKeyHack() {}

    public static List<Iterator<PGPSignature>> getSignaturesForUserAttribute(
    		PGPPublicKey					   publicKey,
            PGPUserAttributeSubpacketVector    userAttributes)
        {
    		List<Iterator<PGPSignature>> list = new LinkedList<Iterator<PGPSignature>>();

            for (int i = 0; i != publicKey.ids.size(); i++)
            {
                if (userAttributes.equals(publicKey.ids.get(i)))
                {
                    list.add(((ArrayList)publicKey.idSigs.get(i)).iterator());
                }
            }

            return list;
        }


}
