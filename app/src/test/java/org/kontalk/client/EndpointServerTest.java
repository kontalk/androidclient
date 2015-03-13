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
        s1 = new EndpointServer("beta.kontalk.net");
        s2 = new EndpointServer("beta.kontalk.net");
        assertTrue(s1.equals(s2));
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
}
