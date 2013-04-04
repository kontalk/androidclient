/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.xmpp.ui;

import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kontalk.xmpp.R;
import org.kontalk.xmpp.data.Contact;
import org.kontalk.xmpp.message.AbstractMessage;
import org.kontalk.xmpp.message.ImageMessage;
import org.kontalk.xmpp.provider.MyMessages.Messages;
import org.kontalk.xmpp.util.MessageUtils;
import org.kontalk.xmpp.util.MessageUtils.SmileyImageSpan;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ImageSpan;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;


/**
 * A message list item to be used in {@link ComposeMessage} activity.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageListItem extends RelativeLayout {
    //private static final String TAG = MessageListItem.class.getSimpleName();
    static private Drawable sDefaultContactImage;

    private AbstractMessage<?> mMessage;
    private SpannableStringBuilder formattedMessage;
    private TextView mTextView;
    private ImageView mStatusIcon;
    private ImageView mLockView;
    private TextView mDateView;
    private View mBalloonView;
    private LinearLayout mParentView;

    private ImageView mAvatarIncoming;
    private ImageView mAvatarOutgoing;

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

    public MessageListItem(final Context context, AttributeSet attrs) {
        super(context, attrs);
        int color = context.getResources().getColor(R.color.highlight_color);
        mHighlightColorSpan = new BackgroundColorSpan(color);

        if (sDefaultContactImage == null) {
            sDefaultContactImage = context.getResources().getDrawable(R.drawable.ic_contact_picture);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mTextView = (TextView) findViewById(R.id.text_view);
        mStatusIcon = (ImageView) findViewById(R.id.status_indicator);
        mLockView = (ImageView) findViewById(R.id.lock_icon);
        mBalloonView = findViewById(R.id.balloon_view);
        mDateView = (TextView) findViewById(R.id.date_view);
        mAvatarIncoming = (ImageView) findViewById(R.id.avatar_incoming);
        mAvatarOutgoing = (ImageView) findViewById(R.id.avatar_outgoing);
        mParentView = (LinearLayout) findViewById(R.id.message_view_parent);

        if (isInEditMode()) {
            mTextView.setText("Test messaggio\nCiao zio!\nBelluuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu!!");
            mTextView.setText("TEST");
            //mTextView.setText(":-)");
            /* INCOMING
            if (mDateView == null) {
                if (mBalloonView != null)
                    mBalloonView.setBackgroundResource(R.drawable.balloon_incoming);
                if (mDateViewIncoming != null) {
    	            mDateViewIncoming.setVisibility(VISIBLE);
    	            mDateViewOutgoing.setVisibility(GONE);
    	            mDateViewIncoming.setText("28 Nov");
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
            */
	        /* OUTGOING */
            if (mStatusIcon != null) {
                mStatusIcon.setImageResource(R.drawable.ic_msg_delivered);
                mStatusIcon.setVisibility(VISIBLE);
            }
            mLockView.setVisibility(VISIBLE);
            if (mStatusIcon != null)
                mStatusIcon.setImageResource(R.drawable.ic_msg_delivered);
            setGravity(Gravity.RIGHT);
            if (mBalloonView != null)
                mBalloonView.setBackgroundResource(R.drawable.balloon_classic_outgoing);
            if (mDateView != null) {
                mDateView.setVisibility(VISIBLE);
                mDateView.setText("00:00");
            }
            if (mAvatarIncoming != null) {
                mAvatarIncoming.setVisibility(GONE);
                mAvatarOutgoing.setVisibility(VISIBLE);
                mAvatarOutgoing.setImageResource(R.drawable.ic_contact_picture);
            }
        }
    }

    public final void bind(Context context, final AbstractMessage<?> msg,
            final Contact contact, final Pattern highlight) {
        mMessage = msg;

        formattedMessage = formatMessage(contact, highlight);
        String size = MessagingPreferences.getFontSize(context);
        int sizeId;
        if (size.equals("small"))
            sizeId = android.R.style.TextAppearance_Small;
        else if (size.equals("large"))
            sizeId = android.R.style.TextAppearance_Large;
        else
            sizeId = android.R.style.TextAppearance;
        mTextView.setTextAppearance(context, sizeId);

        // linkify!
        boolean linksFound = Linkify.addLinks(formattedMessage, Linkify.ALL);

        /*
         * workaround for bugs:
         * http://code.google.com/p/android/issues/detail?id=17343
         * http://code.google.com/p/android/issues/detail?id=22493
         * applies only to ICS
         */
        if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH ||
                android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
            formattedMessage.append("\u2060");

        if (linksFound)
            mTextView.setMovementMethod(LinkMovementMethod.getInstance());
        else
            mTextView.setMovementMethod(null);

        mTextView.setText(formattedMessage);

        int resId = 0;
        int statusId = 0;

        mLockView.setVisibility((mMessage.wasEncrypted()) ? GONE : VISIBLE);

        if (mMessage.getSender() != null) {
            if (mBalloonView != null)
	            mBalloonView.setBackgroundResource(MessagingPreferences
	                .getBalloonResource(getContext(), Messages.DIRECTION_IN));

            setGravity(Gravity.LEFT);
            mParentView.setGravity(Gravity.LEFT);
            //setBackgroundResource(R.drawable.light_blue_background);

            if (mAvatarIncoming != null) {
                mAvatarOutgoing.setVisibility(GONE);
                mAvatarIncoming.setVisibility(VISIBLE);
                mAvatarIncoming.setImageDrawable(contact != null ?
                    contact.getAvatar(context, sDefaultContactImage) : sDefaultContactImage);
            }
        }
        else {
            if (mBalloonView != null)
            	mBalloonView.setBackgroundResource(MessagingPreferences
                    .getBalloonResource(getContext(), Messages.DIRECTION_OUT));

            setGravity(Gravity.RIGHT);
            mParentView.setGravity(Gravity.RIGHT);
            //setBackgroundResource(R.drawable.white_background);

            if (mAvatarOutgoing != null) {
                mAvatarIncoming.setVisibility(GONE);
                mAvatarOutgoing.setVisibility(VISIBLE);
                mAvatarOutgoing.setImageDrawable(contact != null ?
                    contact.getAvatar(context, sDefaultContactImage) : sDefaultContactImage);
            }

            // status icon
            if (mMessage.getSender() == null)
            switch (mMessage.getStatus()) {
                case Messages.STATUS_SENDING:
                // use pending icon even for errors
                case Messages.STATUS_ERROR:
                    resId = R.drawable.ic_msg_pending;
                    statusId = R.string.msg_status_sending;
                    break;
                case Messages.STATUS_RECEIVED:
                    resId = R.drawable.ic_msg_delivered;
                    statusId = R.string.msg_status_delivered;
                    break;
                // here we use the error icon
                case Messages.STATUS_NOTACCEPTED:
                    resId = R.drawable.ic_msg_error;
                    statusId = R.string.msg_status_notaccepted;
                    break;
                case Messages.STATUS_SENT:
                    resId = R.drawable.ic_msg_sent;
                    statusId = R.string.msg_status_sent;
                    break;
                case Messages.STATUS_NOTDELIVERED:
                    resId = R.drawable.ic_msg_notdelivered;
                    statusId = R.string.msg_status_notdelivered;
                    break;
            }
        }

        if (resId > 0) {
            mStatusIcon.setImageResource(resId);
            mStatusIcon.setVisibility(VISIBLE);
            mStatusIcon.setContentDescription(getResources().getString(statusId));
        }
        else {
            mStatusIcon.setImageDrawable(null);
            mStatusIcon.setVisibility(GONE);
        }

        // we are using a custom bg, place the background and invert text color
        if (MessagingPreferences.getConversationBackground(getContext()) != null) {
            mDateView.setBackgroundResource(R.drawable.datebox);
            mDateView.setTextAppearance(getContext(), android.R.style.TextAppearance_Small_Inverse);
        }
        else {
            mDateView.setBackgroundResource(0);
            mDateView.setTextAppearance(getContext(), android.R.style.TextAppearance_Small);
        }

        // enforce text size
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.dateview_text_size));
    }

    private final class MaxSizeImageSpan extends ImageSpan {
        private final Drawable mDrawable;

        public MaxSizeImageSpan(Context context, Bitmap bitmap) {
            super(context, bitmap);
            mDrawable = super.getDrawable();
            mDrawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        }

        @Override
        public Drawable getDrawable() {
            return mDrawable;
        }
    }

    private SpannableStringBuilder formatMessage(final Contact contact, final Pattern highlight) {
        SpannableStringBuilder buf;
        String textContent;
        try {
            textContent = mMessage.getTextContent();
        }
        catch (UnsupportedEncodingException e) {
            // TODO handle this
            textContent = mMessage.getBinaryContent().toString();
        }

        if (!TextUtils.isEmpty(textContent)) {
            if (mMessage.isEncrypted()) {
                buf = new SpannableStringBuilder(getResources().getString(R.string.text_encrypted));
            }
            else {
                buf = new SpannableStringBuilder(textContent);

                if (mMessage instanceof ImageMessage) {
                    ImageMessage image = (ImageMessage) mMessage;
                    Bitmap bitmap = image.getContent();
                    if (bitmap != null) {
                        ImageSpan imgSpan = new MaxSizeImageSpan(getContext(), image.getContent());
                        buf.setSpan(imgSpan, 0, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                else {
                    MessageUtils.convertSmileys(getContext(), buf, SmileyImageSpan.SIZE_EDITABLE);
                }
            }
        }
        else {
            buf = new SpannableStringBuilder();
        }

        long serverTime = mMessage.getServerTimestamp();
        long ts = serverTime > 0 ? serverTime : mMessage.getTimestamp();

        // if we are in the same day, just prime time, else print date & time
        boolean fullFormat;
        Calendar thenCal = new GregorianCalendar();
        thenCal.setTimeInMillis(ts);
        Calendar nowCal = new GregorianCalendar();
        if (thenCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
            && thenCal.get(Calendar.MONTH) == nowCal.get(Calendar.MONTH)
            && thenCal.get(Calendar.DAY_OF_MONTH) == nowCal.get(Calendar.DAY_OF_MONTH)) {
            fullFormat = false;
        }
        else {
            fullFormat = true;
        }

        mDateView.setText(MessageUtils.formatTimeStampString(getContext(), ts, fullFormat));

        if (highlight != null) {
            Matcher m = highlight.matcher(buf.toString());
            while (m.find())
                buf.setSpan(mHighlightColorSpan, m.start(), m.end(), 0);
        }

        return buf;
    }

    public final void unbind() {
        mMessage.recycle();
        mMessage = null;
    }

    public AbstractMessage<?> getMessage() {
        return mMessage;
    }
}
