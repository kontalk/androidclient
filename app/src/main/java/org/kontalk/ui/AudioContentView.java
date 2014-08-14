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

import java.io.File;
import java.util.regex.Pattern;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.AudioComponent;


/**
 * Audio content view for {@link AudioComponent}s.
 */
public class AudioContentView extends LinearLayout
        implements MessageContentView<AudioComponent>, View.OnClickListener,
        AudioContentViewControl {

    static final String TAG = AudioContentView.class.getSimpleName();

    private AudioComponent mComponent;
    private ImageButton mPlayButton;
    private SeekBar mSeekBar;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_PLAYING = 1;
    public static final int STATUS_PAUSED = 2;
    public static final int STATUS_ENDED = 3;

    private long mMessageId;

    private AudioPlayerControl mAudioPlayerControl;

    public AudioContentView(Context context) {
        super(context);
    }

    public AudioContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public AudioContentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void bind(long messageId, AudioComponent component, Contact contact, Pattern highlight) {
        mComponent = component;
        mMessageId = messageId;
        mPlayButton = (ImageButton) findViewById(R.id.balloon_audio_player);
        mSeekBar = (SeekBar) findViewById(R.id.balloon_audio_seekbar);
        mPlayButton.setOnClickListener(this);
        mAudioPlayerControl.onBind(messageId, this);
    }

    public void unbind() {
        clear();
        mAudioPlayerControl.onUnbind(mMessageId, this);
    }

    public AudioComponent getComponent() {
        return mComponent;
    }

    @Override
    public int getPriority() {
        return 5;
    }

    private void clear() {
        mComponent = null;
    }

    public static AudioContentView create(LayoutInflater inflater, ViewGroup parent) {
        return (AudioContentView) inflater.inflate(R.layout.message_content_audio,
            parent, false);
    }

    @Override
    public void onClick(View v) {
        mAudioPlayerControl.buttonClick(new File(String.valueOf(mComponent.getLocalUri())), this, mMessageId);
    }

    public void  setAudioPlayerControl (AudioPlayerControl apc) {
        mAudioPlayerControl = apc;
    }

    @Override
    public void prepare(int duration) {
        mSeekBar.setMax(duration);
    }

    @Override
    public void play() {
        mPlayButton.setBackgroundResource(R.drawable.pause);
    }

    @Override
    public void pause() {
        mPlayButton.setBackgroundResource(R.drawable.play);
    }

    @Override
    public void updatePosition(int position) {
        mSeekBar.setProgress(position);
    }

    @Override
    public void end() {
        mPlayButton.setBackgroundResource(R.drawable.play);
        mSeekBar.setProgress(0);
    }

    @Override
    public void setProgressChangeListener(boolean enable) {
        SeekBar.OnSeekBarChangeListener listener = enable ?
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        mAudioPlayerControl.seekTo(progress);
                        updatePosition(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    mAudioPlayerControl.pauseAudio(AudioContentView.this);
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    mAudioPlayerControl.playAudio(AudioContentView.this, mMessageId);
                }
            } : null;
        mSeekBar.setOnSeekBarChangeListener(listener);
    }

    public long getMessageId() {
        return mMessageId;
    }

}
