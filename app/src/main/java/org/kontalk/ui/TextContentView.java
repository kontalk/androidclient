package org.kontalk.ui;

import android.content.Context;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.RawComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Message component for {@link TextComponent}.
 * @author Daniele Ricci
 */
public class TextContentView extends TextView
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
        int color = context.getResources().getColor(R.color.highlight_color);
        mHighlightColorSpan = new BackgroundColorSpan(color);
    }

    /*
     * Hack for fixing extra space took by the TextView.
     * I still have to understand why this works and plain getHeight() doesn't.
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

    public void bind(TextComponent component, Contact contact, Pattern highlight) {
        mComponent = component;
        Context context = getContext();

        SpannableStringBuilder formattedMessage = formatMessage(contact, highlight);
        String size = Preferences.getFontSize(context);
        int sizeId;
        if (size.equals("small"))
            sizeId = android.R.style.TextAppearance_Small;
        else if (size.equals("large"))
            sizeId = android.R.style.TextAppearance_Large;
        else
            sizeId = android.R.style.TextAppearance;
        setTextAppearance(context, sizeId);

        // linkify!
        boolean linksFound = false;
        if (formattedMessage.length() < MAX_AFFORDABLE_SIZE)
            linksFound = Linkify.addLinks(formattedMessage, Linkify.ALL);

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

        if (linksFound)
            setMovementMethod(LinkMovementMethod.getInstance());
        else
            setMovementMethod(null);

        setText(formattedMessage);
    }

    public void unbind() {
        recycle();
    }

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

    private SpannableStringBuilder formatMessage(final Contact contact, final Pattern highlight) {
        SpannableStringBuilder buf;

        String textContent = mComponent.getContent();

        buf = new SpannableStringBuilder(textContent);

        // convert smileys first
        int c = buf.length();
        if (c > 0 && c < MAX_AFFORDABLE_SIZE)
            MessageUtils.convertSmileys(getContext(), buf, MessageUtils.SmileyImageSpan.SIZE_EDITABLE);

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

}
