package org.nuntius.ui;

import org.nuntius.R;
import org.nuntius.client.AbstractMessage;
import org.nuntius.client.ImageMessage;
import org.nuntius.provider.MyMessages.Messages;
import org.nuntius.util.MessageUtils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;


/**
 * A message list item to be used in {@link ComposeMessage} activity.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageListItem extends RelativeLayout {
    //private static final String TAG = MessageListItem.class.getSimpleName();

    private AbstractMessage<?> mMessage;
    private CharSequence formattedMessage;
    private TextView mTextView;
    private ImageView mStatusIcon;
    private TextView mDateViewIncoming;
    private TextView mDateViewOutgoing;
    private View mBalloonView;

    /*
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
    */

    public MessageListItem(Context context) {
        super(context);
    }

    public MessageListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        //int color = context.getResources().getColor(R.color.timestamp_color);
        //mColorSpan = new ForegroundColorSpan(color);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTextView = (TextView) findViewById(R.id.text_view);
        mStatusIcon = (ImageView) findViewById(R.id.status_indicator);
        mBalloonView = findViewById(R.id.balloon_view);
        mDateViewIncoming = (TextView) findViewById(R.id.date_view_incoming);
        mDateViewOutgoing = (TextView) findViewById(R.id.date_view_outgoing);

        if (isInEditMode()) {
            mTextView.setText("Test messaggio\nCiao zio!\nBelluuuuuuuuuuuuuuuuuu!!");
            /* INCOMING
            setGravity(Gravity.LEFT);
            setBackgroundResource(R.drawable.light_blue_background);
            mBalloonView.setBackgroundResource(R.drawable.balloon_incoming);
            mDateViewIncoming.setVisibility(VISIBLE);
            mDateViewOutgoing.setVisibility(GONE);
            mDateViewIncoming.setText("10:46");
            */
            /* OUTGOING */
            setGravity(Gravity.RIGHT);
            setBackgroundResource(R.drawable.white_background);
            mBalloonView.setBackgroundResource(R.drawable.balloon_outgoing);
            mDateViewIncoming.setVisibility(GONE);
            mDateViewOutgoing.setVisibility(VISIBLE);
            mDateViewOutgoing.setText("10:46");
        }
    }

    public final void bind(Context context, final AbstractMessage<?> msg) {
        mMessage = msg;

        formattedMessage = formatMessage();
        mTextView.setText(formattedMessage);

        int resId = -1;

        if (mMessage.getSender() != null) {
            setGravity(Gravity.LEFT);
            setBackgroundResource(R.drawable.light_blue_background);
            mBalloonView.setBackgroundResource(R.drawable.balloon_incoming);

            mDateViewIncoming.setVisibility(VISIBLE);
            mDateViewOutgoing.setVisibility(GONE);
        }
        else {
            setGravity(Gravity.RIGHT);
            setBackgroundResource(R.drawable.white_background);
            mBalloonView.setBackgroundResource(R.drawable.balloon_outgoing);

            mDateViewIncoming.setVisibility(GONE);
            mDateViewOutgoing.setVisibility(VISIBLE);

            // status icon
            if (mMessage.getSender() == null)
            switch (mMessage.getStatus()) {
                case Messages.STATUS_SENDING:
                    resId = R.drawable.ic_msg_pending;
                    break;
                case Messages.STATUS_RECEIVED:
                    resId = R.drawable.ic_msg_delivered;
                    break;
                case Messages.STATUS_NOTACCEPTED:
                    resId = R.drawable.ic_msg_error;
                    break;
                case Messages.STATUS_ERROR:
                    resId = R.drawable.ic_msg_error;
                    break;
                case Messages.STATUS_SENT:
                    resId = R.drawable.ic_msg_sent;
                    break;
            }
        }

        if (resId >= 0) {
            mStatusIcon.setImageResource(resId);
            mStatusIcon.setVisibility(VISIBLE);
        }
        else {
            mStatusIcon.setImageDrawable(null);
            mStatusIcon.setVisibility(GONE);
        }

    }

    private final class MaxSizeImageSpan extends ImageSpan {
        private final Drawable mDrawable;

        public MaxSizeImageSpan(Context context, Bitmap bitmap) {
            super(context, bitmap);
            mDrawable = new BitmapDrawable(context.getResources(), bitmap);
            mDrawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        }

        @Override
        public Drawable getDrawable() {
            return mDrawable;
        }
    }

    private CharSequence formatMessage() {
        SpannableStringBuilder buf = new SpannableStringBuilder();

        if (!TextUtils.isEmpty(mMessage.getTextContent())) {
            buf.append(mMessage.getTextContent());

            if (mMessage instanceof ImageMessage) {
                ImageMessage image = (ImageMessage) mMessage;
                Bitmap bitmap = image.getContent();
                if (bitmap != null) {
                    ImageSpan imgSpan = new MaxSizeImageSpan(getContext(), image.getContent());
                    buf.setSpan(imgSpan, 0, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        TextView dateView = (mMessage.getSender() != null) ?
                mDateViewIncoming : mDateViewOutgoing;

        dateView.setText(MessageUtils.formatTimeStampString(getContext(), mMessage.getTimestamp()));

        /*
        buf.append("\n");
        int startOffset = buf.length();

        startOffset = buf.length();
        buf.append(TextUtils.isEmpty(timestamp) ? " " : timestamp);

        buf.setSpan(mTextSmallSpan, startOffset, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        buf.setSpan(mSpan, startOffset+1, buf.length(), 0);

        // Make the timestamp text not as dark
        buf.setSpan(mColorSpan, startOffset, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        buf.setSpan(mLeadingMarginSpan, 0, buf.length(), 0);
        */
        return buf;
    }

    public final void unbind() {
        // TODO unbind (contact?)
    }

    public AbstractMessage<?> getMessage() {
        return mMessage;
    }
}
