/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.message;

import org.kontalk.crypto.Coder;


/**
 * Plain text message component (always cleartext).
 * @author Daniele Ricci
 */
public class TextComponent extends MessageComponent<String> {

    public static final String MIME_TYPE = "text/plain";

    public TextComponent(String text) {
        super(text, text.length(), false, Coder.SECURITY_CLEARTEXT);
    }

    public static boolean supportsMimeType(String mime) {
        return MIME_TYPE.equalsIgnoreCase(mime);
    }


}
