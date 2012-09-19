/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.util;

import org.kontalk.R;


/** Emoji mappings. */
public final class Emoji {
    public static final int[] emojiMap = new int[] {
        /* \ue001 */ R.drawable.emoji_boy,
        /* \ue002 */ R.drawable.emoji_girl,
        /* \ue003 */ R.drawable.emoji_kiss,
        /* \ue004 */ R.drawable.emoji_man,
        /* \ue005 */ R.drawable.emoji_woman,
        /* \ue006 */ R.drawable.emoji_shirt,
        /* \ue007 */ R.drawable.emoji_shoe,
        /* \ue008 */ R.drawable.emoji_camera,
        /* \ue009 */ R.drawable.emoji_telephone,
        /* \ue00A */ R.drawable.emoji_iphone,
        /* \ue00B */ R.drawable.emoji_fax,
        /* \ue00C */ R.drawable.emoji_computer,
        /* \ue00D */ R.drawable.emoji_punch,
        /* \ue00E */ R.drawable.emoji_thumbsup,
        /* \ue00F */ R.drawable.emoji_point_up,
        /* \ue010 */ R.drawable.emoji_fist,
        /* \ue011 */ R.drawable.emoji_v,
        /* \ue012 */ R.drawable.emoji_hand,
        /* \ue013 */ R.drawable.emoji_ski,
        /* \ue014 */ R.drawable.emoji_golf,
        /* \ue015 */ R.drawable.emoji_tennis,
        /* \ue016 */ R.drawable.emoji_baseball,
        /* \ue017 */ R.drawable.emoji_surfer,
        /* \ue018 */ R.drawable.emoji_soccer,
        /* \ue019 */ R.drawable.emoji_fish,
        /* \ue01A */ R.drawable.emoji_horse,
        /* \ue01B */ R.drawable.emoji_car,
    };

    public static int getSmileyResourceId(int index) {
        return emojiMap[index - 1];
    }

}
