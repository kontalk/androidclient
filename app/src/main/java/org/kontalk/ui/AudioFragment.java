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


import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.v4.app.Fragment;

/**
 * A fragment that handles media recorder and player instances, independently
 * of its parent activity.
 * @author Daniele Ricci
 */
public class AudioFragment extends Fragment {

    private MediaRecorder mRecorder;
    private MediaPlayer mPlayer;

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

    public void finish() {
        mPlayer = null;
        mRecorder = null;
    }

}
