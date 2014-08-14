package org.kontalk.ui;

/**
* Created by andrea on 14/08/14.
*/
public interface AudioContentViewControl {
    public void setProgressChangeListener(boolean enabled);
    public void prepare(int duration);
    public void play();
    public void pause();
    public void updatePosition(int position);
    public void end();
    // TODO remove me
    public long getMessageId();

}
