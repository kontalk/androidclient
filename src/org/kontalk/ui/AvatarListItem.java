package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.data.Contact.ContactCallback;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;

public abstract class AvatarListItem extends RelativeLayout implements ContactCallback {

    protected QuickContactBadge mAvatarView;

    private Handler mHandler;

    static protected Drawable sDefaultContactImage;

    public AvatarListItem(Context context) {
        super(context);
        init(context);
    }

    public AvatarListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mHandler = new Handler();

        if (sDefaultContactImage == null)
            sDefaultContactImage = context.getResources()
                .getDrawable(R.drawable.ic_contact_picture);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAvatarView = (QuickContactBadge) findViewById(R.id.avatar);

        if (isInEditMode()) {
            mAvatarView.setImageDrawable(sDefaultContactImage);
            mAvatarView.setVisibility(VISIBLE);
        }
    }

    protected void loadAvatar(Contact contact) {
        if (contact != null) {
            // we mark this with the contact's hash code for the async avatar
            mAvatarView.setTag(contact.hashCode());
            mAvatarView.assignContactUri(contact.getUri());
            mAvatarView.setImageDrawable(sDefaultContactImage);
            // laod avatar asynchronously
            contact.getAvatarAsync(getContext(), this);
        }
        else {
            mAvatarView.setTag(null);
            mAvatarView.setImageDrawable(sDefaultContactImage);
        }
    }

    @Override
    public void avatarLoaded(final Contact contact, final Drawable avatar) {
        if (avatar != null) {
            if (mHandler.getLooper().getThread() != Thread.currentThread()) {
                mHandler.post(new Runnable() {
                    public void run() {
                        updateAvatar(contact, avatar);
                    }
                });
            }
            else {
                updateAvatar(contact, avatar);
            }
        }
    }

    private void updateAvatar(Contact contact, Drawable avatar) {
        try {
            // be sure the contact is still the same
            // this is an insane workaround against race conditions
            Integer contactTag = (Integer) mAvatarView.getTag();
            if (contactTag != null && contactTag.intValue() == contact.hashCode())
                mAvatarView.setImageDrawable(avatar);
        }
        catch (Exception e) {
            // we are deliberately ignoring any exception here
            // because an error here could happen only if something
            // weird is happening, e.g. user leaving the activity
        }
    }

}
