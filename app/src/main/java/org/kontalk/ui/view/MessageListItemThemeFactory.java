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

package org.kontalk.ui.view;

import java.util.LinkedHashMap;
import java.util.Map;

import android.content.Context;

import org.kontalk.R;
import org.kontalk.ui.view.themes.ClassicMessageTheme;
import org.kontalk.ui.view.themes.HangoutMessageTheme;
import org.kontalk.ui.view.themes.IPhoneMessageTheme;
import org.kontalk.ui.view.themes.OldClassicMessageTheme;
import org.kontalk.ui.view.themes.SilenceMessageTheme;


/**
 * Factory to build balloon themes.
 * @author Daniele Ricci
 */
public class MessageListItemThemeFactory {

    public interface FactoryCreator {
        MessageListItemTheme create(int direction, boolean groupChat);

        // creating a theme involves creating theme instances possibly for nothing
        // so we put these here so we are able to make an early decision

        boolean supportsLightTheme();

        boolean supportsDarkTheme();
    }

    private static final Map<String, FactoryCreator> mThemes = new LinkedHashMap<>();

    static {
        mThemes.put("hangout", new HangoutMessageTheme.FactoryCreator());
        mThemes.put("silence", new SilenceMessageTheme.FactoryCreator());
        mThemes.put("classic", new ClassicMessageTheme.FactoryCreator());
        mThemes.put("old_classic", new OldClassicMessageTheme.FactoryCreator());
        mThemes.put("iphone", new IPhoneMessageTheme.FactoryCreator());
    }

    private MessageListItemThemeFactory() {
    }

    public static MessageListItemTheme createTheme(Context context, String theme,
            int direction, boolean event, boolean groupChat) {
        if (event) {
            return new EventMessageTheme(R.layout.balloon_event);
        }
        else {
            FactoryCreator factory = mThemes.get(theme);
            if (factory == null)
                throw new IllegalArgumentException("theme not found: " + theme);

            return factory.create(direction, groupChat);
        }
    }

    public static boolean isDayNightSupported(Context context, String theme) {
        FactoryCreator factory = mThemes.get(theme);
        if (factory == null)
            throw new IllegalArgumentException("theme not found: " + theme);

        return factory.supportsLightTheme() && factory.supportsDarkTheme();
    }
}
