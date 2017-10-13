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

package org.kontalk.ui.view;

import android.net.Uri;

import org.kontalk.message.MessageComponent;


/**
 * Listeners for the composer bar.
 * @author Daniele Ricci
 */
public interface ComposerListener {

    /** Sends a text message. */
    void sendTextMessage(String message);

    void sendLocationMessage(String message, double lat, double lon, String geoText, String geoStreet);

    /** Sends a binary message. */
    void sendBinaryMessage(Uri uri, String mime, boolean media,
        Class<? extends MessageComponent<?>> klass);

    /**
     * Sends a typing notification.
     * @return true if the notification was sent
     */
    boolean sendTyping();

    /** Asks the parent to stop all sounds. */
    void stopAllSounds();

    /** Text has been changed. */
    void textChanged(CharSequence text);

}
