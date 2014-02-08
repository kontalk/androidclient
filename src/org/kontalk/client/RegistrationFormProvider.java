/**
 * $RCSfile$
 * $Revision: 2407 $
 * $Date: 2004-11-02 15:37:00 -0800 (Tue, 02 Nov 2004) $
 *
 * Copyright 2003-2007 Jive Software.
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
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

package org.kontalk.client;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Registration;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.xmlpull.v1.XmlPullParser;

/**
 * TEST
 */
public class RegistrationFormProvider implements IQProvider {

    public IQ parseIQ(XmlPullParser parser) throws Exception {
        Registration reg = new Registration();
        String namespace = parser.getNamespace();
        boolean done = false;

        while (!done)
        {
            int eventType = parser.next();

            if (eventType == XmlPullParser.START_TAG)
            {
                PacketExtension ext = PacketParserUtils.parsePacketExtension(parser.getName(), namespace, parser);

                if (ext != null)
                {
                    reg.addExtension(ext);
                }
            }
            else if (eventType == XmlPullParser.END_TAG)
            {
                if (parser.getName().equals("query"))
                {
                    done = true;
                }
            }
        }
        return reg;
    }
}
