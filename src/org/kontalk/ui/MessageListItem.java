/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kontalk.R;
import org.kontalk.crypto.Coder;
import org.kontalk.data.Contact;
import org.kontalk.message.AttachmentComponent;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.ImageComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;
import org.kontalk.util.MessageUtils.SmileyImageSpan;

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
import android.view.Gravity;
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

	/**
	 * Maximum affordable size of a text message to make complex stuff
	 * (e.g. emoji, linkify, etc.)
	 */
	private static final int MAX_AFFORDABLE_SIZE = 10240;	// 10 KB

    static private Drawable sDefaultContactImage;

    private CompositeMessage mMessage;
    private SpannableStringBuilder mFormattedMessage;
    private MessageItemTextView mTextView;
    private ImageView mStatusIcon;
    private ImageView mWarningIcon;
    private TextView mDateView;
    private LinearLayout mBalloonView;
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

        mTextView = (MessageItemTextView) findViewById(R.id.text_view);
        mStatusIcon = (ImageView) findViewById(R.id.status_indicator);
        mWarningIcon = (ImageView) findViewById(R.id.warning_icon);
        mBalloonView = (LinearLayout) findViewById(R.id.balloon_view);
        mDateView = (TextView) findViewById(R.id.date_view);
        mAvatarIncoming = (ImageView) findViewById(R.id.avatar_incoming);
        mAvatarOutgoing = (ImageView) findViewById(R.id.avatar_outgoing);
        mParentView = (LinearLayout) findViewById(R.id.message_view_parent);

        if (isInEditMode()) {
            mTextView.setText("Test messaggio\nCiao zio!\nBelluuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu!!");
            //mTextView.setText("TEST");
            //mTextView.setText(":-)");
            /* INCOMING
            //setGravity(Gravity.LEFT);
            if (mBalloonView != null) {
                mBalloonView.setBackgroundResource(R.drawable.balloon_classic_incoming);
            }
            mDateView.setText("28 Nov");
            */

	        /* OUTGOING */
            if (mStatusIcon != null) {
                mStatusIcon.setImageResource(R.drawable.ic_msg_delivered);
                mStatusIcon.setVisibility(VISIBLE);
            }
            if (mStatusIcon != null)
                mStatusIcon.setImageResource(R.drawable.ic_msg_delivered);
            mWarningIcon.setVisibility(VISIBLE);
            setGravity(Gravity.RIGHT);
            if (mBalloonView != null) {
                mBalloonView.setBackgroundResource(R.drawable.balloon_classic_outgoing);
            }
            mDateView.setText("16:25");
            if (mAvatarIncoming != null) {
                mAvatarIncoming.setVisibility(GONE);
                mAvatarOutgoing.setVisibility(VISIBLE);
                mAvatarOutgoing.setImageResource(R.drawable.ic_contact_picture);
            }
        }
    }

    public final void bind(Context context, final CompositeMessage msg,
            final Contact contact, final Pattern highlight) {
        mMessage = msg;

        mFormattedMessage = formatMessage(contact, highlight);
        String size = Preferences.getFontSize(context);
        int sizeId;
        if (size.equals("small"))
            sizeId = android.R.style.TextAppearance_Small;
        else if (size.equals("large"))
            sizeId = android.R.style.TextAppearance_Large;
        else
            sizeId = android.R.style.TextAppearance;
        mTextView.setTextAppearance(context, sizeId);

        // linkify!
        boolean linksFound = false;
        if (mFormattedMessage.length() < MAX_AFFORDABLE_SIZE)
        	linksFound = Linkify.addLinks(mFormattedMessage, Linkify.ALL);

        /*
         * workaround for bugs:
         * http://code.google.com/p/android/issues/detail?id=17343
         * http://code.google.com/p/android/issues/detail?id=22493
         * applies from Honeycomb to JB 4.2.2 afaik
         */
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB &&
                android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
        	// from http://stackoverflow.com/a/12303155/1045199
        	mFormattedMessage.append("\u200b"); // was: \u2060

        if (linksFound)
            mTextView.setMovementMethod(LinkMovementMethod.getInstance());
        else
            mTextView.setMovementMethod(null);

        mTextView.setText(mFormattedMessage);

        int resId = 0;
        int statusId = 0;

        int securityFlags = mMessage.getSecurityFlags();

        if (Coder.isError(securityFlags)) {
        	mWarningIcon.setImageResource(R.drawable.ic_msg_security);
            mWarningIcon.setVisibility(VISIBLE);
        }
        else {
        	mWarningIcon.setImageResource(R.drawable.ic_msg_warning);
        	mWarningIcon.setVisibility((securityFlags != Coder.SECURITY_CLEARTEXT) ? GONE : VISIBLE);
        }

        if (mMessage.getSender() != null) {
            if (mBalloonView != null) {
	            mBalloonView.setBackgroundResource(Preferences
	                .getBalloonResource(getContext(), Messages.DIRECTION_IN));
            }
            else {
                mParentView.setGravity(Gravity.LEFT);
            }

            setGravity(Gravity.LEFT);

            if (mAvatarIncoming != null) {
                mAvatarOutgoing.setVisibility(GONE);
                mAvatarIncoming.setVisibility(VISIBLE);
                mAvatarIncoming.setImageDrawable(contact != null ?
                    contact.getAvatar(context, sDefaultContactImage) : sDefaultContactImage);
            }
        }
        else {
            if (mBalloonView != null) {
            	mBalloonView.setBackgroundResource(Preferences
                    .getBalloonResource(getContext(), Messages.DIRECTION_OUT));
            }
            else {
                mParentView.setGravity(Gravity.RIGHT);
            }

            setGravity(Gravity.RIGHT);

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
                case Messages.STATUS_PENDING:
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

        mDateView.setText(formatTimestamp());
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

        if (mMessage.isEncrypted()) {
            buf = new SpannableStringBuilder(getResources().getString(R.string.text_encrypted));
        }
        else {
            // this is used later to add \n at the end of the image placeholder
            boolean thumbnailOnly;

            TextComponent txt = (TextComponent) mMessage.getComponent(TextComponent.class);

            String textContent = txt != null ? txt.getContent() : null;

            if (TextUtils.isEmpty(textContent)) {
            	buf = new SpannableStringBuilder();
            	thumbnailOnly = true;
            }

            else {
	            buf = new SpannableStringBuilder(textContent);
	            thumbnailOnly = false;
            }

            // convert smileys first
            int c = buf.length();
            if (c > 0 && c < MAX_AFFORDABLE_SIZE)
            	MessageUtils.convertSmileys(getContext(), buf, SmileyImageSpan.SIZE_EDITABLE);

            // image component: show image before text
            AttachmentComponent attachment = (AttachmentComponent) mMessage
            		.getComponent(AttachmentComponent.class);

            if (attachment != null) {

            	if (attachment instanceof ImageComponent) {
            		ImageComponent img = (ImageComponent) attachment;

	            	// prepend some text for the ImageSpan
	            	String placeholder = CompositeMessage.getSampleTextContent(img.getContent().getMime());
	            	buf.insert(0, placeholder);

	            	// add newline if there is some text after
	            	if (!thumbnailOnly)
	            		buf.insert(placeholder.length(), "\n");

	                Bitmap bitmap = img.getBitmap();
	                if (bitmap != null) {
	                    ImageSpan imgSpan = new MaxSizeImageSpan(getContext(), bitmap);
	                    buf.setSpan(imgSpan, 0, placeholder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	                }

            	}

                else {

                	// other component: show sample content if no body was found
                	if (txt == null)
                		buf.append(CompositeMessage.getSampleTextContent(attachment.getMime()));

                }

            }

        }

        if (highlight != null) {
            Matcher m = highlight.matcher(buf.toString());
            while (m.find())
                buf.setSpan(mHighlightColorSpan, m.start(), m.end(), 0);
        }

        return buf;
    }

    private CharSequence formatTimestamp() {
        long serverTime = mMessage.getServerTimestamp();
        long ts = serverTime > 0 ? serverTime : mMessage.getTimestamp();

        return MessageUtils.formatTimeStampString(getContext(), ts, false);
    }

    public final void unbind() {
        // TODO mMessage.recycle();
        mMessage = null;
    }

    public CompositeMessage getMessage() {
        return mMessage;
    }
}
