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

import java.io.IOException;

import android.app.Activity;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;

import org.kontalk.util.SystemUtils;


/**
 * A fragment that handles media recorder and player instances, independently
 * of its parent activity.
 * @author Daniele Ricci
 */
public class AudioFragment extends Fragment {

    private MediaRecorder mRecorder;
    private MediaPlayer mPlayer;

    private long mStartTime;

    /** Message id of the media currently being played. */
    private long mMessageId = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
    }

    public MediaPlayer getPlayer() {
        if (mPlayer == null) {
            mPlayer = new MediaPlayer();
        }
        return mPlayer;
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
        acquireLock();
    }

    public long getElapsedTime() {
        return mStartTime > 0 ? SystemClock.uptimeMillis() - mStartTime : 0;
    }

    public void stopRecording() {
        // release lock anyway
        releaseLock();
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;
            mStartTime = 0;
        }
    }

    public void startPlaying() {
        if (mPlayer != null) {
            mStartTime = SystemClock.uptimeMillis();
            mPlayer.start();
            // started, acquire lock
            acquireLock();
        }
    }

    public void pausePlaying() {
        if (mPlayer != null) {
            mPlayer.pause();
            // paused, release lock
            releaseLock();
        }
    }

    public void seekPlayerTo(int msec) {
        if (mPlayer != null)
            mPlayer.seekTo(msec);
    }

    public void resetPlayer() {
        if (mPlayer != null)
            mPlayer.reset();
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
        mPlayer = null;
        mRecorder = null;
        mStartTime = 0;
        releaseLock();
    }

    private void acquireLock() {
        Activity a = getActivity();
        if (a != null)
            SystemUtils.acquireScreenOn(a);
    }

    private void releaseLock() {
        Activity a = getActivity();
        if (a != null)
            SystemUtils.releaseScreenOn(a);
    }

}
