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

package org.kontalk.ui;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.app.Fragment;

import org.kontalk.util.SystemUtils;


/**
 * A fragment that handles media recorder and player instances, independently
 * of its parent activity.
 * @author Daniele Ricci
 */
public class AudioFragment extends Fragment implements MediaPlayer.OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener, SensorEventListener {

    private MediaRecorder mRecorder;
    private MediaPlayer mPlayer;

    private long mStartTime;

    /** Message id of the media currently being played. */
    private long mMessageId = -1;

    private OnCompletionListener mOnCompletionListener;
    private boolean mAudioFocus;

    /** Proximity wake lock. */
    private PowerManager.WakeLock mProximityLock;

    private SensorManager mSensorManager;
    private Sensor mProximitySensor;
    /** True if we are covering the proximity sensor. */
    private boolean mProximityClosed;

    /** This is used when restarting playback. */
    private File mLastDataSource;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (mProximitySensor == null) {
            mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        }
    }

    public MediaRecorder getRecorder() {
        if (mRecorder == null) {
            mRecorder = new MediaRecorder();
        }
        return mRecorder;
    }

    public void startRecording() throws IOException {
        mStartTime = SystemClock.uptimeMillis();
        MediaRecorder recorder = getRecorder();
        recorder.prepare();
        recorder.start();
        acquireLock(false);
    }

    public long getElapsedTime() {
        return mStartTime > 0 ? SystemClock.uptimeMillis() - mStartTime : 0;
    }

    public void stopRecording() {
        // release lock anyway
        releaseLock(false);
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
            mStartTime = 0;
        }
    }

    public void preparePlayer(File audioFile) throws IOException {
        mLastDataSource = audioFile;
        preparePlayer(audioFile, false);
    }

    private void preparePlayer(File audioFile, boolean frontSpeaker) throws IOException {
        if (mPlayer == null) {
            mPlayer = new MediaPlayer();
            mPlayer.setOnCompletionListener(this);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(frontSpeaker ? AudioAttributes.CONTENT_TYPE_SPEECH :
                    AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(frontSpeaker ? AudioAttributes.USAGE_VOICE_COMMUNICATION :
                    AudioAttributes.USAGE_MEDIA)
                .build());
        }
        else {
            mPlayer.setAudioStreamType(frontSpeaker ? AudioManager.STREAM_VOICE_CALL :
                AudioManager.STREAM_MUSIC);
        }

        mPlayer.setDataSource(audioFile.getAbsolutePath());
        mPlayer.prepare();
    }

    private void restartPlayback(boolean frontSpeaker) throws IOException {
        if (mPlayer != null && mLastDataSource != null) {
            // pause the listener
            mPlayer.setOnCompletionListener(null);
            mPlayer.pause();
            // save current position and destroy everything
            int position = mPlayer.getCurrentPosition();
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
            // restart playback from last position
            preparePlayer(mLastDataSource, frontSpeaker);
            mPlayer.seekTo(position);
            mPlayer.start();
        }
    }

    public int getPlayerDuration() {
        return mPlayer != null ? mPlayer.getDuration() : -1;
    }

    public int getPlayerPosition() {
        return mPlayer != null ? mPlayer.getCurrentPosition() : -1;
    }

    public boolean startPlaying() {
        return startPlaying(true);
    }

    public boolean startPlaying(boolean proximity) {
        if (mPlayer != null) {
            if (acquireAudioFocus()) {
                mStartTime = SystemClock.uptimeMillis();
                mPlayer.start();
                // started, acquire lock
                acquireLock(proximity);
                return true;
            }
        }
        return false;
    }

    public void pausePlaying() {
        if (mPlayer != null) {
            mPlayer.pause();
            // paused, release lock
            releaseLock(true);
            releaseAudioFocus();
        }
    }

    public void seekPlayerTo(int msec) {
        if (mPlayer != null)
            mPlayer.seekTo(msec);
    }

    public void resetPlayer() {
        if (mPlayer != null)
            mPlayer.reset();
        releaseLock(true);
        releaseAudioFocus();
    }

    public void setOnCompletionListener(OnCompletionListener listener)
    {
        mOnCompletionListener = listener;
    }

    interface OnCompletionListener {
        /** Called on play completion. */
        void onCompletion(AudioFragment audio);

        /** Called when the app loses audio focus. */
        void onAudioFocusLost(AudioFragment audio);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        releaseLock(true);
        // release any audio focus
        releaseAudioFocus();

        if (mOnCompletionListener != null) {
            mOnCompletionListener.onCompletion(this);
        }
    }

    public boolean isPlaying() {
        return mPlayer != null && mPlayer.isPlaying();
    }

    public void setMessageId(long mMessageId) {
        this.mMessageId = mMessageId;
    }

    public long getMessageId() {
        return mMessageId;
    }

    public void finish(boolean release) {
        if (mPlayer != null && release)
            mPlayer.release();
        finish();
    }

    public void finish() {
        mLastDataSource = null;
        mPlayer = null;
        mRecorder = null;
        mStartTime = 0;
        mOnCompletionListener = null;
        releaseLock(true);
        releaseAudioFocus();
    }

    private boolean isNearToSensor(float value) {
        return value < 5.0f && value != mProximitySensor.getMaximumRange();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == mProximitySensor) {
            if (isNearToSensor(event.values[0])) {
                if (!mProximityClosed) {
                    mProximityClosed = true;
                    try {
                        // restart playback from phone speaker
                        restartPlayback(true);
                    }
                    catch (IOException e) {
                        if (mOnCompletionListener != null)
                            mOnCompletionListener.onCompletion(this);
                    }
                }
            }
            else {
                if (mProximityClosed) {
                    mProximityClosed = false;
                    try {
                        // restart playback from speaker
                        restartPlayback(false);
                    }
                    catch (IOException e) {
                        if (mOnCompletionListener != null)
                            mOnCompletionListener.onCompletion(this);
                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void acquireLock(boolean playing) {
        Activity a = getActivity();
        if (a != null) {
            SystemUtils.acquireScreenOn(a);

            if (playing && SystemUtils.isProximityWakelockSupported(a)) {
                if (mProximityLock == null) {
                    mProximityLock = SystemUtils.newProximityWakeLock(a, "ListenProximity");
                    mProximityLock.acquire();

                    if (mProximitySensor != null) {
                        mSensorManager.registerListener(this, mProximitySensor,
                            SensorManager.SENSOR_DELAY_NORMAL);
                    }
                }
            }
        }
    }

    private void releaseLock(boolean playing) {
        Activity a = getActivity();
        if (a != null) {
            SystemUtils.releaseScreenOn(a);

            if (playing && mProximityLock != null) {
                SystemUtils.releaseProximityWakeLock(mProximityLock);
                mProximityLock = null;

                if (mProximitySensor != null) {
                    mSensorManager.unregisterListener(this, mProximitySensor);
                }
            }
        }
    }

    private boolean acquireAudioFocus() {
        if (!mAudioFocus) {
            final Context ctx = getContext();
            if (ctx != null) {
                // using the application context so the audio focus is global
                AudioManager audio = (AudioManager) ctx.getApplicationContext()
                    .getSystemService(Context.AUDIO_SERVICE);
                if (audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    mAudioFocus = true;
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private void releaseAudioFocus() {
        if (mAudioFocus) {
            final Context ctx = getContext();
            if (ctx != null) {
                // using the application context so the audio focus is global
                AudioManager audio = (AudioManager) ctx.getApplicationContext()
                    .getSystemService(Context.AUDIO_SERVICE);
                audio.abandonAudioFocus(this);
                mAudioFocus = false;
            }
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            mAudioFocus = false;
            if (mOnCompletionListener != null) {
                // notify listener that we should stop playing now
                mOnCompletionListener.onAudioFocusLost(AudioFragment.this);
            }
        }
    }

}
