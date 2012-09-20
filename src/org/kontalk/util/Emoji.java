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

import android.content.Context;


/** Emoji mappings. */
public final class Emoji {
    public static final int[] emojiTheme = new int[] {
        // TEST a simple group
        0x1f466,
        0x1f603,
    };

    private static final int[] softbankMap = new int[] {
        /* \ue001 */ 0x1f466,
    };

    private static final int[] oldSoftbankMap = new int[] {
        /* \ue002 */ R.drawable.emoji_girl,
        /* \ue003 */ R.drawable.emoji_kiss,
        /* \ue004 */ R.drawable.emoji_man,
        /* \ue005 */ R.drawable.emoji_woman,
        /* \ue006 */ R.drawable.emoji_shirt,
        /* \ue007 */ R.drawable.emoji_shoe,
        /* \ue008 */ R.drawable.emoji_camera,
        /* \ue009 */ R.drawable.emoji_telephone,
        /* \ue00a */ R.drawable.emoji_iphone,
        /* \ue00b */ R.drawable.emoji_fax,
        /* \ue00c */ R.drawable.emoji_computer,
        /* \ue00d */ R.drawable.emoji_punch,
        /* \ue00e */ R.drawable.emoji_thumbsup,
        /* \ue00f */ R.drawable.emoji_point_up,
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
        /* \ue01a */ R.drawable.emoji_horse,
        /* \ue01b */ R.drawable.emoji_car,
        /* \ue01c */ R.drawable.emoji_sailboat,
        /* \ue01d */ R.drawable.emoji_airplane,
        /* \ue01e */ R.drawable.emoji_train,
        /* \ue01f */ R.drawable.emoji_train2,
        /* \ue020 */ R.drawable.emoji_question,
        /* \ue021 */ R.drawable.emoji_exclamation,
        /* \ue022 */ R.drawable.emoji_heart,
        /* \ue023 */ R.drawable.emoji_broken_heart,
        /* \ue024 */ R.drawable.emoji_clock1,
        /* \ue025 */ R.drawable.emoji_clock2,
        /* \ue026 */ R.drawable.emoji_clock3,
        /* \ue027 */ R.drawable.emoji_clock4,
        /* \ue028 */ R.drawable.emoji_clock5,
        /* \ue029 */ R.drawable.emoji_clock6,
        /* \ue02a */ R.drawable.emoji_clock7,
        /* \ue02b */ R.drawable.emoji_clock8,
        /* \ue02c */ R.drawable.emoji_clock9,
        /* \ue02d */ R.drawable.emoji_clock10,
        /* \ue02e */ R.drawable.emoji_clock11,
        /* \ue02f */ R.drawable.emoji_clock12,
        /* \ue030 */ R.drawable.emoji_cherry_blossom,
        /* \ue031 */ R.drawable.emoji_trident,
        /* \ue032 */ R.drawable.emoji_rose,
        /* \ue033 */ R.drawable.emoji_christmas_tree,
        /* \ue034 */ R.drawable.emoji_ring,
        /* \ue035 */ R.drawable.emoji_gem,
        /* \ue036 */ R.drawable.emoji_house,
        /* \ue037 */ R.drawable.emoji_church,
        /* \ue038 */ R.drawable.emoji_office,
        /* \ue039 */ R.drawable.emoji_station,
        /* \ue03a */ R.drawable.emoji_fuelpump,
        /* \ue03b */ R.drawable.emoji_mount_fuji,
        /* \ue03c */ R.drawable.emoji_microphone,
        /* \ue03d */ R.drawable.emoji_movie_camera,
        /* \ue03e */ R.drawable.emoji_musical_note,
        /* \ue03f */ R.drawable.emoji_key,
        /* \ue040 */ R.drawable.emoji_saxophone,
        /* \ue041 */ R.drawable.emoji_guitar,
        /* \ue042 */ R.drawable.emoji_trumpet,
        /* \ue043 */ R.drawable.emoji_fork_and_knife,
        /* \ue044 */ R.drawable.emoji_cocktail,
        /* \ue045 */ R.drawable.emoji_coffee,
        /* \ue046 */ R.drawable.emoji_cake,
        /* \ue047 */ R.drawable.emoji_beer,
        /* \ue048 */ R.drawable.emoji_snowman,
        /* \ue049 */ R.drawable.emoji_cloud,
        /* \ue04a */ R.drawable.emoji_sunny,
        /* \ue04b */ R.drawable.emoji_umbrella,
        /* \ue04c */ R.drawable.emoji_moon,
        /* \ue04d */ R.drawable.emoji_sunrise_over_mountains,
        /* \ue04e */ R.drawable.emoji_angel,
        /* \ue04f */ R.drawable.emoji_cat,
        /* \ue050 */ R.drawable.emoji_tiger,
        /* \ue051 */ R.drawable.emoji_bear,
        /* \ue052 */ R.drawable.emoji_dog,
        /* \ue053 */ R.drawable.emoji_mouse,
        /* \ue054 */ R.drawable.emoji_whale,
        /* \ue055 */ R.drawable.emoji_penguin,
    };

    public static boolean isSoftBankEmoji(char c) {
        return ((c >> 12) == 0xe);
    }

    public static int getEmojiResource(Context context, int codePoint) {
        return context.getResources().getIdentifier(String
            .format("emoji_%x", codePoint), "drawable", context.getPackageName());
    }

    public static int getSoftbankEmoji(char c) {
        return softbankMap[c - 0xe000 - 1];
    }

}
