/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

import org.junit.Test;

import static org.junit.Assert.*;


public class EndpointServerTest {

    @Test
    public void testGetHost() throws Exception {
        EndpointServer s;
        s = new EndpointServer("beta.kontalk.net");
        assertNull(s.getHost());
        s = new EndpointServer("beta.kontalk.net|127.0.0.1");
        assertEquals("127.0.0.1", s.getHost());
        s = new EndpointServer("beta.kontalk.net|127.0.0.1:5999");
        assertEquals("127.0.0.1", s.getHost());
    }

    @Test
    public void testGetPort() throws Exception {
        EndpointServer s;
        s = new EndpointServer("beta.kontalk.net");
        assertEquals(EndpointServer.DEFAULT_PORT, s.getPort());
        s = new EndpointServer("beta.kontalk.net|127.0.0.1");
        assertEquals(EndpointServer.DEFAULT_PORT, s.getPort());
        s = new EndpointServer("beta.kontalk.net|127.0.0.1:5999");
        assertEquals(5999, s.getPort());
    }

    @Test
    public void testGetSecurePort() throws Exception {
        EndpointServer s;
        s = new EndpointServer("beta.kontalk.net");
        assertEquals(EndpointServer.DEFAULT_PORT+1, s.getSecurePort());
        s = new EndpointServer("beta.kontalk.net|127.0.0.1");
        assertEquals(EndpointServer.DEFAULT_PORT+1, s.getSecurePort());
        s = new EndpointServer("beta.kontalk.net|127.0.0.1:5999");
        assertEquals(5999+1, s.getSecurePort());
    }

    @Test
    public void testGetNetwork() throws Exception {
        EndpointServer s;
        s = new EndpointServer("beta.kontalk.net");
        assertEquals("beta.kontalk.net", s.getNetwork());
        s = new EndpointServer("beta.kontalk.net|127.0.0.1");
        assertEquals("beta.kontalk.net", s.getNetwork());
        s = new EndpointServer("beta.kontalk.net|127.0.0.1:5999");
        assertEquals("beta.kontalk.net", s.getNetwork());
    }

    @Test
    public void testEquals() throws Exception {
        EndpointServer s1, s2;
        s1 = new EndpointServer("beta.kontalk.net");
        s2 = new EndpointServer("beta.kontalk.net|127.0.0.1");
        assertFalse(s1.equals(s2));
        assertFalse(s2.equals(s1));
        s1 = new EndpointServer("beta.kontalk.net");
        s2 = new EndpointServer("beta.kontalk.net");
        assertTrue(s1.equals(s2));
        assertTrue(s2.equals(s1));
    }

    @Test
    public void testToString() throws Exception {
        EndpointServer s;
        s = new EndpointServer("beta.kontalk.net");
        assertEquals("beta.kontalk.net", s.toString());
        s = new EndpointServer("beta.kontalk.net|127.0.0.1");
        assertEquals("beta.kontalk.net|127.0.0.1", s.toString());
        s = new EndpointServer("beta.kontalk.net|127.0.0.1:5999");
        assertEquals("beta.kontalk.net|127.0.0.1:5999", s.toString());
    }

    @Test
    public void testValidate() throws Exception {
        assertTrue(EndpointServer.validate("beta.kontalk.net"));
        assertTrue(EndpointServer.validate("beta.kontalk.net|127.0.0.1"));
        assertTrue(EndpointServer.validate("beta.kontalk.net|127.0.0.1:5999"));
        assertFalse(EndpointServer.validate("beta.kontalk.net|:5999"));
        assertFalse(EndpointServer.validate("beta.kontalk.net|ping/test/host:5999"));
        assertFalse(EndpointServer.validate("prova:5999"));
    }

}
