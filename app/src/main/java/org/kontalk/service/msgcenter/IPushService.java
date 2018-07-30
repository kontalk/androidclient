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

package org.kontalk.service.msgcenter;


import java.util.concurrent.TimeUnit;

/**
 * Interface for a push client service.
 * @author Daniele Ricci
 */
public interface IPushService {

    /**
     * Default lifespan (7 days) of the {@link #isRegisteredOnServer()}
     * flag until it is considered expired.
     */
    public static final long DEFAULT_ON_SERVER_LIFESPAN_MS = TimeUnit.DAYS.toMillis(7);

    /** Begin the registration process to the push service. */
    public void register(IPushListener listener, String senderId);

    /** Begin the unregistration process from the push service. */
    public void unregister(IPushListener listener);

    /** Retry last (un)registration attempt. */
    public void retry();

    /** Returns true if the device is registered with the push service. */
    public boolean isRegistered();

    /** Returns true if the push service is available on the device. */
    public boolean isServiceAvailable();

    /**
     * Sets whether the device was successfully registered in the server side.
     */
    public void setRegisteredOnServer(boolean flag);

    /**
     * Checks whether the device was successfully registered in the server side,
     * as set by {@link #setRegisteredOnServer(boolean)}.
     *
     * <p>To avoid the scenario where the device sends the registration to the
     * server but the server loses it, this flag has an expiration date, which
     * is {@link #DEFAULT_ON_SERVER_LIFESPAN_MS} by default (but can be changed
     * by {@link #setRegisterOnServerLifespan(long)}).
     */
    public boolean isRegisteredOnServer();

    /**
     * Gets how long (in milliseconds) the {@link #isRegistered()}
     * property is valid.
     *
     * @return value set by {@link #setRegisteredOnServer(boolean)} or
     *      {@link #DEFAULT_ON_SERVER_LIFESPAN_MS} if not set.
     */
    public long getRegisterOnServerLifespan();

    /**
     * Sets how long (in milliseconds) the {@link #isRegistered()}
     * flag is valid.
     */
    public void setRegisterOnServerLifespan(long lifespan);

    public String getRegistrationId();

}
