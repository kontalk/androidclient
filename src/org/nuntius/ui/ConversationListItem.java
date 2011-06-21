package org.nuntius.ui;

import org.nuntius.R;
import org.nuntius.data.Contact;
import org.nuntius.data.Conversation;
import org.nuntius.provider.MyMessages.Messages;
import org.nuntius.util.MessageUtils;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ConversationListItem extends RelativeLayout {
    //private static final String TAG = ConversationListItem.class.getSimpleName();

    private static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);

    private Conversation mConversation;
    private TextView mSubjectView;
    private TextView mFromView;
    private TextView mDateView;
    //private View mAttachmentView;
    private ImageView mErrorIndicator;
    //private ImageView mPresenceView;
    private QuickContactBadge mAvatarView;

    static private Drawable sDefaultContactImage;

    public ConversationListItem(Context context) {
        super(context);
    }

    public ConversationListItem(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (sDefaultContactImage == null) {
            sDefaultContactImage = context.getResources().getDrawable(R.drawable.ic_contact_picture);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mFromView = (TextView) findViewById(R.id.from);
        mSubjectView = (TextView) findViewById(R.id.subject);

        mDateView = (TextView) findViewById(R.id.date);
        //mAttachmentView = findViewById(R.id.attachment);
        mErrorIndicator = (ImageView) findViewById(R.id.error);
        //mPresenceView = (ImageView) findViewById(R.id.presence);
        mAvatarView = (QuickContactBadge) findViewById(R.id.avatar);
    }

    /**
     * Only used for header binding.
     */
    public void bind(String title, String explain) {
        mFromView.setText(title);
        mSubjectView.setText(explain);
    }

    public final void bind(Context context, final Conversation conv) {
        mConversation = conv;
        String recipient = null;

        Contact contact = mConversation.getContact();

        if (contact != null) {
            recipient = contact.getName();
            mAvatarView.assignContactUri(contact.getUri());
            mAvatarView.setImageDrawable(contact.getAvatar(getContext(), sDefaultContactImage));
        }
        else {
            recipient = conv.getRecipient();
            mAvatarView.setImageDrawable(sDefaultContactImage);
        }
        mAvatarView.setVisibility(View.VISIBLE);

        SpannableStringBuilder from = new SpannableStringBuilder(recipient);
        if (conv.getMessageCount() > 1)
            from.append(" (" + conv.getMessageCount() + ") ");

        if (conv.getUnreadCount() > 0)
            from.setSpan(STYLE_BOLD, 0, from.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

        mFromView.setText(from);
        mDateView.setText(MessageUtils.formatTimeStampString(context, conv.getDate()));
        mSubjectView.setText(conv.getSubject());

        // error indicator
        int resId = -1;
        switch (conv.getStatus()) {
            case Messages.STATUS_SENDING:
                resId = R.drawable.ic_msg_pending;
                break;
            case Messages.STATUS_SENT:
                resId = R.drawable.ic_msg_sent;
                break;
            case Messages.STATUS_RECEIVED:
                resId = R.drawable.ic_msg_delivered;
                break;
            case Messages.STATUS_ERROR:
            case Messages.STATUS_NOTACCEPTED:
                resId = R.drawable.ic_msg_error;
                break;
        }

        if (resId < 0) {
            mErrorIndicator.setVisibility(INVISIBLE);
        }
        else {
            mErrorIndicator.setVisibility(VISIBLE);
            mErrorIndicator.setImageResource(resId);
        }
    }

    public final void unbind() {
        // TODO unbind (contact?)
    }

    public Conversation getConversation() {
        return mConversation;
    }

}
