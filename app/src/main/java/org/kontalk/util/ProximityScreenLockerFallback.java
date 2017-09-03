/*
 * Copyright (C) 2014 The Simlar Authors.
 *
 * This file is part of Simlar. (https://www.simlar.org)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.kontalk.util;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import org.kontalk.Log;


public class ProximityScreenLockerFallback implements ProximityScreenLocker, SensorEventListener {
    private static final String TAG = ProximityScreenLocker.class.getSimpleName();
	private static final float PROXIMITY_DISTANCE_THRESHOLD = 4.0f;

	private final Activity mActivity;
    // handled by caller
    //private final SensorManager mSensorManager;

	public ProximityScreenLockerFallback(final Activity activity)
	{
		mActivity = activity;
        // handled by caller
		//mSensorManager = (SensorManager) mActivity.getSystemService(Context.SENSOR_SERVICE);
	}

	@Override
	public final void acquire()
	{
		// handled by caller
        //mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY), SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	public final void release(final boolean immediately)
	{
	    // handler by caller
		//mSensorManager.unregisterListener(this);
		/// TODO: handle immediately
	}

	private void showNavigationBar(final boolean visible)
	{
	    // WARNING not supported on Gingerbread
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mActivity.getWindow().getDecorView()
                .setSystemUiVisibility(visible ?
                    View.SYSTEM_UI_FLAG_VISIBLE : View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

	//
	// SensorEventListener overloaded member functions
	//
	@Override
	public void onAccuracyChanged(final Sensor sensor, final int accuracy)
	{
	}

	@Override
	public final void onSensorChanged(final SensorEvent event)
	{
		final float distance = event.values[0];

		if (distance > event.sensor.getMaximumRange()) {
			Log.v(TAG, "proximity sensors distance=" + distance + " out of range=" + event.sensor.getMaximumRange());
			/// do not return, e.g. the galaxy s2 won't work otherwise
		}

		final Window window = mActivity.getWindow();
		@SuppressWarnings("UnnecessaryFullyQualifiedName") /// this would require to 'import android.R'
		final View view = ((ViewGroup) mActivity.findViewById(android.R.id.content)).getChildAt(0);

		final WindowManager.LayoutParams params = window.getAttributes();
		if (distance <= PROXIMITY_DISTANCE_THRESHOLD) {
			Log.v(TAG, "proximity sensors distance=" + distance + " below threshold=" + PROXIMITY_DISTANCE_THRESHOLD +
					" => dimming screen in order to disable touch events");
			params.screenBrightness = 0.1f;
			view.setVisibility(View.INVISIBLE);
			showNavigationBar(false);
			window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		} else {
			Log.v(TAG, "proximity sensors distance=" + distance + " above threshold=" + PROXIMITY_DISTANCE_THRESHOLD +
					" => enabling touch events (no screen dimming)");
			params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
			view.setVisibility(View.VISIBLE);
			showNavigationBar(true);
			window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		window.setAttributes(params);
	}
}
