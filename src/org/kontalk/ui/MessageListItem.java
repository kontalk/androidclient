package org.kontalk.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kontalk.R;
import org.kontalk.client.AbstractMessage;
import org.kontalk.client.ImageMessage;
import org.kontalk.data.Contact;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.util.MessageUtils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
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
    private TextView mDateView;
    private TextView mNameView;
    private View mBalloonView;
    private View mBackground;

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
    */

    private BackgroundColorSpan mHighlightColorSpan;  // set in ctor

    public MessageListItem(Context context) {
        super(context);
    }

    public MessageListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        int color = context.getResources().getColor(R.color.highlight_color);
        mHighlightColorSpan = new BackgroundColorSpan(color);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTextView = (TextView) findViewById(R.id.text_view);
        mStatusIcon = (ImageView) findViewById(R.id.status_indicator);
        mBalloonView = findViewById(R.id.balloon_view);
        mDateViewIncoming = (TextView) findViewById(R.id.date_view_incoming);
        mDateViewOutgoing = (TextView) findViewById(R.id.date_view_outgoing);
        mDateView = (TextView) findViewById(R.id.date_view);
        mNameView = (TextView) findViewById(R.id.name_view);
        mBackground = findViewById(R.id.msg_list_item_background);

        if (isInEditMode()) {
            mTextView.setText("Test messaggio\nCiao zio!\nBelluuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu!!");
            /* INCOMING */
            //mTextView.setBackgroundResource(R.drawable.light_blue_background);
            if (mStatusIcon != null)
            	mStatusIcon.setImageResource(R.drawable.ic_msg_delivered);
            if (mDateView == null) {
                if (mBalloonView != null)
                    mBalloonView.setBackgroundResource(R.drawable.balloon_incoming);
                if (mDateViewIncoming != null) {
    	            mDateViewIncoming.setVisibility(VISIBLE);
    	            mDateViewOutgoing.setVisibility(GONE);
    	            mDateViewIncoming.setText("10:46");
                }
            }
            else {
                int backId = R.drawable.message_list_item_in_fill;
                mNameView.setBackgroundResource(backId);
                mDateView.setBackgroundResource(backId);
                mBackground.setBackgroundResource(R.drawable.message_list_item_in_border);
            	mNameView.setText("Daniele Ricci");
            	mDateView.setText("11:56");
            }
	        /* OUTGOING
            setGravity(Gravity.RIGHT);
            setBackgroundResource(R.drawable.white_background);
            if (mBalloonView != null)
            	mBalloonView.setBackgroundResource(R.drawable.balloon_outgoing);
            if (mDateViewIncoming != null) {
	            mDateViewIncoming.setVisibility(GONE);
	            mDateViewOutgoing.setVisibility(VISIBLE);
	            mDateViewOutgoing.setText("10:46");
            }
            */
        }
    }

    public final void bind(Context context, final AbstractMessage<?> msg,
            final Contact contact, final Pattern highlight) {
        mMessage = msg;

        formattedMessage = formatMessage(contact, highlight);
        mTextView.setText(formattedMessage);

        int resId = -1;

        if (mMessage.getSender() != null) {
            if (mBalloonView != null)
	            mBalloonView.setBackgroundResource(
	                (mMessage.wasEncrypted()) ?
	                R.drawable.encrypted_incoming :
	                R.drawable.balloon_incoming);

            if (mDateView == null) {
                setGravity(Gravity.LEFT);
                setBackgroundResource(R.drawable.light_blue_background);
	            mDateViewIncoming.setVisibility(VISIBLE);
	            mDateViewOutgoing.setVisibility(GONE);
            }
            else {
	            int backId = R.drawable.message_list_item_in_fill;
	            mNameView.setBackgroundResource(backId);
	            mDateView.setBackgroundResource(backId);
	            mBackground.setBackgroundResource(R.drawable.message_list_item_in_border);
            }
        }
        else {
            if (mBalloonView != null)
            	mBalloonView.setBackgroundResource(
                    mMessage.wasEncrypted() ?
                    R.drawable.encrypted_outgoing :
                    R.drawable.balloon_outgoing);

            if (mDateView == null) {
                setGravity(Gravity.RIGHT);
                setBackgroundResource(R.drawable.white_background);
	            mDateViewIncoming.setVisibility(GONE);
	            mDateViewOutgoing.setVisibility(VISIBLE);
	        }
            else {
	            int backId = R.drawable.message_list_item_out_fill;
	            mNameView.setBackgroundResource(backId);
	            mDateView.setBackgroundResource(backId);
	            mBackground.setBackgroundResource(R.drawable.message_list_item_out_border);
            }

            // status icon
            if (mMessage.getSender() == null)
            switch (mMessage.getStatus()) {
                case Messages.STATUS_SENDING:
                    resId = R.drawable.ic_msg_pending;
                    break;
                case Messages.STATUS_RECEIVED:
                    resId = R.drawable.ic_msg_delivered;
                    break;
                case Messages.STATUS_ERROR:
                case Messages.STATUS_NOTACCEPTED:
                    resId = R.drawable.ic_msg_error;
                    break;
                case Messages.STATUS_SENT:
                    resId = R.drawable.ic_msg_sent;
                    break;
                case Messages.STATUS_NOTDELIVERED:
                    resId = R.drawable.ic_msg_notdelivered;
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

    private CharSequence formatMessage(final Contact contact, final Pattern highlight) {
        SpannableStringBuilder buf = new SpannableStringBuilder();

        if (!TextUtils.isEmpty(mMessage.getTextContent())) {
            if (mMessage.isEncrypted()) {
                buf.append(getResources().getString(R.string.text_hint_encrypted));
            }
            else {
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
        }

        TextView dateView;

        if (mDateView == null)
        	dateView = (mMessage.getSender() != null) ?
                mDateViewIncoming : mDateViewOutgoing;
        else
        	dateView = mDateView;

        dateView.setText(MessageUtils.formatTimeStampString(getContext(), mMessage.getTimestamp()));

        if (mNameView != null) {
        	String text;
        	if (mMessage.getDirection() == Messages.DIRECTION_IN) {
        		text = (contact != null) ? contact.getName() : mMessage.getSender(true);
        	}
        	else {
        		text = getResources().getString(R.string.myself_label);
        	}

        	mNameView.setText(text);
        }

        if (highlight != null) {
            Matcher m = highlight.matcher(buf.toString());
            while (m.find())
                buf.setSpan(mHighlightColorSpan, m.start(), m.end(), 0);
        }

        return buf;
    }

    public final void unbind() {
        // TODO unbind (contact?)
    }

    public AbstractMessage<?> getMessage() {
        return mMessage;
    }
}
