package org.kontalk.xmpp.ui;

import java.io.File;
import java.io.IOException;

import org.kontalk.xmpp.R;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import de.passsy.holocircularprogressbar.HoloCircularProgressBar;

public class AudioDialog extends AlertDialog {
    private MediaRecorder recorder = new MediaRecorder();
    private MediaPlayer player=new MediaPlayer();
    private HoloCircularProgressBar mHoloCircularProgressBar;
    private ObjectAnimator mProgressBarAnimator=null;
    private ImageView img;
    private TextView timetxt;
    protected boolean mAnimationHasEnded = false;
    private String path;
    private int check_flags;

    private static final int STATUS_IDLE=0;
    private static final int STATUS_RECORDING=1;
    private static final int STATUS_STOPPED=2;
    private static final int STATUS_PLAYING=3;
    private static final int STATUS_PAUSED=4;
    private static final int MAX_DURATE=10000;
    private static final int COLOR_RECORD = Color.rgb(0xDD, 0x18, 0x12);
    private static final int COLOR_PLAY = Color.rgb(0x76, 0xCC, 0x1E);


    public AudioDialog(Context context) {
        super(context);
        init();
    }

    private void init() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View v=inflater.inflate(R.layout.audio_dialog, null);
        setView(v);
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

            @Override
            public void onCompletion(MediaPlayer mp) {
                img.setImageResource(R.drawable.play);
                check_flags=STATUS_PAUSED;
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
                else if (check_flags == STATUS_PAUSED) {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        timetxt=(TextView) findViewById(R.id.time);
        img=(ImageView) findViewById(R.id.image_audio);
        mHoloCircularProgressBar = (HoloCircularProgressBar) findViewById(R.id.holoCircularProgressBar1);
        getButton(Dialog.BUTTON_POSITIVE).setVisibility(View.GONE);
    }

    private void startRecord() {
        Log.w("Kontalk","Start Record");
        img.setImageResource(R.drawable.rec);
        animate(mHoloCircularProgressBar, null, 1f, MAX_DURATE);
        mHoloCircularProgressBar.setProgressColor(COLOR_RECORD);
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
    }

    private void playAudio() {
        Log.w("Kontalk",path);
        try {
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
        timetxt.setTextColor(COLOR_PLAY);
        animate(mHoloCircularProgressBar, null, 1f, player.getDuration());
        path="/sdcard/record/"+System.currentTimeMillis()+".3gp";
        resumeAudio();

    }

    private void pauseAudio() {
        img.setImageResource(R.drawable.play);
        player.pause();
        check_flags=STATUS_PAUSED;
    }

    private void resumeAudio() {
        img.setImageResource(R.drawable.pause);
        player.start();
        check_flags=STATUS_PLAYING;
    }
    private void animate(final HoloCircularProgressBar progressBar, final AnimatorListener listener, final float progress, final int duration) {

        mProgressBarAnimator = ObjectAnimator.ofFloat(progressBar, "progress", progress);
        mProgressBarAnimator.setInterpolator(new LinearInterpolator());
        mProgressBarAnimator.setDuration(duration);

        mProgressBarAnimator.addListener(new AnimatorListener() {

            @Override
            public void onAnimationCancel(final Animator animation) {
                animation.end();
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                progressBar.setProgress(progress);
                mHoloCircularProgressBar.setProgressColor(COLOR_PLAY);
                if (check_flags==STATUS_RECORDING)
                    stopRecord();
            }

            @Override
            public void onAnimationRepeat(final Animator animation) {
            }

            @Override
            public void onAnimationStart(final Animator animation) {
            }
        });
        if (listener != null) {
            mProgressBarAnimator.addListener(listener);
        }
        //mProgressBarAnimator.reverse();
        mProgressBarAnimator.addUpdateListener(new AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(final ValueAnimator animation) {
                progressBar.setProgress((Float) animation.getAnimatedValue());
                long time = animation.getCurrentPlayTime();
                timetxt.setText(DateUtils.formatElapsedTime(time / 1000));
            }
        });
        progressBar.setProgress(0f);
        progressBar.setMarkerProgress(progress);
        mProgressBarAnimator.start();
    }
}
