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

package org.kontalk.util

import android.media.MediaRecorder
import java.io.File


/**
 * Audio recording utilities.
 * @author Daniele Ricci
 */
class AudioRecording {

    companion object {
        /** TODO change to m4a after some time (this is for backward compatibility) */
        const val MIME_TYPE = "audio/mp4"
        const val FILE_EXTENSION = "mp4";

        @JvmStatic
        fun setupMediaRecorder(recorder: MediaRecorder, outputFile: File): MediaRecorder {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setOutputFile(outputFile.absolutePath);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            // TODO we need further study for these
            //recorder.setAudioSamplingRate(48000);
            //recorder.setAudioEncodingBitRate(96000);
            return recorder;
        }

    }

}
