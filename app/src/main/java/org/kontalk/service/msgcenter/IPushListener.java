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

import android.content.Context;


/**
 * Interface for GCM listeners (the Message Center in our case).
 * @author Daniele Ricci
 */
public interface IPushListener {

    public void onRegistered(Context context, String registrationId);

    public void onUnregistered(Context context);

    public void onError(Context context, String errorId);

    public String getSenderId(Context context);

}
