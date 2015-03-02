package org.kontalk.crypto;


import org.spongycastle.asn1.x509.AlgorithmIdentifier;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.Signer;
import org.spongycastle.crypto.signers.ECDSASigner;
import org.spongycastle.operator.OperatorCreationException;
import org.spongycastle.operator.bc.BcContentSignerBuilder;


public class BcECDSAContentSignerBuilder
    extends BcContentSignerBuilder
{
    public BcECDSAContentSignerBuilder(AlgorithmIdentifier sigAlgId, AlgorithmIdentifier digAlgId)
    {
        super(sigAlgId, digAlgId);
    }

    protected Signer createSigner(AlgorithmIdentifier sigAlgId, AlgorithmIdentifier digAlgId)
        throws OperatorCreationException
    {
        Digest dig = digestProvider.get(digAlgId);

        return new ECDSADigestSigner(new ECDSASigner(), dig);
    }
}
