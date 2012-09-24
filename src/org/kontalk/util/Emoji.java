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
import android.util.SparseIntArray;


/** Emoji mappings. */
public final class Emoji {

    private static final int EMOJI_COUNT = 846;

    /** Mappings from Unicode to drawables. */
    public static final SparseIntArray emojiTheme = new SparseIntArray(EMOJI_COUNT);
    static {
        emojiTheme.put(0x1F603, R.drawable.emoji_smiley);
        emojiTheme.put(0x1F60A, R.drawable.emoji_blush);
        emojiTheme.put(0x1F466, R.drawable.emoji_boy);
        emojiTheme.put(0x1F467, R.drawable.emoji_girl);
        emojiTheme.put(0x1F48B, R.drawable.emoji_kiss);
        emojiTheme.put(0x1F468, R.drawable.emoji_man);
        emojiTheme.put(0x1F469, R.drawable.emoji_woman);
        emojiTheme.put(0x1F455, R.drawable.emoji_shirt);
        emojiTheme.put(0x1F45F, R.drawable.emoji_shoe);
        emojiTheme.put(0x1F4F7, R.drawable.emoji_camera);
        emojiTheme.put(0x260E, R.drawable.emoji_telephone);
        emojiTheme.put(0x1F4F1, R.drawable.emoji_iphone);
        emojiTheme.put(0x1F4E0, R.drawable.emoji_fax);
        emojiTheme.put(0x1F4BB, R.drawable.emoji_computer);
        emojiTheme.put(0x1F44A, R.drawable.emoji_punch);
        emojiTheme.put(0x1F44D, R.drawable.emoji_thumbsup);
        emojiTheme.put(0x261D, R.drawable.emoji_point_up);
        emojiTheme.put(0x270A, R.drawable.emoji_fist);
        emojiTheme.put(0x270C, R.drawable.emoji_v);
        emojiTheme.put(0x270B, R.drawable.emoji_hand);
        emojiTheme.put(0x1F3BF, R.drawable.emoji_ski);
        emojiTheme.put(0x26F3, R.drawable.emoji_golf);
        emojiTheme.put(0x1F3BE, R.drawable.emoji_tennis);
        emojiTheme.put(0x26BE, R.drawable.emoji_baseball);
        emojiTheme.put(0x1F3C4, R.drawable.emoji_surfer);
        emojiTheme.put(0x26BD, R.drawable.emoji_soccer);
        emojiTheme.put(0x1F41F, R.drawable.emoji_fish);
        emojiTheme.put(0x1F434, R.drawable.emoji_horse);
        emojiTheme.put(0x1F697, R.drawable.emoji_car);
        emojiTheme.put(0x26F5, R.drawable.emoji_sailboat);
        emojiTheme.put(0x2708, R.drawable.emoji_airplane);
        emojiTheme.put(0x1F683, R.drawable.emoji_train);
    }

    /** Mappings from SoftBank encoding to Unicode. */
    private static final int[] softbankMap = new int[] {
        /* \ue001 */ 0x1F466,
        /* \ue002 */ 0x1F467,
        /* \ue003 */ 0x1F48B,
        /* \ue004 */ 0x1F468,
        /* \ue005 */ 0x1F469,
        /* \ue006 */ 0x1F455,
        /* \ue007 */ 0x1F45F,
        /* \ue008 */ 0x1F4F7,
        /* \ue009 */ 0x260E,
        /* \ue00a */ 0x1F4F1,
        /* \ue00b */ 0x1F4E0,
        /* \ue00c */ 0x1F4BB,
        /* \ue00d */ 0x1F44A,
        /* \ue00e */ 0x1F44D,
        /* \ue00f */ 0x261D,
        /* \ue010 */ 0x270A,
        /* \ue011 */ 0x270C,
        /* \ue012 */ 0x270B,
        /* \ue013 */ 0x1F3BF,
        /* \ue014 */ 0x26F3,
        /* \ue015 */ 0x1F3BE,
        /* \ue016 */ 0x26BE,
        /* \ue017 */ 0x1F3C4,
        /* \ue018 */ 0x26BD,
        /* \ue019 */ 0x1F41F,
        /* \ue01a */ 0x1F434,
        /* \ue01b */ 0x1F697,
        /* \ue01c */ 0x26F5,
        /* \ue01d */ 0x2708,
        /* \ue01e */ 0x1F683,
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
        /* \ue056 */ 0x1f60a,
        /* \ue057 */ 0x1f604,
    };

    /** Emoji groups as displayed in UI. */
    public static final int[][] emojiGroups = {
        {
            0x1F603,
            0x1F60A,
            0x1F466,
            0x1F467,
            0x1F48B,
            0x1F468,
            0x1F469,
            0x1F455,
            0x1F45F,
            0x1F4F7,
            0x260E,
            0x1F4F1,
            0x1F4E0,
            0x1F4BB,
            0x1F44A,
            0x1F44D,
            0x261D,
            0x270A,
            0x270C,
            0x270B,
            0x1F3BF,
            0x26F3,
            0x1F3BE,
            0x26BE,
            0x1F3C4,
            0x26BD,
            0x1F41F,
            0x1F434,
            0x1F697,
            0x26F5,
            0x2708,
            0x1F683,
        }
    };

    public static boolean isSoftBankEmoji(char c) {
        return ((c >> 12) == 0xe);
    }

    public static int getEmojiResource(Context context, int codePoint) {
        return emojiTheme.get(codePoint);
    }

    public static int getSoftbankEmoji(char c) {
        return softbankMap[c - 0xe000 - 1];
    }

}
