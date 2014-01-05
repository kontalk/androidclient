package org.spongycastle.openpgp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.kontalk.xmpp.crypto.PrivacyListAttribute;
import org.spongycastle.bcpg.UserAttributeSubpacket;
import org.spongycastle.bcpg.attr.ImageAttribute;


public class PGPCustomUserAttributeSubpacketVectorGenerator
		extends PGPUserAttributeSubpacketVectorGenerator {

    private List<UserAttributeSubpacket> list = new ArrayList<UserAttributeSubpacket>();

    public void setImageAttribute(int imageType, byte[] imageData)
    {
        if (imageData == null)
        {
            throw new IllegalArgumentException("attempt to set null image");
        }

        list.add(new ImageAttribute(imageType, imageData));
    }

    public void setPrivacyListAttribute(int listType, Collection<String> listData)
    {
        if (list == null)
        {
            throw new IllegalArgumentException("attempt to set null image");
        }

        list.add(new PrivacyListAttribute(listType, listData));
    }

    public PGPCustomUserAttributeSubpacketVector generate()
    {
        return new PGPCustomUserAttributeSubpacketVector(list.toArray(new UserAttributeSubpacket[list.size()]));
    }

}
