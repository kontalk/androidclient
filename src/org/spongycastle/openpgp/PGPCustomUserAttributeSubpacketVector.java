package org.spongycastle.openpgp;

import org.kontalk.xmpp.crypto.PrivacyListAttribute;
import org.spongycastle.bcpg.UserAttributeSubpacket;

public class PGPCustomUserAttributeSubpacketVector
		extends PGPUserAttributeSubpacketVector {

	public PGPCustomUserAttributeSubpacketVector(UserAttributeSubpacket[] packets) {
		super(packets);
	}

    public PrivacyListAttribute getPrivacyListAttribute()
    {
        UserAttributeSubpacket    p = this.getSubpacket(PrivacyListAttribute.ATTRIBUTE_TYPE);

        if (p == null)
        {
            return null;
        }

        return (PrivacyListAttribute)p;
    }

}
