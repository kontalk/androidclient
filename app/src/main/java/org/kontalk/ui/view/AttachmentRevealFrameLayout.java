/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

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

    private SupportAnimator mShowAnimator;
    boolean mFullyVisible;
    SupportAnimator mHideAnimator;

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
        // mAttachmentContainer = this
        mContent = findViewById(R.id.circular_card);
    }

    public void show() {
        if (mHideAnimator != null && mHideAnimator.isRunning()) {
            mHideAnimator.cancel();
            mHideAnimator = null;
        }

        // if show animation ended (meaning content is fully visible) this is a no-op
        if (mFullyVisible) {
            return;
        }

        setVisibility(VISIBLE);
        int right = mContent.getRight();
        int top = mContent.getTop();
        float f = (float) Math.sqrt(Math.pow(mContent.getWidth(), 2D) + Math.pow(mContent.getHeight(), 2D));
        mShowAnimator = ViewAnimationUtils.createCircularReveal(mContent, right, top, 0, f);
        setAnimatorParams(mShowAnimator);
        mShowAnimator.addListener(new SupportAnimator.AnimatorListener() {
            @Override
            public void onAnimationStart() {
            }

            @Override
            public void onAnimationEnd() {
                mFullyVisible = true;
            }

            @Override
            public void onAnimationCancel() {
            }

            @Override
            public void onAnimationRepeat() {
            }
        });

        // create hide animation now
        mHideAnimator = mShowAnimator.reverse();
        setAnimatorParams(mHideAnimator);
        mHideAnimator.addListener(new SupportAnimator.AnimatorListener() {
            @Override
            public void onAnimationStart() {
            }

            @Override
            public void onAnimationEnd() {
                setVisibility(INVISIBLE);
                mFullyVisible = false;
                mHideAnimator = null;
            }

            @Override
            public void onAnimationCancel() {
            }

            @Override
            public void onAnimationRepeat() {
            }
        });

        mShowAnimator.start();
    }

    public void hide() {
        mFullyVisible = false;

        // cancel show animation (if any)
        if (mShowAnimator != null && mShowAnimator.isRunning()) {
            mShowAnimator.cancel();
            mShowAnimator = null;
        }

        if (mHideAnimator != null && !mHideAnimator.isRunning()) {
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

    private void setAnimatorParams(SupportAnimator anim) {
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.setDuration(250);
    }

}
