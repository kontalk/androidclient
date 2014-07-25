package org.kontalk.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.os.Handler;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.AudioComponent;
import org.kontalk.message.AudioComponent;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Timer;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;
import java.util.regex.Pattern;


/**
 * Audio content view for {@link AudioComponent}s.
 */
public class AudioContentView extends LinearLayout
        implements MessageContentView<AudioComponent>, View.OnClickListener, Runnable {

    private AudioComponent mComponent;
    private File mAudioFile;
    private MediaPlayer mPlayer;
    private ImageButton mPlayButton;
    private SeekBar mSeekBar;
    private final Handler mHandler = new Handler();

    private static final int STATUS_IDLE = 0;
    private static final int STATUS_PLAYING = 1;
    private static final int STATUS_PAUSED = 2;
    private static final int STATUS_ENDED = 3;

    private int mStatus = STATUS_IDLE;

    public AudioContentView(Context context) {
        super(context);
    }

    public AudioContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AudioContentView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void bind(AudioComponent component, Contact contact, Pattern highlight) {
        mComponent = component;
        mPlayButton = (ImageButton) findViewById(R.id.balloon_audio_player);
        mSeekBar = (SeekBar) findViewById(R.id.balloon_audio_seekbar);
        mAudioFile = new File(String.valueOf(mComponent.getLocalUri()));

        mPlayer = new MediaPlayer();
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mPlayer.setDataSource(mAudioFile.getPath());
            mPlayer.prepare();
        }
        catch (IOException e) {

        }

        mPlayButton.setOnClickListener(this);
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mPlayButton.setBackgroundResource(R.drawable.play);
                mStatus = STATUS_ENDED;
                mPlayer.seekTo(0);
                mSeekBar.setProgress(0);
            }
        });
        mSeekBar.setMax(mPlayer.getDuration());
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                //TODO
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //TODO
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //TODO
            }
        });
    }

    public void unbind() {
        clear();
    }

    public AudioComponent getComponent() {
        return mComponent;
    }

    private void clear() {
        mComponent = null;
    }

    public static AudioContentView create(LayoutInflater inflater, ViewGroup parent) {
        return (AudioContentView) inflater.inflate(R.layout.message_content_audio,
            parent, false);
    }

    private void playAudio() {
        Log.w("PLAYER: ","PLAY");
        mPlayer.start();
        mPlayButton.setBackgroundResource(R.drawable.pause);
        mStatus = STATUS_PLAYING;
        new Thread(this).start();
    }

    private void pauseAudio() {
        Log.w("PLAYER: ","PAUSE");
        mPlayer.pause();
        mPlayButton.setBackgroundResource(R.drawable.play);
        mStatus = STATUS_PAUSED;
    }


    @Override
    public void onClick(View v) {
        if (mStatus == STATUS_PLAYING)
            pauseAudio();
        else if (mStatus == STATUS_PAUSED || mStatus == STATUS_ENDED || mStatus == STATUS_IDLE ) {
            playAudio();
        }
    }

    @Override
    public void run() {
        mSeekBar.setProgress(mPlayer.getCurrentPosition());
        mHandler.postDelayed(this, 100);
    }
}
