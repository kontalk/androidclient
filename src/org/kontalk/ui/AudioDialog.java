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

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.Animator.AnimatorListener;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.animation.ValueAnimator;
import com.nineoldandroids.animation.ValueAnimator.AnimatorUpdateListener;
/**
 * AudioDialog Attachments.
 * @author Andrea Cappelli & Daniele Ricci
 */


public class AudioDialog extends AlertDialog {
    private MediaRecorder mRecorder = new MediaRecorder();
    private MediaPlayer mPlayer=new MediaPlayer();
    private CircularSeekBar mHoloCircularProgressBar;
    private ObjectAnimator mProgressBarAnimator;
    private ImageView mImageButton;
    private TextView mTimeTxt;
    protected boolean mAnimationHasEnded = false;
    private File mFile;
    private int mCheckFlags;
    private float mTimeCircle;
    private int mPlayerSeekTo;
    private int mCheckSeek;
    private OnAudioDialogResult mResult;
    private static final int STATUS_IDLE=0;
    private static final int STATUS_RECORDING=1;
    private static final int STATUS_STOPPED=2;
    private static final int STATUS_PLAYING=3;
    private static final int STATUS_PAUSED=4;
    private static final int STATUS_ENDED = 5;
    private static final int STATUS_SEND = 6;
    private static final int MAX_DURATE=120000;
    private static final int MAX_PROGRESS=100;
    public static final String MIME_3GPP = "audio/3gpp";


    public AudioDialog(Context context, OnAudioDialogResult result) {
        super(context);
        mResult = result;
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
            if (mCheckFlags == STATUS_RECORDING)
                cancel();

            else if (mCheckFlags == STATUS_PLAYING)
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
                mCheckFlags=STATUS_ENDED;
            }
        });

        v.findViewById(R.id.image_audio).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(mCheckFlags==STATUS_IDLE){
                    try {
                        startRecord();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else if (mCheckFlags==STATUS_RECORDING) {
                    mProgressBarAnimator.cancel();
                }
                else if (mCheckFlags==STATUS_STOPPED) {
                    playAudio();
                }
                else if (mCheckFlags==STATUS_PLAYING) {
                    pauseAudio();
                }
                else if (mCheckFlags == STATUS_PAUSED || mCheckFlags == STATUS_ENDED) {
                    resumeAudio();
                }
            }
        });

        setButton(Dialog.BUTTON_POSITIVE, getContext().getString(R.string.send), new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mFile != null) {
                    mResult.onResult(mFile.getAbsolutePath());
                    mCheckFlags = STATUS_SEND;
                }
            }
        });
        setButton(Dialog.BUTTON_NEGATIVE, getContext().getString(R.string.cancel), new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.w("Kontalk","File Cancellato");
                mFile.delete();
            }
        });
    }

    public interface OnAudioDialogResult  {
        public void onResult (String path);
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    private void finish() {
        if (mCheckFlags == STATUS_RECORDING) {
            Log.w("Kontalk","Stop Riproduzione");
            stopRecord();
        }
        else if (mCheckFlags == STATUS_PLAYING) {
            pauseAudio();
            mPlayer.release();
        }

        if (mCheckFlags==STATUS_STOPPED || mCheckFlags== STATUS_PAUSED && mCheckFlags != STATUS_SEND) {
            Log.w("Kontalk","File Cancellato");
            mFile.delete();
        }
    }

    private void startRecord() throws IOException {
        Log.w("Kontalk","Start Record");
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
        mFile = MediaStorage.getTempAudio(getContext());
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mFile.getAbsolutePath());
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mRecorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Start recording
        mRecorder.start();
        mCheckFlags=STATUS_RECORDING;

    }

    private void stopRecord() {
        Log.w("Kontalk","Registrazione Fermata");
        mRecorder.stop();
        mRecorder.reset();
        mRecorder.release();
        mImageButton.setImageResource(R.drawable.play);
        getButton(Dialog.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
        mCheckFlags=STATUS_STOPPED;
        mProgressBarAnimator.end();
        mTimeTxt.setVisibility(View.INVISIBLE);
        mHoloCircularProgressBar.setCircleProgressColor(getContext().getResources().getColor(R.color.audio_pbar_play));
        mHoloCircularProgressBar.setPointerHaloColor(Color.TRANSPARENT);
        mHoloCircularProgressBar.setPointerColor(getContext().getResources().getColor(R.color.audio_pbar_play));
        mHoloCircularProgressBar.setPointerBorderColor(getContext().getResources().getColor(R.color.audio_pbar_play));
    }

    private void playAudio() {
        Log.w("Kontalk",mFile.getAbsolutePath());
        mHoloCircularProgressBar.setClickable(true);
        try {
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setDataSource(mFile.getAbsolutePath());
            mPlayer.prepare();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mTimeTxt.setVisibility(View.VISIBLE);
        mTimeTxt.setTextColor(getContext().getResources().getColor(R.color.audio_pbar_play));
        mTimeCircle=MAX_PROGRESS/(float)mPlayer.getDuration();
        animate(mHoloCircularProgressBar, null, 100, mPlayer.getDuration());
        resumeAudio();
    }

    private void pauseAudio() {
        mImageButton.setImageResource(R.drawable.play);
        mProgressBarAnimator.cancel();
        mPlayer.pause();
        mCheckFlags=STATUS_PAUSED;
    }

    private void resumeAudio() {
        mImageButton.setImageResource(R.drawable.pause);
        if (mCheckFlags==STATUS_PAUSED || mCheckFlags == STATUS_ENDED)
            mProgressBarAnimator.start();
        if (mCheckFlags==STATUS_PAUSED)
            mProgressBarAnimator.setCurrentPlayTime(mPlayer.getCurrentPosition());
        mPlayer.start();
        mCheckFlags=STATUS_PLAYING;
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
                if (mCheckFlags==STATUS_RECORDING)
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
                        if (mCheckFlags== STATUS_RECORDING) {
                            return true;
                        }
                        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && (mCheckFlags==STATUS_PLAYING || mCheckFlags==STATUS_PAUSED)) {
                            progressBar.setPointerAlpha(135);
                            progressBar.setPointerAlphaOnTouch(100);
                            mCheckSeek=mCheckFlags;
                            pauseAudio();
                          }
                        else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                            progressBar.setPointerAlpha(0);
                            progressBar.setPointerAlphaOnTouch(0);
                            mPlayer.seekTo(mPlayerSeekTo);
                            if (mCheckSeek==STATUS_PLAYING)
                                resumeAudio();
                          }
                          else if (event.getAction() == android.view.MotionEvent.ACTION_MOVE && (mCheckFlags==STATUS_PLAYING || mCheckFlags==STATUS_PAUSED)) {
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
