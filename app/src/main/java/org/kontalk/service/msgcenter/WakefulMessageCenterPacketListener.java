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

package org.kontalk.service.msgcenter;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.Stanza;

import android.annotation.SuppressLint;
import android.os.PowerManager;

import org.kontalk.Kontalk;
import org.kontalk.util.SystemUtils;


/**
 * Wakeful packet listener for the Message Center.
 * @author Daniele Ricci
 */
abstract class WakefulMessageCenterPacketListener extends MessageCenterPacketListener {

    private PowerManager.WakeLock mWakeLock;

    WakefulMessageCenterPacketListener(MessageCenterService instance, String suffix) {
        super(instance);
        mWakeLock = SystemUtils.createPartialWakeLock(instance,
            Kontalk.TAG + "-" + suffix, false);
    }

    protected abstract void processWakefulStanza(Stanza packet)
        throws SmackException.NotConnectedException, InterruptedException, SmackException.NotLoggedInException;

    @SuppressLint("WakelockTimeout")
    @Override
    public void processStanza(Stanza packet) throws SmackException.NotConnectedException, InterruptedException, SmackException.NotLoggedInException {
        mWakeLock.acquire();
        try {
            processWakefulStanza(packet);
        }
        finally {
            mWakeLock.release();
        }
    }
}
