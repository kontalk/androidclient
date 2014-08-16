package org.kontalk.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

/**
 * An emoji drawer.
 * @author Daniele Ricci
 */
public class EmojiDrawer extends KeyboardAwareFrameLayout {

    public EmojiDrawer(Context context) {
        super(context);
    }

    public EmojiDrawer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public EmojiDrawer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public boolean isVisible() {
        return getVisibility() == View.VISIBLE;
    }

    public void hide() {
        setVisibility(View.GONE);
    }

    public void show() {
        int keyboardHeight = getKeyboardHeight();
        setLayoutParams(new LinearLayout.LayoutParams(LinearLayout
            .LayoutParams.MATCH_PARENT, keyboardHeight));
        requestLayout();
        setVisibility(View.VISIBLE);
    }

}
