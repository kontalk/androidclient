package org.kontalk.ui;

import java.io.File;

/**
 * Created by andrea on 14/08/14.
 */
public interface AudioPlayerControl {
    public void buttonClick (File audioFile, AudioContentViewControl view, long messageId);
    public void playAudio(AudioContentViewControl view, long messageId);
    public void pauseAudio(AudioContentViewControl view);
    public void onBind (long messageId, AudioContentViewControl view);
    public void onUnbind(long messageId, AudioContentViewControl view);
    public void seekTo(int position);

}

