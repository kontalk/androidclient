/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.util;

import org.junit.BeforeClass;
import org.junit.Test;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;

import static org.junit.Assert.*;


public class CustomSimpleXmppStringprepTest {

    @BeforeClass
    public static void init() {
        CustomSimpleXmppStringprep.setup();
    }

    @Test
    public void testNoLowercase() throws Exception {
        Jid jid = JidCreate.from("8DCmeP5k6aV2PmiMgtaB@admin@prime.kontalk.net");
        assertEquals("8DCmeP5k6aV2PmiMgtaB@admin@prime.kontalk.net", jid.toString());
    }

}
