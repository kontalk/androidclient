package org.kontalk.ui.view;

import org.kontalk.R;
import org.kontalk.provider.MyMessages;


/**
 * Theme based on Google Hangouts :)
 * @author Daniele Ricci
 */
public class HangoutMessageTheme extends AvatarMessageTheme {

    private final int mDirection;

    public HangoutMessageTheme(int direction) {
        super(direction == MyMessages.Messages.DIRECTION_IN ?
            R.layout.balloon_avatar_in : R.layout.balloon_avatar_out,
            direction == MyMessages.Messages.DIRECTION_IN ?
                R.drawable.balloon_hangout_incoming :
                R.drawable.balloon_hangout_outgoing, true);
        mDirection = direction;
    }

    @Override
    protected void setView(boolean sameMessageBlock) {
        if (sameMessageBlock) {
            if (mBalloonView != null) {
                int drawable = mDirection == MyMessages.Messages.DIRECTION_IN ?
                    R.drawable.balloon_hangout_block_incoming :
                    // TODO block balloon
                    R.drawable.balloon_hangout_outgoing;
                mBalloonView.setBackgroundResource(drawable);
            }
        }
        else {
            super.setView(false);
        }
    }
}
