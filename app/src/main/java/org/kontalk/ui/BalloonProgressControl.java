package org.kontalk.ui;

import org.kontalk.message.AttachmentComponent;

/**
 * Created by andrea on 01/10/14.
 */
public interface BalloonProgressControl {
    public void setProgress(int progress, long messageId);
    public void setVisible(int visibility);
    public void startDownload(AttachmentComponent attachment);
    public void stopDownload(AttachmentComponent attachment);
}
