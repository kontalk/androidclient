package org.kontalk.ui.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.RelativeLayout;


/**
 * The composer bar.
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public class ComposerBar extends RelativeLayout {

    public ComposerBar(Context context) {
        super(context);
    }

    public ComposerBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ComposerBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ComposerBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
}
