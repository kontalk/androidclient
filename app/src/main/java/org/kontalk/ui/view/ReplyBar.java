/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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
import android.content.res.TypedArray;
import android.os.Build;
import android.support.annotation.IdRes;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.kontalk.R;


/**
 * The composer bar.
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public class ReplyBar extends RelativeLayout {

    /** The referenced message database id. */
    private long mMessageId;

    private TextView mSender;
    private TextView mText;

    OnCancelListener mOnCancelListener;

    // this is actually out of our view group
    private View mDividerView;
    @IdRes
    private int mDividerViewId;

    public ReplyBar(Context context) {
        super(context);
        init(context, null, 0);
    }

    public ReplyBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public ReplyBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ReplyBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        inflate(context, R.layout.reply_bar, this);

        final TypedArray attrArray = context.obtainStyledAttributes(attrs,
            R.styleable.ReplyBar, defStyle, 0);
        initAttributes(attrArray);
        attrArray.recycle();
    }

    private void initAttributes(TypedArray attrs) {
        mDividerViewId = attrs.getResourceId(R.styleable.ReplyBar_dividerView, View.NO_ID);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSender = findViewById(R.id.sender);
        mText = findViewById(R.id.text);

        findViewById(R.id.btn_cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mOnCancelListener != null)
                    mOnCancelListener.onCancel(ReplyBar.this);
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mDividerViewId != View.NO_ID) {
            ViewParent parent = getParent();
            if (parent instanceof View) {
                mDividerView = ((View) parent).findViewById(mDividerViewId);
            }
        }
    }

    public void setOnCancelListener(OnCancelListener listener) {
        mOnCancelListener = listener;
    }

    public long getMessageId() {
        return mMessageId;
    }

    public void show(long msgId, CharSequence sender, CharSequence text) {
        mMessageId = msgId;
        mSender.setText(sender);
        mText.setText(text);
        setVisibility(VISIBLE);
        if (mDividerView != null)
            mDividerView.setVisibility(VISIBLE);
    }

    public void hide() {
        mMessageId = 0;
        setVisibility(GONE);
        if (mDividerView != null)
            mDividerView.setVisibility(GONE);
    }

    public interface OnCancelListener {
        void onCancel(ReplyBar view);
    }

}
