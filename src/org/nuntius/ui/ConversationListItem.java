package org.nuntius.ui;

import org.nuntius.R;
import org.nuntius.data.Contact;
import org.nuntius.data.Conversation;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ConversationListItem extends RelativeLayout {
    private static final String TAG = "ConversationListItem";

    private Conversation mConversation;
    private TextView mSubjectView;
    private TextView mFromView;
    private TextView mDateView;
    private View mAttachmentView;
    private View mErrorIndicator;
    private ImageView mPresenceView;
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
        mAttachmentView = findViewById(R.id.attachment);
        mErrorIndicator = findViewById(R.id.error);
        mPresenceView = (ImageView) findViewById(R.id.presence);
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

        StringBuilder from = new StringBuilder(recipient);
        if (conv.getMessageCount() > 1)
            from.append(" (" + conv.getMessageCount() + ") ");

        mFromView.setText(from);
        mDateView.setText(MessageUtils.formatTimeStampString(context, conv.getDate()));
        mSubjectView.setText(conv.getSubject());
    }

    public final void unbind() {
        // TODO unbind (contact?)
    }

    public Conversation getConversation() {
        return mConversation;
    }

}
