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

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.TextViewCompat;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.message.TextComponent;
import org.kontalk.util.Preferences;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.rockerhieu.emojicon.EmojiconTextView;


/**
 * Message component for {@link TextComponent}.
 * @author Daniele Ricci
 */
public class TextContentView extends EmojiconTextView
        implements MessageContentView<TextComponent> {

    // pool-related stuff

    private static final Object sPoolSync = new Object();
    private static TextContentView sPool;
    private static int sPoolSize = 0;

    /** Global pool max size. */
    private static final int MAX_POOL_SIZE = 50;

    /** Used for pooling. */
    protected TextContentView next;


    /**
     * Maximum affordable size of a text message to make complex stuff
     * (e.g. emoji, linkify, etc.)
     */
    private static final int MAX_AFFORDABLE_SIZE = 10240;   // 10 KB

    private TextComponent mComponent;
    private boolean mEncryptionPlaceholder;
    private BackgroundColorSpan mHighlightColorSpan;  // set in ctor

    private boolean mMeasureHack;

    public TextContentView(Context context) {
        super(context);
        init(context);
    }

    public TextContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TextContentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        int color = ContextCompat.getColor(context, R.color.highlight_color);
        mHighlightColorSpan = new BackgroundColorSpan(color);
    }

    /**
     * Hack for fixing extra space took by the TextView.
     * I still have to understand why this works and plain getHeight() doesn't.
     * http://stackoverflow.com/questions/7439748/why-is-wrap-content-in-multiple-line-textview-filling-parent
     * https://github.com/qklabs/qksms/blob/master/QKSMS/src/main/java/com/moez/QKSMS/ui/view/QKTextView.java
     */
    void enableMeasureHack(boolean enabled) {
        mMeasureHack = enabled;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (mMeasureHack) {
            int specModeW = MeasureSpec.getMode(widthMeasureSpec);
            if (specModeW != MeasureSpec.EXACTLY) {
                Layout layout = getLayout();
                int linesCount = layout.getLineCount();
                if (linesCount > 1) {
                    float textRealMaxWidth = 0;
                    for (int n = 0; n < linesCount; ++n) {
                        textRealMaxWidth = Math.max(textRealMaxWidth, layout.getLineWidth(n));
                    }
                    int w = Math.round(textRealMaxWidth);
                    if (w < getMeasuredWidth()) {
                        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST),
                            heightMeasureSpec);
                    }
                }
            }
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

    @Override
    public void bind(long databaseId, TextComponent component, Pattern highlight) {
        mComponent = component;

        SpannableStringBuilder formattedMessage = formatMessage(highlight);
        setTextStyle(this);

        // linkify!
        if (formattedMessage.length() < MAX_AFFORDABLE_SIZE)
            Linkify.addLinks(formattedMessage, Linkify.ALL);

        /*
         * workaround for bugs:
         * http://code.google.com/p/android/issues/detail?id=17343
         * http://code.google.com/p/android/issues/detail?id=22493
         * applies from Honeycomb to JB 4.2.2 afaik
         */
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB &&
                android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
            // from http://stackoverflow.com/a/12303155/1045199
            formattedMessage.append("\u200b"); // was: \u2060

        setText(formattedMessage);
    }

    @Override
    public void unbind() {
        recycle();
    }

    @Override
    public TextComponent getComponent() {
        return mComponent;
    }

    /** Text is always below. */
    @Override
    public int getPriority() {
        return 10;
    }

    public boolean isEncryptionPlaceholder() {
        return mEncryptionPlaceholder;
    }

    private SpannableStringBuilder formatMessage(final Pattern highlight) {
        SpannableStringBuilder buf;

        String textContent = mComponent.getContent();

        buf = new SpannableStringBuilder(textContent);

        if (highlight != null) {
            Matcher m = highlight.matcher(buf.toString());
            while (m.find())
                buf.setSpan(mHighlightColorSpan, m.start(), m.end(), 0);
        }

        return buf;
    }

    private void clear() {
        mComponent = null;
    }

    public void recycle() {
        clear();

        synchronized (sPoolSync) {
            if (sPoolSize < MAX_POOL_SIZE) {
                next = sPool;
                sPool = this;
                sPoolSize++;
            }
        }
    }

    public static TextContentView obtain(LayoutInflater inflater, ViewGroup parent) {
        return obtain(inflater, parent, false);
    }

    /**
     * Return a new Message instance from the global pool. Allows us to
     * avoid allocating new objects in many cases. Inspired by {@link android.os.Message}.
     * @param encryptionPlaceholder true if the whole message is encrypted and this is a placeholder.
     */
    public static TextContentView obtain(LayoutInflater inflater, ViewGroup parent, boolean encryptionPlaceholder) {
        synchronized (sPoolSync) {
            if (sPool != null) {
                TextContentView m = sPool;
                sPool = m.next;
                m.next = null;
                sPoolSize--;
                //m.mContext = context;
                m.mEncryptionPlaceholder = encryptionPlaceholder;
                return m;
            }
        }

        return create(inflater, parent, encryptionPlaceholder);
    }

    public static TextContentView create(LayoutInflater inflater, ViewGroup parent, boolean encryptionPlaceholder) {
        TextContentView view = (TextContentView) inflater.inflate(R.layout.message_content_text,
            parent, false);
        view.mEncryptionPlaceholder = encryptionPlaceholder;

        return view;
    }

    public static void setTextStyle(TextView textView) {
        Context context = textView.getContext();
        String size = Preferences.getFontSize(context);
        int sizeId;
        if (size.equals("small"))
            sizeId = android.R.style.TextAppearance_Small;
        else if (size.equals("large"))
            sizeId = android.R.style.TextAppearance_Large;
        else
            sizeId = android.R.style.TextAppearance;
        TextViewCompat.setTextAppearance(textView, sizeId);
        //setEmojiconSize((int) getTextSize());
    }

}
