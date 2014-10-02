package org.kontalk.ui;

import android.widget.ImageView;

import org.kontalk.message.AttachmentComponent;

/**
 * Created by andrea on 30/09/14.
 */
public interface BalloonProgress {
    public void buttonClick(long messageId, BalloonProgressControl balloonProgressControl,
                            AttachmentComponent attachment, ImageView button);
    public void onBind(BalloonProgressControl balloonProgressControl, long messageId);
    public void onUnBind(BalloonProgressControl balloonProgressControl, long messageId);
}
