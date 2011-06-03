package org.nuntius.ui;

import org.nuntius.R;
import org.nuntius.client.AbstractMessage;
import org.nuntius.provider.MyMessages.Messages;

import android.content.Context;
import android.graphics.Paint.FontMetricsInt;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineHeightSpan;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class MessageListItem extends RelativeLayout {
    private static final String TAG = MessageListItem.class.getSimpleName();

    private AbstractMessage<?> mMessage;
    private CharSequence formattedMessage;
    private TextView mTextView;

    private LeadingMarginSpan mLeadingMarginSpan;

    private LineHeightSpan mSpan = new LineHeightSpan() {
        public void chooseHeight(CharSequence text, int start,
                int end, int spanstartv, int v, FontMetricsInt fm) {
            fm.ascent -= 10;
        }
    };

    private TextAppearanceSpan mTextSmallSpan =
        new TextAppearanceSpan(getContext(), android.R.style.TextAppearance_Small);

    private ForegroundColorSpan mColorSpan = null;  // set in ctor

    public MessageListItem(Context context) {
        super(context);
    }

    public MessageListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        int color = context.getResources().getColor(R.color.timestamp_color);
        mColorSpan = new ForegroundColorSpan(color);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTextView = (TextView) findViewById(R.id.text_view);
    }

    public final void bind(Context context, final AbstractMessage<?> msg) {
        mMessage = msg;

        formattedMessage = formatMessage();
        mTextView.setText(formattedMessage);
    }

    private CharSequence formatMessage() {
        CharSequence template = getContext().getResources().getText(R.string.name_colon);
        SpannableStringBuilder buf =
            new SpannableStringBuilder(TextUtils.replace(template,
                new String[] { "%s" },
                new CharSequence[] { mMessage.getSender() }));

        if (!TextUtils.isEmpty(mMessage.getTextContent())) {
            buf.append(mMessage.getTextContent());
        }

        String timestamp;
        switch (mMessage.getStatus()) {
            case Messages.STATUS_SENDING:
                timestamp = "Sending...";
                break;
            case Messages.STATUS_RECEIVED:
                timestamp = String.format(getContext().getString(R.string.received_on),
                        MessageUtils.formatTimeStampString(getContext(), mMessage.getTimestamp()));
                break;
            case Messages.STATUS_NOTACCEPTED:
                timestamp = "Error";
                break;
            case Messages.STATUS_SENT:
            default:
                timestamp = String.format(getContext().getString(R.string.sent_on),
                        MessageUtils.formatTimeStampString(getContext(), mMessage.getTimestamp()));
                break;
        }

        buf.append("\n");
        int startOffset = buf.length();

        startOffset = buf.length();
        buf.append(TextUtils.isEmpty(timestamp) ? " " : timestamp);

        buf.setSpan(mTextSmallSpan, startOffset, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        buf.setSpan(mSpan, startOffset+1, buf.length(), 0);

        // Make the timestamp text not as dark
        buf.setSpan(mColorSpan, startOffset, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        buf.setSpan(mLeadingMarginSpan, 0, buf.length(), 0);
        return buf;
    }

    public final void unbind() {
        // TODO unbind (contact?)
    }
}
