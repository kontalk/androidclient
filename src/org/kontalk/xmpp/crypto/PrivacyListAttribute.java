package org.kontalk.xmpp.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

import org.spongycastle.bcpg.UserAttributeSubpacket;


/**
 * A user attribute for storing a privacy list.<br>
 * Byte structure:
 * <ul>
 * <li>1 byte: header length</li>
 * <li>1 byte: version (1)</li>
 * <li>1 byte: type (whitelist=1, blacklist=2)</li>
 * <li>13 bytes: reserved (all zero)</li>
 * <li>rest of data: zero-separated list of UTF-8 items</li>
 * </ul>
 * @author Daniele Ricci
 */
public class PrivacyListAttribute extends UserAttributeSubpacket {

	/**
	 * User attribute type number.<br>
	 * From RFC 4880: "Subpacket types 100 through 110 are reserved for private
	 * or experimental use."
	 * However, we decide that values ranging from 40 to 49 are reserved for XMPP.
	 * 43 is for privacy lists.
	 */
	public static final int ATTRIBUTE_TYPE = 43;

	/** List type: whitelist. */
	public static final int WHITELIST = 1;
	/** List type: blacklist. */
	public static final int BLACKLIST = 2;

	private static final byte[] ZEROES = new byte[13];

    private int hdrLength;
    private int version;
    private int listType;
    private Collection<String> list;

    public PrivacyListAttribute(
        byte[]    data)
    {
        super(ATTRIBUTE_TYPE, data);
        header(data);

        byte[] listData = new byte[data.length - hdrLength];
        System.arraycopy(data, hdrLength, listData, 0, listData.length);

        list = new LinkedList<String>();
        StringBuilder b = null;
        for (int i = 0; i < listData.length; i++) {
        	if (b == null)
        		b = new StringBuilder();

        	if (listData[i] == 0 && b.length() > 0) {
        		list.add(b.toString());
        		b.delete(0, b.length());
        	}
        	else {
        		b.append((char) (listData[i] & 0xff));
        	}
        }
    }

    public PrivacyListAttribute(
        int listType,
        Collection<String> list)
    {
        super(ATTRIBUTE_TYPE, toByteArray(listType, list));
        header(data);

        this.list = list;
    }

    private void header(byte[] data) {
        hdrLength = data[0] & 0xff;
        version = data[1] & 0xff;
        listType = data[2] & 0xff;
    }

    private static byte[] toByteArray(int listType, Collection<String> list)
    {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();

        try
        {
            bOut.write(0x10); bOut.write(0x01);
            bOut.write(listType);
            bOut.write(ZEROES);

            for (String item : list) {
            	bOut.write(item.getBytes());
            	bOut.write(0);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("unable to encode to byte array!");
        }

        return bOut.toByteArray();
    }

    public int version()
    {
        return version;
    }

    public int getListType()
    {
        return listType;
    }

    public Collection<String> getList()
    {
        return list;
    }


}
