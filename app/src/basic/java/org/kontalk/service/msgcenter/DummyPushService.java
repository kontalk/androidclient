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

package org.kontalk.service.msgcenter;

import android.content.Context;


/**
 * Dummy push client service.
 * @author Daniele Ricci
 */
public class DummyPushService implements IPushService {

    public DummyPushService(Context context) {
    }

    @Override
    public void register(IPushListener listener, String senderId) {
    }

    @Override
    public void unregister(IPushListener listener) {
    }

    @Override
    public void retry() {
    }

    @Override
    public boolean isRegistered() {
        return false;
    }

    @Override
    public boolean isServiceAvailable() {
        return false;
    }

    @Override
    public void setRegisteredOnServer(boolean flag) {
    }

    @Override
    public boolean isRegisteredOnServer() {
        return false;
    }

    @Override
    public long getRegisterOnServerLifespan() {
        return 0;
    }

    @Override
    public void setRegisterOnServerLifespan(long lifespan) {
    }

    @Override
    public String getRegistrationId() {
        return null;
    }

}
