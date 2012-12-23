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

import org.kontalk.xmpp.R;

import android.content.Context;
import android.util.SparseIntArray;


/** Emoji mappings. */
public final class Emoji {

    // FIXME this will be adjusted when all emojis will be added
    private static final int EMOJI_COUNT = 846;

    /** Mappings from Unicode to drawables. */
    public static final SparseIntArray emojiTheme = new SparseIntArray(EMOJI_COUNT);
    static {
        // GROUP 1: Smiley (http://punchdrunker.github.com/iOSEmoji/table_html/index.html)
        emojiTheme.put(0x1F604, R.drawable.emoji_smile);
        emojiTheme.put(0x1F60A, R.drawable.emoji_blush);
        emojiTheme.put(0x1F603, R.drawable.emoji_smiley);
        emojiTheme.put(0x263A, R.drawable.emoji_relaxed);
        emojiTheme.put(0x1F609, R.drawable.emoji_wink);
        emojiTheme.put(0x1F60D, R.drawable.emoji_heart_eyes);
        emojiTheme.put(0x1F618, R.drawable.emoji_kissing_heart);
        emojiTheme.put(0x1F61A, R.drawable.emoji_kissing_face);
        emojiTheme.put(0x1F633, R.drawable.emoji_flushed);
        emojiTheme.put(0x1F60C, R.drawable.emoji_satisfied);
        emojiTheme.put(0x1F601, R.drawable.emoji_grin);
        emojiTheme.put(0x1F61C, R.drawable.emoji_wink2);
        emojiTheme.put(0x1F61D, R.drawable.emoji_tongue);
        emojiTheme.put(0x1F612, R.drawable.emoji_unamused);
        emojiTheme.put(0x1F60F, R.drawable.emoji_smirk);
        emojiTheme.put(0x1F613, R.drawable.emoji_sweat);
        emojiTheme.put(0x1F614, R.drawable.emoji_pensive);
        emojiTheme.put(0x1F61E, R.drawable.emoji_disappointed);
        emojiTheme.put(0x1F616, R.drawable.emoji_confounded);
        emojiTheme.put(0x1F625, R.drawable.emoji_relieved);
        emojiTheme.put(0x1F630, R.drawable.emoji_cold_sweat);
        emojiTheme.put(0x1F628, R.drawable.emoji_fearful);
        emojiTheme.put(0x1F623, R.drawable.emoji_persevere);
        emojiTheme.put(0x1F622, R.drawable.emoji_cry);
        emojiTheme.put(0x1F62D, R.drawable.emoji_sob);
        emojiTheme.put(0x1F602, R.drawable.emoji_joy);
        emojiTheme.put(0x1F632, R.drawable.emoji_astonished);
        emojiTheme.put(0x1F631, R.drawable.emoji_scream);
        emojiTheme.put(0x1F620, R.drawable.emoji_angry);
        emojiTheme.put(0x1F621, R.drawable.emoji_rage);
        emojiTheme.put(0x1F62A, R.drawable.emoji_sleepy);
        emojiTheme.put(0x1F637, R.drawable.emoji_mask);
        emojiTheme.put(0x1F47F, R.drawable.emoji_imp);
        emojiTheme.put(0x1F47D, R.drawable.emoji_alien);
        emojiTheme.put(0x1F49B, R.drawable.emoji_yellow_heart);
        emojiTheme.put(0x1F499, R.drawable.emoji_blue_heart);
        emojiTheme.put(0x1F49C, R.drawable.emoji_purple_heart);
        emojiTheme.put(0x1F497, R.drawable.emoji_heartpulse);
        emojiTheme.put(0x1F49A, R.drawable.emoji_green_heart);
        emojiTheme.put(0x2764, R.drawable.emoji_heart);
        emojiTheme.put(0x1F494, R.drawable.emoji_broken_heart);
        emojiTheme.put(0x1F493, R.drawable.emoji_heartbeat);
        emojiTheme.put(0x1F498, R.drawable.emoji_cupid);
        emojiTheme.put(0x2728, R.drawable.emoji_sparkles);
        emojiTheme.put(0x1F31F, R.drawable.emoji_star2);
        emojiTheme.put(0x1F4A2, R.drawable.emoji_anger);
        emojiTheme.put(0x2755, R.drawable.emoji_grey_exclamation);
        emojiTheme.put(0x2754, R.drawable.emoji_grey_question);
        emojiTheme.put(0x1F4A4, R.drawable.emoji_zzz);
        emojiTheme.put(0x1F4A8, R.drawable.emoji_dash);
        emojiTheme.put(0x1F4A6, R.drawable.emoji_sweat_drops);
        emojiTheme.put(0x1F3B6, R.drawable.emoji_notes);
        emojiTheme.put(0x1F3B5, R.drawable.emoji_musical_note);
        emojiTheme.put(0x1F525, R.drawable.emoji_fire);
        emojiTheme.put(0x1F4A9, R.drawable.emoji_shit);
        emojiTheme.put(0x1F44D, R.drawable.emoji_thumbsup);
        emojiTheme.put(0x1F44E, R.drawable.emoji_thumbsdown);
        emojiTheme.put(0x1F44C, R.drawable.emoji_ok_hand);
        emojiTheme.put(0x1F44A, R.drawable.emoji_punch);
        emojiTheme.put(0x270A, R.drawable.emoji_fist);
        emojiTheme.put(0x270C, R.drawable.emoji_v);
        emojiTheme.put(0x1F44B, R.drawable.emoji_wave);
        emojiTheme.put(0x270B, R.drawable.emoji_hand);
        emojiTheme.put(0x1F450, R.drawable.emoji_open_hands);
        emojiTheme.put(0x1F446, R.drawable.emoji_point_up_2);
        emojiTheme.put(0x1F447, R.drawable.emoji_point_down);
        emojiTheme.put(0x1F449, R.drawable.emoji_point_right);
        emojiTheme.put(0x1F448, R.drawable.emoji_point_left);
        emojiTheme.put(0x1F64C, R.drawable.emoji_raised_hands);
        emojiTheme.put(0x1F64F, R.drawable.emoji_pray);
        emojiTheme.put(0x261D, R.drawable.emoji_point_up);
        emojiTheme.put(0x1F44F, R.drawable.emoji_clap);
        emojiTheme.put(0x1F4AA, R.drawable.emoji_muscle);
        emojiTheme.put(0x1F6B6, R.drawable.emoji_walking);
        emojiTheme.put(0x1F3C3, R.drawable.emoji_running);
        emojiTheme.put(0x1F46B, R.drawable.emoji_couple);
        emojiTheme.put(0x1F483, R.drawable.emoji_dancer);
        emojiTheme.put(0x1F46F, R.drawable.emoji_dancers);
        emojiTheme.put(0x1F646, R.drawable.emoji_ok_woman);
        emojiTheme.put(0x1F645, R.drawable.emoji_no_good);
        emojiTheme.put(0x1F481, R.drawable.emoji_information_desk_person);
        emojiTheme.put(0x1F647, R.drawable.emoji_bow);
        emojiTheme.put(0x1F48F, R.drawable.emoji_couplekiss);
        emojiTheme.put(0x1F491, R.drawable.emoji_couple_with_heart);
        emojiTheme.put(0x1F486, R.drawable.emoji_massage);
        emojiTheme.put(0x1F487, R.drawable.emoji_haircut);
        emojiTheme.put(0x1F485, R.drawable.emoji_nail_care);
        emojiTheme.put(0x1F466, R.drawable.emoji_boy);
        emojiTheme.put(0x1F467, R.drawable.emoji_girl);
        emojiTheme.put(0x1F469, R.drawable.emoji_woman);
        emojiTheme.put(0x1F468, R.drawable.emoji_man);
        emojiTheme.put(0x1F476, R.drawable.emoji_baby);
        emojiTheme.put(0x1F475, R.drawable.emoji_older_woman);
        emojiTheme.put(0x1F474, R.drawable.emoji_older_man);
        emojiTheme.put(0x1F471, R.drawable.emoji_person_with_blond_hair);
        emojiTheme.put(0x1F472, R.drawable.emoji_man_with_gua_pi_mao);
        emojiTheme.put(0x1F473, R.drawable.emoji_man_with_turban);
        emojiTheme.put(0x1F477, R.drawable.emoji_construction_worker);
        emojiTheme.put(0x1F46E, R.drawable.emoji_cop);
        emojiTheme.put(0x1F47C, R.drawable.emoji_angel);
        emojiTheme.put(0x1F478, R.drawable.emoji_princess);
        emojiTheme.put(0x1F482, R.drawable.emoji_guardsman);
        emojiTheme.put(0x1F480, R.drawable.emoji_skull);
        emojiTheme.put(0x1F463, R.drawable.emoji_feet);
        emojiTheme.put(0x1F48B, R.drawable.emoji_kiss);
        emojiTheme.put(0x1F444, R.drawable.emoji_lips);
        emojiTheme.put(0x1F442, R.drawable.emoji_ear);
        emojiTheme.put(0x1F440, R.drawable.emoji_eyes);
        emojiTheme.put(0x1F443, R.drawable.emoji_nose);

        //emojiTheme.put(0x, R.drawable.emoji_);

        // UNSORTED
        emojiTheme.put(0x1F455, R.drawable.emoji_tshirt);
        emojiTheme.put(0x1F45F, R.drawable.emoji_shoe);
        emojiTheme.put(0x1F4F7, R.drawable.emoji_camera);
        emojiTheme.put(0x260E, R.drawable.emoji_telephone);
        emojiTheme.put(0x1F4F1, R.drawable.emoji_iphone);
        emojiTheme.put(0x1F4E0, R.drawable.emoji_fax);
        emojiTheme.put(0x1F4BB, R.drawable.emoji_computer);
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
        /* \ue001 */ R.drawable.emoji_boy,
        /* \ue002 */ R.drawable.emoji_girl,
        /* \ue003 */ R.drawable.emoji_kiss,
        /* \ue004 */ R.drawable.emoji_man,
        /* \ue005 */ R.drawable.emoji_woman,
        /* \ue006 */ R.drawable.emoji_tshirt,
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
        /* \ue056 */ R.drawable.emoji_blush,
        /* \ue057 */ R.drawable.emoji_smiley,
    };

    /** Emoji groups as displayed in UI. */
    public static final int[][] emojiGroups = {
        {
            0x1F604,
            0x1F60A,
            0x1F603,
            0x263A,
            0x1F609,
            0x1F60D,
            0x1F618,
            0x1F61A,
            0x1F633,
            0x1F60C,
            0x1F601,
            0x1F61C,
            0x1F61D,
            0x1F612,
            0x1F60F,
            0x1F613,
            0x1F614,
            0x1F61E,
            0x1F616,
            0x1F625,
            0x1F630,
            0x1F628,
            0x1F623,
            0x1F622,
            0x1F62D,
            0x1F602,
            0x1F632,
            0x1F631,
            0x1F620,
            0x1F621,
            0x1F62A,
            0x1F637,
            0x1F47F,
            0x1F47D,
            0x1F49B,
            0x1F499,
            0x1F49C,
            0x1F497,
            0x1F49A,
            0x2764,
            0x1F494,
            0x1F493,
            0x1F498,
            0x2728,
            0x1F31F,
            0x1F4A2,
            0x2755,
            0x2754,
            0x1F4A4,
            0x1F4A8,
            0x1F4A6,
            0x1F3B6,
            0x1F3B5,
            0x1F525,
            0x1F4A9,
            0x1F44D,
            0x1F44E,
            0x1F44C,
            0x1F44A,
            0x270A,
            0x270C,
            0x1F44B,
            0x270B,
            0x1F450,
            0x1F446,
            0x1F447,
            0x1F449,
            0x1F448,
            0x1F64C,
            0x1F64F,
            0x261D,
            0x1F44F,
            0x1F4AA,
            0x1F6B6,
            0x1F3C3,
            0x1F46B,
            0x1F483,
            0x1F46F,
            0x1F646,
            0x1F645,
            0x1F481,
            0x1F647,
            0x1F48F,
            0x1F491,
            0x1F486,
            0x1F487,
            0x1F485,
            0x1F466,
            0x1F467,
            0x1F469,
            0x1F468,
            0x1F476,
            0x1F475,
            0x1F474,
            0x1F471,
            0x1F472,
            0x1F473,
            0x1F477,
            0x1F46E,
            0x1F47C,
            0x1F478,
            0x1F482,
            0x1F480,
            0x1F463,
            0x1F48B,
            0x1F444,
            0x1F442,
            0x1F440,
            0x1F443,
        }
    };

    public static boolean isSoftBankEmoji(char c) {
        return ((c >> 12) == 0xe);
    }

    public static int getEmojiResource(Context context, int codePoint) {
        return emojiTheme.get(codePoint);
    }

    public static int getSoftbankEmojiResource(char c) {
        return softbankMap[c - 0xe000 - 1];
    }

}
