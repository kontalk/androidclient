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

package org.kontalk.ui.view.themes;

import org.kontalk.R;
import org.kontalk.ui.view.MessageListItemTheme;
import org.kontalk.ui.view.MessageListItemThemeFactory;


/**
 * Classic (old) theme.
 * @author Daniele Ricci
 */
public class OldClassicMessageTheme extends SimpleMessageTheme {

    private OldClassicMessageTheme(int direction, boolean groupChat) {
         super(R.drawable.balloon_old_classic_incoming,
             R.drawable.balloon_old_classic_outgoing, groupChat,
             R.attr.chatThemeDayOnlyMessageTextColor, R.attr.chatThemeDayOnlyDateTextColor);
    }

    public static final class FactoryCreator implements MessageListItemThemeFactory.FactoryCreator {

        @Override
        public MessageListItemTheme create(int direction, boolean groupChat) {
            return new OldClassicMessageTheme(direction, groupChat);
        }

        @Override
        public boolean supportsLightTheme() {
            return true;
        }

        @Override
        public boolean supportsDarkTheme() {
            return false;
        }
    }

}
