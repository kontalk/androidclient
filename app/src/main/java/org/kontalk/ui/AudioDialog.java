/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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
import java.io.IOException;

import org.kontalk.R;
import org.kontalk.util.MediaStorage;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.Animator.AnimatorListener;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.animation.ValueAnimator;
import com.nineoldandroids.animation.ValueAnimator.AnimatorUpdateListener;


/**
 * Audio message recording dialog.
 * @author Andrea Cappelli
 * @author Daniele Ricci
 */
public class AudioDialog extends AlertDialog {
    private static final String TAG = ComposeMessage.TAG;

    public static final String DEFAULT_MIME = "audio/3gpp";

    private static final int STATUS_IDLE = 0;
    private static final int STATUS_RECORDING = 1;
    private static final int STATUS_STOPPED = 2;
    private static final int STATUS_PLAYING = 3;
    private static final int STATUS_PAUSED = 4;
    private static final int STATUS_ENDED = 5;
    private static final int STATUS_SEND = 6;

    private static final int MAX_DURATE = 120000;
    private static final int MAX_PROGRESS = 100;

    private MediaRecorder mRecorder = new MediaRecorder();
    private MediaPlayer mPlayer = new MediaPlayer();

    private CircularSeekBar mHoloCircularProgressBar;
    private ObjectAnimator mProgressBarAnimator;
    private ImageView mImageButton;
    private TextView mTimeTxt;

    private File mFile;

    /** The current status. */
    private int mStatus;

    /** Holds the status while dragging the circular progress bar. */
    private int mCheckSeek;

    private float mTimeCircle;
    private int mPlayerSeekTo;
    private AudioDialogListener mListener;

    public AudioDialog(Context context, AudioDialogListener result) {
        super(context);
        mListener = result;
        init();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTimeTxt=(TextView) findViewById(R.id.time);
        mTimeTxt.setVisibility(View.INVISIBLE);
        mImageButton=(ImageView) findViewById(R.id.image_audio);
        mHoloCircularProgressBar = (CircularSeekBar) findViewById(R.id.circularSeekBar);
        mHoloCircularProgressBar.getProgress();
        mHoloCircularProgressBar.setMax(MAX_PROGRESS);
        mHoloCircularProgressBar.setVisibility(View.INVISIBLE);
        getButton(Dialog.BUTTON_POSITIVE).setVisibility(View.GONE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus) {
            if (mStatus == STATUS_RECORDING)
                cancel();

            else if (mStatus == STATUS_PLAYING)
                pauseAudio();
        }
    }

    private void init() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View v=inflater.inflate(R.layout.audio_dialog, null);
        setView(v);
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mImageButton.setImageResource(R.drawable.play);
                mProgressBarAnimator.end();
                mStatus = STATUS_ENDED;
            }
        });

        v.findViewById(R.id.image_audio).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mStatus == STATUS_IDLE){
                    try {
                        startRecord();
                    }
                    catch (IOException e) {
                        Log.e (TAG, "error starting audio recording: ", e);
                        // TODO i18n
                        Toast.makeText(getContext(), "Unable to start recording.", Toast.LENGTH_SHORT).show();
                    }
                }
                else if (mStatus == STATUS_RECORDING) {
                    mProgressBarAnimator.cancel();
                }
                else if (mStatus == STATUS_STOPPED) {
                    playAudio();
                }
                else if (mStatus == STATUS_PLAYING) {
                    pauseAudio();
                }
                else if (mStatus == STATUS_PAUSED || mStatus == STATUS_ENDED) {
                    resumeAudio();
                }
            }
        });

        setButton(Dialog.BUTTON_POSITIVE, getContext().getString(R.string.send), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mFile != null) {
                    mPlayer.setOnCompletionListener(null);
                    mListener.onRecordingSuccessful(mFile);
                    mStatus = STATUS_SEND;
                }
            }
        });
        setButton(Dialog.BUTTON_NEGATIVE, getContext().getString(android.R.string.cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mFile != null)
                    mFile.delete();
            }
        });
    }

    public interface AudioDialogListener {
        public void onRecordingSuccessful(File file);
        public void onRecordingCancel();
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    private void finish() {
        if (mStatus == STATUS_RECORDING) {
            stopRecord();
        }
        else if (mStatus == STATUS_PLAYING || mStatus == STATUS_SEND) {
            pauseAudio(mStatus == STATUS_SEND);
            mPlayer.release();
        }

        if (mStatus != STATUS_SEND && mFile != null) {
            mFile.delete();
        }
        mListener.onRecordingCancel();
    }

    private void startRecord() throws IOException {
        mFile = MediaStorage.getTempAudio(getContext());
        mImageButton.setImageResource(R.drawable.rec);
        mHoloCircularProgressBar.setVisibility(View.VISIBLE);
        mHoloCircularProgressBar.setCircleColor(Color.TRANSPARENT);
        mHoloCircularProgressBar.setCircleProgressColor(getContext().getResources().getColor(R.color.audio_pbar_record));
        mHoloCircularProgressBar.setPointerColor(getContext().getResources().getColor(R.color.audio_pbar_record));
        mHoloCircularProgressBar.setPointerBorderColor(getContext().getResources().getColor(R.color.audio_pbar_record));
        mHoloCircularProgressBar.setPointerHaloColor(Color.TRANSPARENT);
        animate(mHoloCircularProgressBar, null, 100, MAX_DURATE);
        mTimeTxt.setVisibility(View.VISIBLE);
        mTimeTxt.setTextColor(getContext().getResources().getColor(R.color.audio_pbar_record));
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mFile.getAbsolutePath());
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mRecorder.prepare();
            // Start recording
            mRecorder.start();
            mStatus = STATUS_RECORDING;
        }
        catch (IllegalStateException e) {
            Log.e (TAG, "error starting audio recording:", e);
        }
        catch (IOException e) {
            Log.e (TAG, "error writing on external storage:", e);
            cancel();
            new Builder(getContext())
                .setMessage(R.string.err_audio_record_writing)
                .setNegativeButton(android.R.string.ok, null)
                .show();
        }
        catch (RuntimeException e) {
            Log.e (TAG, "error starting audio recording:", e);
            cancel();
            new AlertDialog.Builder(getContext())
                .setMessage(R.string.err_audio_record)
                .setNegativeButton(android.R.string.ok, null)
                .show();
        }
    }

    private void stopRecord() {
        mRecorder.stop();
        mRecorder.reset();
        mRecorder.release();
        mImageButton.setImageResource(R.drawable.play);
        getButton(Dialog.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
        mStatus = STATUS_STOPPED;
        mProgressBarAnimator.end();
        mTimeTxt.setVisibility(View.INVISIBLE);
        mHoloCircularProgressBar.setCircleProgressColor(getContext().getResources().getColor(R.color.audio_pbar_play));
        mHoloCircularProgressBar.setPointerHaloColor(Color.TRANSPARENT);
        mHoloCircularProgressBar.setPointerColor(getContext().getResources().getColor(R.color.audio_pbar_play));
        mHoloCircularProgressBar.setPointerBorderColor(getContext().getResources().getColor(R.color.audio_pbar_play));
    }

    private void playAudio() {
        mHoloCircularProgressBar.setClickable(true);
        try {
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setDataSource(mFile.getAbsolutePath());
            mPlayer.prepare();
        }
        catch (IllegalArgumentException e) {
            Log.e (TAG, "error playing audio:", e);
        }
        catch (SecurityException e) {
            Log.e (TAG, "error playing audio:", e);
        }
        catch (IllegalStateException e) {
            Log.e (TAG, "error playing audio:", e);
        }
        catch (IOException e) {
            Log.e (TAG, "error reading from external storage", e);
            new AlertDialog.Builder(getContext())
            .setMessage(R.string.err_playing_sdcard)
            .setNegativeButton(android.R.string.ok, null)
            .show();
        }
        mTimeTxt.setVisibility(View.VISIBLE);
        mTimeTxt.setTextColor(getContext().getResources().getColor(R.color.audio_pbar_play));
        mTimeCircle = MAX_PROGRESS/(float)mPlayer.getDuration();
        animate(mHoloCircularProgressBar, null, 100, mPlayer.getDuration());
        resumeAudio();
    }

    private void pauseAudio() {
        pauseAudio(false);
    }

    private void pauseAudio(boolean sending) {
        mImageButton.setImageResource(R.drawable.play);
        mProgressBarAnimator.cancel();
        mPlayer.pause();
        if (!sending)
            mStatus = STATUS_PAUSED;
    }

    private void resumeAudio() {
        mImageButton.setImageResource(R.drawable.pause);
        if (mStatus == STATUS_PAUSED || mStatus == STATUS_ENDED)
            mProgressBarAnimator.start();
        if (mStatus == STATUS_PAUSED)
            mProgressBarAnimator.setCurrentPlayTime(mPlayer.getCurrentPosition());
        mPlayer.start();
        mStatus=STATUS_PLAYING;
    }

    private void animate(final CircularSeekBar progressBar, final AnimatorListener listener, final float progress, final int duration) {
        mProgressBarAnimator = ObjectAnimator.ofFloat(progressBar, "progress", progress);
        mProgressBarAnimator.setInterpolator(new LinearInterpolator());
        mProgressBarAnimator.setDuration(duration);

        mProgressBarAnimator.addListener(new AnimatorListener() {

            @Override
            public void onAnimationCancel(final Animator animation) {
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                if (mStatus == STATUS_RECORDING)
                    stopRecord();
            }

            @Override
            public void onAnimationRepeat(final Animator animation) {
            }

            @Override
            public void onAnimationStart(final Animator animation) {
                progressBar.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (mStatus == STATUS_RECORDING) {
                            return true;
                        }
                        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && (mStatus == STATUS_PLAYING || mStatus == STATUS_PAUSED)) {
                            progressBar.setPointerAlpha(135);
                            progressBar.setPointerAlphaOnTouch(100);
                            mCheckSeek = mStatus;
                            pauseAudio();
                          }
                        else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                            progressBar.setPointerAlpha(0);
                            progressBar.setPointerAlphaOnTouch(0);
                            mPlayer.seekTo(mPlayerSeekTo);
                            if (mCheckSeek == STATUS_PLAYING)
                                resumeAudio();
                          }
                          else if (event.getAction() == android.view.MotionEvent.ACTION_MOVE && (mStatus == STATUS_PLAYING || mStatus == STATUS_PAUSED)) {
                            mPlayerSeekTo = (int) (progressBar.getProgress()/mTimeCircle);
                            mTimeTxt.setText(DateUtils.formatElapsedTime(mPlayerSeekTo / 1000));
                          }
                        return false;
                    }
                });
            }
        });
        if (listener != null) {
            mProgressBarAnimator.addListener(listener);
        }
        mProgressBarAnimator.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(final ValueAnimator animation) {
                progressBar.setProgress((Float) animation.getAnimatedValue());
                long time = animation.getCurrentPlayTime();
                mTimeTxt.setText(DateUtils.formatElapsedTime(time / 1000));
            }
        });
        progressBar.setProgress(0);
        mProgressBarAnimator.start();
    }
}
