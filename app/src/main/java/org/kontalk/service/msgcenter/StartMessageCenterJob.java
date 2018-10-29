/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.service.msgcenter;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;


/**
 * Job service for starting the message center when needed.
 * @author Daniele Ricci
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class StartMessageCenterJob extends JobService {

    public static int JOB_ID = 1000;

    @Override
    public boolean onStartJob(JobParameters params) {
        MessageCenterService.start(this);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, StartMessageCenterJob.class))
            // TODO for API level 28 -- .setImportantWhileForeground(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .build());
    }
}
