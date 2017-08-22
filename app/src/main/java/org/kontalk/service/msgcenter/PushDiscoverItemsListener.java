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

package org.kontalk.service.msgcenter;

import java.util.List;

import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.kontalk.util.Preferences;


/**
 * Packet listener for discovering push notifications support.
 * @author Daniele Ricci
 */
class PushDiscoverItemsListener extends MessageCenterPacketListener {

    public PushDiscoverItemsListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processStanza(Stanza packet) {
        // we don't need this listener anymore
        getConnection().removeAsyncStanzaListener(this);

        DiscoverItems query = (DiscoverItems) packet;
        List<DiscoverItems.Item> items = query.getItems();
        for (DiscoverItems.Item item : items) {
            String jid = item.getEntityID().toString();
            // google push notifications
            if (("gcm.push." + getServer().getNetwork()).equals(jid)) {
                String senderId = item.getNode();
                setPushSenderId(senderId);

                if (isPushNotificationsEnabled()) {
                    String oldSender = Preferences.getPushSenderId();

                    // store the new sender id
                    Preferences.setPushSenderId(senderId);

                    // begin a registration cycle if senderId is different
                    if (oldSender != null && !oldSender.equals(senderId)) {
                        IPushService service = PushServiceManager.getInstance(getContext());
                        if (service != null)
                            service.unregister(getPushListener());
                        // unregister will see this as an attempt to register again
                        startPushRegistrationCycle();
                    }
                    else {
                        // begin registration immediately
                        pushRegister();
                    }
                }
            }
        }
    }
}

