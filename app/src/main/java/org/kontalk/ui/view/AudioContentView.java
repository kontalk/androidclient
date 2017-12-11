/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.ui.view;

import java.io.File;
import java.util.regex.Pattern;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.message.AudioComponent;


/**
 * Audio content view for {@link AudioComponent}s.
 */
public class AudioContentView extends RelativeLayout
        implements MessageContentView<AudioComponent>, View.OnClickListener,
        AudioContentViewControl {

    private AudioComponent mComponent;
    private ImageButton mPlayButton;
    private SeekBar mSeekBar;
    private ImageView mDownloadButton;
    private TextView mTime;
    private StringBuilder mTimeBuilder = new StringBuilder();
    private int mDuration = -1;

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

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mPlayButton = findViewById(R.id.balloon_audio_player);
        mSeekBar = findViewById(R.id.balloon_audio_seekbar);
        mDownloadButton = findViewById(R.id.balloon_audio_download);
        mTime = findViewById(R.id.balloon_audio_time);

        if (isInEditMode()) {
            mDownloadButton.setVisibility(GONE);
        }
    }

    @Override
    public void bind(long messageId, AudioComponent component, Pattern highlight) {
        mComponent = component;
        mMessageId = messageId;

        boolean fetched = component.getLocalUri() != null;
        mPlayButton.setVisibility(fetched ? VISIBLE : GONE);
        mSeekBar.setVisibility(fetched ? VISIBLE : GONE);
        mDownloadButton.setVisibility(fetched ? GONE : VISIBLE);
        mTime.setVisibility(fetched ? VISIBLE : GONE);

        updatePosition(-1);
        mSeekBar.setMax(getAudioDuration());
        mPlayButton.setOnClickListener(this);
        mAudioPlayerControl.onBind(messageId, this);
    }

    @Override
    public void unbind() {
        clear();
        mAudioPlayerControl.onUnbind(mMessageId, this);
    }

    @Override
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

    // FIXME this is crap
    private void setTheme(MessageListItemTheme theme) {
        if (theme.isFullWidth()) {
            RelativeLayout.LayoutParams params = (LayoutParams) mSeekBar.getLayoutParams();
            params.width = LayoutParams.MATCH_PARENT;
            // no layout has been requested yet - mSeekBar.setLayoutParams(params);
        }
    }

    @Override
    public void onClick(View v) {
        Uri localUri = mComponent.getLocalUri();
        if (localUri != null)
            mAudioPlayerControl.buttonClick(new File(localUri.getPath()), this, mMessageId);
    }

    @Override
    public void prepare(int duration) {
        mSeekBar.setMax(duration);
        mDuration = duration;
    }

    @Override
    public void play() {
        mPlayButton.setBackgroundResource(R.drawable.pause_balloon);
    }

    @Override
    public void pause() {
        mPlayButton.setBackgroundResource(R.drawable.play_balloon);
    }

    @Override
    public void updatePosition(int position) {
        mSeekBar.setProgress(position < 0 ? 0 : position);
        setTimeText(position < 0 ? getAudioDuration() : position);
    }

    @Override
    public void end() {
        mPlayButton.setBackgroundResource(R.drawable.play_balloon);
        updatePosition(-1);
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
                    if (mAudioPlayerControl.isPlaying())
                        mAudioPlayerControl.playAudio(AudioContentView.this, mMessageId);
                }
            } : null;
        mSeekBar.setOnSeekBarChangeListener(listener);
    }

    public long getMessageId() {
        return mMessageId;
    }

    @Override
    public int getPosition() {
        return mSeekBar.getProgress();
    }

    private int getAudioDuration(Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(getContext(), uri);
                String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                return Integer.parseInt(time);
            }
            catch (Exception a) {
                // ignored
            }
        }
        return -1;
    }

    private int getAudioDuration() {
        if (mDuration < 0) {
            Uri uri = mComponent.getLocalUri();
            if (uri != null) {
                mDuration = getAudioDuration(uri);
            }
            else {
                mDuration = -1;
            }
        }
        return mDuration;
    }

    private void setTimeText(long duration) {
        if (duration < 0)
            mTime.setVisibility(GONE);
        else {
            DateUtils.formatElapsedTime(mTimeBuilder, (long) Math.floor((double) duration / 1000));
            mTime.setText(mTimeBuilder);
            mTime.setVisibility(VISIBLE);
        }
    }

    public static AudioContentView create(LayoutInflater inflater, ViewGroup parent, AudioPlayerControl control, MessageListItemTheme theme) {
        AudioContentView view = (AudioContentView) inflater.inflate(R.layout.message_content_audio,
                parent, false);
        if (view != null) {
            view.mAudioPlayerControl = control;
            view.setTheme(theme);
        }
        return view;
    }

}
