package org.kontalk.xmpp.ui;

import android.content.Context;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.TextView;


public class MessageItemTextView extends TextView {

    public MessageItemTextView(Context context) {
        super(context);
    }

    public MessageItemTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MessageItemTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /*
     * Hack for fixing extra space took by the TextView.
     * I still have to understand why this works and plain getWidth() doesn't.
     * http://stackoverflow.com/questions/7439748/why-is-wrap-content-in-multiple-line-textview-filling-parent
     */

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        Layout layout = getLayout();
        if (layout != null) {
            int width = (int)Math.ceil(getMaxLineWidth(layout))
                    + getCompoundPaddingLeft() + getCompoundPaddingRight();
            int height = getMeasuredHeight();
            setMeasuredDimension(width, height);
        }
    }

    private float getMaxLineWidth(Layout layout) {
        float max_width = 0.0f;
        int lines = layout.getLineCount();
        for (int i = 0; i < lines; i++) {
            if (layout.getLineWidth(i) > max_width) {
                max_width = layout.getLineWidth(i);
            }
        }
        return max_width;
    }
}
