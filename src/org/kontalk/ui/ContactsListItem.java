package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.data.Contact;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ContactsListItem extends RelativeLayout {
    //private static final String TAG = ConversationListItem.class.getSimpleName();

    private Contact mContact;
    private TextView mText1;
    private TextView mText2;
    private QuickContactBadge mAvatarView;

    static private Drawable sDefaultContactImage;

    public ContactsListItem(Context context) {
        super(context);
    }

    public ContactsListItem(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (sDefaultContactImage == null) {
            sDefaultContactImage = context.getResources().getDrawable(R.drawable.ic_contact_picture);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mText1 = (TextView) findViewById(android.R.id.text1);
        mText2 = (TextView) findViewById(android.R.id.text2);
        mAvatarView = (QuickContactBadge) findViewById(R.id.avatar);

        if (isInEditMode()) {
            mText1.setText("Test contact");
            mText2.setText("+393375423981");
            mAvatarView.setImageDrawable(sDefaultContactImage);
            mAvatarView.setVisibility(VISIBLE);
        }
    }

    public final void bind(Context context, final Contact contact) {
        mContact = contact;

        mAvatarView.assignContactUri(contact.getUri());
        mAvatarView.setImageDrawable(contact.getAvatar(context, sDefaultContactImage));
        mAvatarView.setVisibility(VISIBLE);

        mText1.setText(contact.getName());
        mText2.setText(contact.getNumber());
    }

    public final void unbind() {
        // TODO unbind (contact?)
    }

    public Contact getContact() {
        return mContact;
    }

}
