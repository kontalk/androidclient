/**
 *
 * Copyright 2014 Florian Schmaus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package org.kontalk.util;

import java.util.LinkedList;
import java.util.List;

import org.jivesoftware.smack.initializer.SmackInitializer;
import org.jivesoftware.smack.util.DNSUtil;
import org.jivesoftware.smack.util.dns.DNSResolver;
import org.jivesoftware.smack.util.dns.SRVRecord;

import de.measite.minidns.Client;
import de.measite.minidns.DNSMessage;
import de.measite.minidns.Record;
import de.measite.minidns.Record.CLASS;
import de.measite.minidns.Record.TYPE;
import de.measite.minidns.record.SRV;


/**
 * This implementation uses the <a href="https://github.com/rtreffer/minidns/">minidns</a> implementation for
 * resolving DNS addresses.
 * Modified to not use a cache to be compatible with Android API level 8.
 */
public class NoCacheMiniDnsResolver implements SmackInitializer, DNSResolver {

    private static final NoCacheMiniDnsResolver instance = new NoCacheMiniDnsResolver();
    private final Client client;

    public NoCacheMiniDnsResolver() {
        client = new Client();
    }

    public static DNSResolver getInstance() {
        return instance;
    }

    @Override
    public List<SRVRecord> lookupSRVRecords(String name) {
        List<SRVRecord> res = new LinkedList<SRVRecord>();
        DNSMessage message = client.query(name, TYPE.SRV, CLASS.IN);
        if (message == null) {
            return res;
        }
        for (Record record : message.getAnswers()) {
            SRV srv = (SRV) record.getPayload();
            res.add(new SRVRecord(srv.getName(), srv.getPort(), srv.getPriority(), srv.getWeight()));
        }
        return res;
    }

    public static void setup() {
        DNSUtil.setDNSResolver(getInstance());
    }

    @Override
    public List<Exception> initialize() {
        setup();
        return null;
    }

}
