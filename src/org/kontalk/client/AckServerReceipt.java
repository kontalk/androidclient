/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.client;

import java.util.List;
import java.util.Map;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.EmbeddedExtensionProvider;


public class AckServerReceipt extends ServerReceipt {
    public static final String ELEMENT_NAME = "ack";

    public AckServerReceipt(String id) {
        super(ELEMENT_NAME, id);
    }

    @Override
    public String getElementName() {
        return ELEMENT_NAME;
    }

    public static final class Provider extends EmbeddedExtensionProvider {
        @Override
        protected PacketExtension createReturnExtension(String currentElement, String currentNamespace,
            Map<String, String> attributeMap, List<? extends PacketExtension> content) {
            return new AckServerReceipt(attributeMap.get("id"));
        }
    }

}
