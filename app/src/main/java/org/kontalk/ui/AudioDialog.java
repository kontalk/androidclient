/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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
import java.util.concurrent.TimeUnit;

import com.afollestad.materialdialogs.MaterialDialog;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.ui.view.CircularSeekBar;
import org.kontalk.util.AudioRecording;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.Permissions;


/**
 * Audio message recording dialog.
 * @author Andrea Cappelli
 * @author Daniele Ricci
 */
public class AudioDialog extends AlertDialog {
    static final String TAG = ComposeMessage.TAG;

    private static final String STATE_PREFIX = "AudioDialog_";

    public static final String DEFAULT_MIME = "audio/3gpp";

    private static final int STATUS_IDLE = 0;
    private static final int STATUS_RECORDING = 1;
    private static final int STATUS_STOPPED = 2;
    private static final int STATUS_PLAYING = 3;
    private static final int STATUS_PAUSED = 4;
    private static final int STATUS_ENDED = 5;
    private static final int STATUS_SEND = 6;

    /** Max duration of recorded audio in milliseconds. */
    private static final long MAX_AUDIO_DURATION = TimeUnit.MINUTES.toMillis(5);
    private static final int MAX_PROGRESS = 100;

    static final String MAX_AUDIO_DURATION_TEXT = DateUtils
        .formatElapsedTime(MAX_AUDIO_DURATION / 1000);

    private CircularSeekBar mProgressBar;
    ObjectAnimator mProgressBarAnimator;
    ImageView mImageButton;
    TextView mTimeTxt;
    private TextView mHintTxt;

    /** Flag indicating that we are stopping due to activity lifecycle. */
    private boolean mSaved;

    File mFile;

    /** The current status. */
    int mStatus;

    /** Holds the status while dragging the circular progress bar. */
    int mCheckSeek;

    float mTimeCircle;
    int mPlayerSeekTo;
    AudioDialogListener mListener;
    AudioFragment mData;

    public AudioDialog(Context context, AudioFragment data, AudioDialogListener result) {
        super(context);
        mListener = result;
        mData = data;
        init();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTimeTxt = findViewById(R.id.time);
        mTimeTxt.setText(DateUtils.formatElapsedTime(0));
        mHintTxt = findViewById(R.id.hint);
        mImageButton = findViewById(R.id.image_audio);
        mProgressBar = findViewById(R.id.circularSeekBar);
        mProgressBar.setMax(MAX_PROGRESS);
        mProgressBar.setVisibility(View.INVISIBLE);
        getButton(Dialog.BUTTON_POSITIVE).setVisibility(View.GONE);

        Activity context = getOwnerActivity();
        if (context == null) {
            dismiss();
        }
        else if (!Permissions.canRecordAudio(context)) {
            Permissions.requestRecordAudio(context, context
                .getString(R.string.err_audio_or_storage_denied));
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus) {
            if (mStatus == STATUS_RECORDING)
                stopRecord();

            else if (mStatus == STATUS_PLAYING)
                pauseAudio();
        }
    }

    /** Used for saving dialog state on destroy/recreate cycles. */
    public void onSaveInstanceState(Bundle out) {
        out.putInt(STATE_PREFIX + "status", mStatus);
        if (mFile != null) {
            out.putString(STATE_PREFIX + "file", mFile.toString());
        }
        mSaved = true;
    }

    /** Used for restoring dialog state on destroy/recreate cycles. */
    public static AudioDialog onRestoreInstanceState(Context context, Bundle state,
            AudioFragment data, AudioDialogListener result) {

        if (state != null && state.getInt(STATE_PREFIX + "status", -1) >= 0) {
            AudioDialog dialog = new AudioDialog(context, data, result);
            dialog.mStatus = state.getInt(STATE_PREFIX + "status");
            String file = state.getString(STATE_PREFIX + "file");
            if (file != null) {
                dialog.mFile = new File(file);
            }

            return dialog;
        }

        return null;
    }

    private void init() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        @SuppressLint("InflateParams")
        View v = inflater.inflate(R.layout.audio_dialog, null);
        setView(v);
        mData.setListener(new AudioFragment.AudioFragmentListener() {
            @Override
            public void onCompletion(AudioFragment audio) {
                mImageButton.setImageResource(R.drawable.play);
                mProgressBarAnimator.end();
                mStatus = STATUS_ENDED;
            }

            @Override
            public void onAudioFocusLost(AudioFragment audio) {
                pauseAudio();
            }

            @Override
            public void onPause(AudioFragment audio) {
                mImageButton.setImageResource(R.drawable.play);
                mProgressBarAnimator.end();
                mStatus = STATUS_PAUSED;
            }

            @Override
            public void onError(AudioFragment audio) {
                mImageButton.setImageResource(R.drawable.play);
                mProgressBarAnimator.end();
                mStatus = STATUS_ENDED;
                Toast.makeText(getContext(), R.string.err_playing_audio, Toast.LENGTH_LONG)
                    .show();
            }
        });

        v.findViewById(R.id.image_audio).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mStatus == STATUS_IDLE){
                    try {
                        startRecord();
                    }
                    catch (IOException e) {
                        Log.e (TAG, "error writing audio recording", e);
                        Toast.makeText(getContext(), R.string.err_audio_record_writing, Toast.LENGTH_SHORT).show();
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
                    mData.setListener(null);
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
        void onRecordingSuccessful(File file);

        void onRecordingCancel();
    }

    @Override
    protected void onStart() {
        super.onStart();
        switch (mStatus) {
            case STATUS_RECORDING:
                setupViewForRecording(calculateRecordingProgress());
                break;
            case STATUS_STOPPED:
            case STATUS_PAUSED:
            case STATUS_ENDED:
                // restart
                animate(mProgressBar, null, 0, MAX_PROGRESS, mData.getPlayerDuration());
                setupViewForPlaying(0, mStatus);
                mData.seekPlayerTo(0);
                break;
            case STATUS_PLAYING:
                // restore animator
                float progress = calculatePlayingProgress();
                animate(mProgressBar, null, progress, MAX_PROGRESS, mData.getPlayerDuration());
                setupViewForPlaying(progress);
                setupForPlaying();
                resumeAudio();
                break;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    private void finish() {
        if (mSaved)
            return;

        if (mStatus == STATUS_RECORDING) {
            stopRecord();
        }
        else if (mStatus == STATUS_PLAYING || mStatus == STATUS_SEND) {
            pauseAudio(mStatus == STATUS_SEND);
            mData.finish(true);
        }

        if (mStatus != STATUS_SEND && mFile != null) {
            mFile.delete();
        }
        mListener.onRecordingCancel();
        mData.finish();
    }

    private float calculateRecordingProgress() {
        long time = mData.getElapsedTime();
        return (float) (time * 100) / MAX_AUDIO_DURATION;
    }

    private float calculatePlayingProgress() {
        long time = mData.getElapsedTime();
        return (float) (time * 100) / mData.getPlayerDuration();
    }

    @SuppressLint("ResourceAsColor")
    private void setupViewForRecording(float progress) {
        mImageButton.setImageResource(R.drawable.rec);
        setViewsColor(R.color.audio_pbar_record);
        mProgressBar.setVisibility(View.VISIBLE);
        animate(mProgressBar, null, progress, MAX_PROGRESS, MAX_AUDIO_DURATION);
        mTimeTxt.setVisibility(View.VISIBLE);
        mHintTxt.setVisibility(View.GONE);
    }

    private void setupViewForPlaying(float progress) {
        setupViewForPlaying(progress, -1);
    }

    @SuppressLint("ResourceAsColor")
    private void setupViewForPlaying(float progress, int overrideStatus) {
        mProgressBar.setVisibility(View.VISIBLE);
        // set play icon
        mImageButton.setImageResource(R.drawable.play);
        // show send button
        getButton(Dialog.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
        // stop animation and hide timer text
        if (progress == 0) {
            // set status to stopped to avoid loops in the animator
            mStatus = overrideStatus >= 0 ? overrideStatus : STATUS_STOPPED;
            mProgressBarAnimator.end();
        }
        mTimeTxt.setVisibility(View.INVISIBLE);
        mHintTxt.setVisibility(View.GONE);
        // set UI colors
        setViewsColor(R.color.audio_pbar_play);
    }

    private void setViewsColor(int resId) {
        int color = ContextCompat.getColor(getContext(), resId);
        mProgressBar.setCircleProgressColor(color);
        mProgressBar.setPointerColor(color);
        mProgressBar.setPointerBorderColor(color);
        mTimeTxt.setTextColor(color);
    }

    /**
     * Begins recording audio.
     * @throws IOException if writing to storage failed
     */
    void startRecord() throws IOException {
        mFile = MediaStorage.getOutgoingAudioFile(getContext());
        setupViewForRecording(0);

        try {
            AudioRecording.setupMediaRecorder(mData.getRecorder(), mFile);
            // start recording
            mData.startRecording();
            mStatus = STATUS_RECORDING;
        }
        catch (IllegalStateException e) {
            Log.e(TAG, "error starting audio recording", e);
        }
        catch (IOException e) {
            Log.e(TAG, "error writing on external storage", e);
            cancel();

            int resId;
            if (Permissions.canWriteExternalStorage(getContext())) {
                resId = R.string.err_audio_record_writing;
            }
            else {
                resId = R.string.err_audio_record_writing_permission;
            }

            new MaterialDialog.Builder(getContext())
                .content(resId)
                .positiveText(android.R.string.ok)
                .show();
        }
        catch (RuntimeException e) {
            Log.e(TAG, "error starting audio recording", e);
            cancel();

            int resId;
            if (Permissions.canRecordAudioOnly(getContext())) {
                resId = R.string.err_audio_record;
            }
            else {
                resId = R.string.err_audio_record_permission;
            }

            new MaterialDialog.Builder(getContext())
                .content(resId)
                .positiveText(android.R.string.ok)
                .show();
        }
    }

    void stopRecord() {
        try {
            // stop recorder
            mData.stopRecording();
        }
        catch (RuntimeException e) {
            Log.e(TAG, "error recording audio", e);
            cancel();
            new MaterialDialog.Builder(getContext())
                .content(R.string.err_audio_record)
                .positiveText(android.R.string.ok)
                .show();
        }
        setupViewForPlaying(0);
        // stopped!
        mStatus = STATUS_STOPPED;
    }

    void playAudio() {
        mProgressBar.setClickable(true);
        try {
            mData.preparePlayer(mFile);
        }
        catch (IOException e) {
            Log.e (TAG, "error reading from external storage", e);
            new MaterialDialog.Builder(getContext())
                .content(R.string.err_playing_sdcard)
                .positiveText(android.R.string.ok)
                .show();
        }
        catch (Exception e) {
            Log.e(TAG, "error playing audio", e);
        }
        setupForPlaying();
        animate(mProgressBar, null, 0, MAX_PROGRESS, mData.getPlayerDuration());
        resumeAudio();
    }

    private void setupForPlaying() {
        mHintTxt.setVisibility(View.GONE);
        mTimeTxt.setVisibility(View.VISIBLE);
        int color = ContextCompat.getColor(getContext(), R.color.audio_pbar_play);
        mTimeTxt.setTextColor(color);
        mTimeCircle = MAX_PROGRESS / (float) mData.getPlayerDuration();
    }

    void pauseAudio() {
        pauseAudio(false);
    }

    private void pauseAudio(boolean sending) {
        mImageButton.setImageResource(R.drawable.play);
        mProgressBarAnimator.cancel();
        mData.pausePlaying();
        if (!sending)
            mStatus = STATUS_PAUSED;
    }

    void resumeAudio() {
        mImageButton.setImageResource(R.drawable.pause);
        if (mStatus == STATUS_PAUSED || mStatus == STATUS_ENDED)
            mProgressBarAnimator.start();
        // STATUS_PLAYING is used when restoring dialog
        if (mStatus == STATUS_PAUSED || mStatus == STATUS_PLAYING)
            mProgressBarAnimator.setCurrentPlayTime(mData.getPlayerPosition());
        mData.startPlaying(false);
        mStatus = STATUS_PLAYING;
    }

    private void animate(final CircularSeekBar progressBar, final AnimatorListener listener,
            final float progress, final float maxProgress, final long duration) {
        mProgressBarAnimator = ObjectAnimator.ofFloat(progressBar, "progress", maxProgress);
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
                            mData.seekPlayerTo(mPlayerSeekTo);
                            if (mCheckSeek == STATUS_PLAYING)
                                resumeAudio();
                        }
                        else if (event.getAction() == android.view.MotionEvent.ACTION_MOVE && (mStatus == STATUS_PLAYING || mStatus == STATUS_PAUSED)) {
                            mPlayerSeekTo = (int) (progressBar.getProgress() / mTimeCircle);
                            setDurationText(mPlayerSeekTo);
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
                setDurationText(time);
            }
        });
        progressBar.setProgress(progress);
        mProgressBarAnimator.start();
        if (progress > 0) {
            mProgressBarAnimator.setCurrentPlayTime((long) (progress * MAX_AUDIO_DURATION / 100));
        }
    }

    void setDurationText(long millis) {
        long duration = (mStatus == STATUS_PLAYING) ? mData.getPlayerDuration() : -1;
        mTimeTxt.setText(mTimeTxt.getContext().getString(R.string.audio_duration_max,
            DateUtils.formatElapsedTime(millis / 1000),
            // recording or playing?
            duration < 0 ? MAX_AUDIO_DURATION_TEXT :
                DateUtils.formatElapsedTime(duration / 1000)));
    }

    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(
            PackageManager.FEATURE_MICROPHONE);
    }
}
