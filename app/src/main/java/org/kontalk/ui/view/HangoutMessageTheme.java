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

import org.kontalk.R;
import org.kontalk.provider.MyMessages;


/**
 * Theme based on Google Hangouts :)
 * @author Daniele Ricci
 */
public class HangoutMessageTheme extends AvatarMessageTheme {

    private final int mDirection;

    public HangoutMessageTheme(int direction, boolean groupChat) {
        super(direction == MyMessages.Messages.DIRECTION_IN ?
            R.layout.balloon_avatar_in_top : R.layout.balloon_avatar_out,
            direction == MyMessages.Messages.DIRECTION_IN ?
                R.drawable.balloon_hangout_incoming :
                R.drawable.balloon_hangout_outgoing, true, groupChat);
        mDirection = direction;
    }

    @Override
    protected void setView(boolean sameMessageBlock) {
        if (sameMessageBlock) {
            if (mBalloonView != null) {
                int drawable = mDirection == MyMessages.Messages.DIRECTION_IN ?
                    R.drawable.balloon_hangout_block_incoming :
                    R.drawable.balloon_hangout_block_outgoing;
                mBalloonView.setBackgroundResource(drawable);
            }
        }
        else {
            super.setView(false);
        }
    }
}
