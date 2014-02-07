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

package org.kontalk.xmpp.ui;

import java.io.File;
import java.io.IOException;

import org.kontalk.xmpp.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
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
    private MediaRecorder recorder = new MediaRecorder();
    private MediaPlayer player=new MediaPlayer();
    private CircularSeekBar mHoloCircularProgressBar;
    private ObjectAnimator mProgressBarAnimator;
    private ImageView img;
    private TextView timetxt;
    protected boolean mAnimationHasEnded = false;
    private String path;
    private int check_flags;
    private float timeCircle;
    private int playerSeekTo;
    private int checkSeek;
    private static final int STATUS_IDLE=0;
    private static final int STATUS_RECORDING=1;
    private static final int STATUS_STOPPED=2;
    private static final int STATUS_PLAYING=3;
    private static final int STATUS_PAUSED=4;
    private static final int STATUS_ENDED = 5;
    private static final int MAX_DURATE=120000;
    private static final int MAX_PROGRESS=100;
    private static final int COLOR_RECORD = Color.rgb(0xDD, 0x18, 0x12);
    private static final int COLOR_PLAY = Color.rgb(0x00, 0xAC, 0xEC);

    public AudioDialog(Context context) {
        super(context);
        init();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        timetxt=(TextView) findViewById(R.id.time);
        timetxt.setVisibility(View.INVISIBLE);
        img=(ImageView) findViewById(R.id.image_audio);
        mHoloCircularProgressBar = (CircularSeekBar) findViewById(R.id.circularSeekBar);
        mHoloCircularProgressBar.getProgress();
        mHoloCircularProgressBar.setMax(MAX_PROGRESS);
        mHoloCircularProgressBar.setVisibility(View.INVISIBLE);
        getButton(Dialog.BUTTON_POSITIVE).setVisibility(View.GONE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus) {
            if (check_flags == STATUS_RECORDING)
                cancel();

            else if (check_flags == STATUS_PLAYING)
                pauseAudio();
        }
    }

    private void init() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View v=inflater.inflate(R.layout.audio_dialog, null);
        setView(v);
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                img.setImageResource(R.drawable.play);
                mProgressBarAnimator.end();
                check_flags=STATUS_ENDED;
            }
        });

        v.findViewById(R.id.image_audio).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(check_flags==STATUS_IDLE){
                    startRecord();
                }
                else if (check_flags==STATUS_RECORDING) {
                    mProgressBarAnimator.cancel();
                }
                else if (check_flags==STATUS_STOPPED) {
                    playAudio();
                }
                else if (check_flags==STATUS_PLAYING) {
                    pauseAudio();
                }
                else if (check_flags == STATUS_PAUSED || check_flags == STATUS_ENDED) {
                    resumeAudio();
                }
            }
        });

        setButton(Dialog.BUTTON_POSITIVE, "Send", (OnClickListener) null);
        setButton(Dialog.BUTTON_NEGATIVE, "Cancel", (OnClickListener) null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    private void finish() {
        if (check_flags == STATUS_RECORDING) {
            Log.w("Kontalk","Stop Riproduzione");
            stopRecord();
        }
        else if (check_flags == STATUS_PLAYING) {
            pauseAudio();
            player.release();
        }

        if (check_flags==STATUS_STOPPED || check_flags== STATUS_PAUSED) {
            Log.w("Kontalk","File Cancellato");
            File audio = new File(path);
            audio.delete();
        }
    }

    private void startRecord() {
        Log.w("Kontalk","Start Record");
        img.setImageResource(R.drawable.rec);
        mHoloCircularProgressBar.setVisibility(View.VISIBLE);
        mHoloCircularProgressBar.setCircleColor(Color.TRANSPARENT);
        mHoloCircularProgressBar.setCircleProgressColor(COLOR_RECORD);
        mHoloCircularProgressBar.setPointerColor(COLOR_RECORD);
        mHoloCircularProgressBar.setPointerBorderColor(COLOR_RECORD);
        mHoloCircularProgressBar.setPointerHaloColor(Color.TRANSPARENT);
        animate(mHoloCircularProgressBar, null, 100, MAX_DURATE);
        timetxt.setVisibility(View.VISIBLE);
        timetxt.setTextColor(COLOR_RECORD);
        path="/sdcard/record/"+System.currentTimeMillis()+".3gp";
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(path);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            recorder.prepare();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // Start recording
        recorder.start();
        check_flags=STATUS_RECORDING;

    }

    private void stopRecord() {
        Log.w("Kontalk","Registrazione Fermata");
        recorder.stop();
        recorder.reset();
        recorder.release();
        img.setImageResource(R.drawable.play);
        getButton(Dialog.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
        check_flags=STATUS_STOPPED;
        mProgressBarAnimator.end();
        timetxt.setVisibility(View.INVISIBLE);
        mHoloCircularProgressBar.setCircleProgressColor(COLOR_PLAY);
        mHoloCircularProgressBar.setPointerHaloColor(Color.TRANSPARENT);
        mHoloCircularProgressBar.setPointerColor(COLOR_PLAY);
        mHoloCircularProgressBar.setPointerBorderColor(COLOR_PLAY);
    }

    private void playAudio() {
        Log.w("Kontalk",path);
        mHoloCircularProgressBar.setClickable(true);
        try {
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(path);
            player.prepare();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        timetxt.setVisibility(View.VISIBLE);
        timetxt.setTextColor(COLOR_PLAY);
        timeCircle=(float)MAX_PROGRESS/(float)player.getDuration();
        animate(mHoloCircularProgressBar, null, 100, player.getDuration());
        resumeAudio();
    }

    private void pauseAudio() {
        img.setImageResource(R.drawable.play);
        mProgressBarAnimator.cancel();
        player.pause();
        check_flags=STATUS_PAUSED;
    }

    private void resumeAudio() {
        img.setImageResource(R.drawable.pause);
        if (check_flags==STATUS_PAUSED || check_flags == STATUS_ENDED)
            mProgressBarAnimator.start();
        if (check_flags==STATUS_PAUSED)
            mProgressBarAnimator.setCurrentPlayTime(player.getCurrentPosition());
        player.start();
        check_flags=STATUS_PLAYING;
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
                if (check_flags==STATUS_RECORDING)
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
                        if (check_flags== STATUS_RECORDING) {
                            return true;
                        }
                        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && (check_flags==STATUS_PLAYING || check_flags==STATUS_PAUSED)) {
                            progressBar.setPointerAlpha(135);
                            progressBar.setPointerAlphaOnTouch(100);
                            checkSeek=check_flags;
                            pauseAudio();
                          }
                        else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                            progressBar.setPointerAlpha(0);
                            progressBar.setPointerAlphaOnTouch(0);
                            player.seekTo(playerSeekTo);
                            if (checkSeek==STATUS_PLAYING)
                                resumeAudio();
                          }
                          else if (event.getAction() == android.view.MotionEvent.ACTION_MOVE && (check_flags==STATUS_PLAYING || check_flags==STATUS_PAUSED)) {
                            playerSeekTo = (int) (progressBar.getProgress()/timeCircle);
                            timetxt.setText(DateUtils.formatElapsedTime(playerSeekTo / 1000));
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
                timetxt.setText(DateUtils.formatElapsedTime(time / 1000));
            }
        });
        progressBar.setProgress(0);
        mProgressBarAnimator.start();
    }
}
