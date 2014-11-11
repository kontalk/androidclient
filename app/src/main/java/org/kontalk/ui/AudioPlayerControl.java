/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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


/**
 * This interface gives access to composer by the audio content view.
 * @author Andrea Cappelli
 */
public interface AudioPlayerControl {
    public void buttonClick (File audioFile, AudioContentViewControl view, long messageId);
    public void playAudio(AudioContentViewControl view, long messageId);
    public void pauseAudio(AudioContentViewControl view);
    public void onBind (long messageId, AudioContentViewControl view);
    public void onUnbind(long messageId, AudioContentViewControl view);
    public void seekTo(int position);
    public boolean isPlaying();

}
