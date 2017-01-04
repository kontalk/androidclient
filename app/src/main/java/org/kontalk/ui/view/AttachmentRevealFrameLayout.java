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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import io.codetail.animation.SupportAnimator;
import io.codetail.animation.ViewAnimationUtils;
import io.codetail.widget.RevealFrameLayout;

import org.kontalk.R;


/**
 * Composite view for the attachment reveal frame layout.
 * Created mainly for handling animators concurrency. It seems like an overkill,
 * but I was getting crazy with race conditions. If someone has a better idea,
 * please propose a patch.
 * @author Daniele Ricci
 */
public class AttachmentRevealFrameLayout extends RevealFrameLayout {

    private View mContent;
    private SupportAnimator mHideAnimator;

    public AttachmentRevealFrameLayout(Context context) {
        super(context);
    }

    public AttachmentRevealFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public AttachmentRevealFrameLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.circular_card);
    }

    public void show() {
        // stop all animations!
        mContent.clearAnimation();

        setVisibility(VISIBLE);
        int right = mContent.getRight();
        int top = mContent.getTop();
        float f = (float) Math.sqrt(Math.pow(mContent.getWidth(), 2D) + Math.pow(mContent.getHeight(), 2D));
        SupportAnimator showAnimator = ViewAnimationUtils.createCircularReveal(mContent, right, top, 0, f);
        setAnimatorParams(showAnimator);
        showAnimator.addListener(new SupportAnimator.AnimatorListener() {
            @Override
            public void onAnimationStart() {
            }

            @Override
            public void onAnimationEnd() {
            }

            @Override
            public void onAnimationCancel() {
            }

            @Override
            public void onAnimationRepeat() {
            }
        });

        // create hide animation now
        mHideAnimator = showAnimator.reverse();
        setAnimatorParams(mHideAnimator);
        mHideAnimator.addListener(new SupportAnimator.AnimatorListener() {
            @Override
            public void onAnimationStart() {
            }

            @Override
            public void onAnimationEnd() {
                hideNow();
            }

            @Override
            public void onAnimationCancel() {
            }

            @Override
            public void onAnimationRepeat() {
            }
        });

        showAnimator.start();
    }

    public void hide() {
        hide(false);
    }

    public void hide(boolean instant) {
        // cancel show animation (if any)
        mContent.clearAnimation();

        if (instant) {
            hideNow();
        }
        else if (mHideAnimator != null && !mHideAnimator.isRunning()) {
            mHideAnimator.start();
        }
    }

    public void toggle() {
        if (getVisibility() == VISIBLE) {
            hide();
        }
        else {
            show();
        }
    }

    public boolean isClosing() {
        return mHideAnimator != null && mHideAnimator.isRunning();
    }

    void hideNow() {
        setVisibility(INVISIBLE);
        mHideAnimator = null;
    }

    private void setAnimatorParams(SupportAnimator anim) {
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.setDuration(250);
    }

}
