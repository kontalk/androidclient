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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import org.kontalk.Log;


/**
 * This class capsules the undocumented native proximity screen off wake lock.
 */
public final class ProximityScreenLockerNative implements ProximityScreenLocker {
	private static final String TAG = ProximityScreenLocker.class.getSimpleName();
	private final PowerManager.WakeLock mProximityWakeLock;
	private final Method mPowerLockReleaseMethod;

	public static ProximityScreenLocker create(final Context context)
	{
		final PowerManager.WakeLock proximityWakeLock = initProximitySensor(context);

		if (proximityWakeLock == null) {
			return null;
		}

		return new ProximityScreenLockerNative(proximityWakeLock);
	}

	private ProximityScreenLockerNative(final PowerManager.WakeLock proximityWakeLock)
	{
		mProximityWakeLock = proximityWakeLock;
		mPowerLockReleaseMethod = initPowerLockReleaseMethod(mProximityWakeLock);
	}

	private static PowerManager.WakeLock initProximitySensor(final Context context)
	{
		final PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		try {
			final int proximityScreenOffWakeLock = (Integer) PowerManager.class
                .getDeclaredField("PROXIMITY_SCREEN_OFF_WAKE_LOCK").get(null);

			if (!checkNativeSupport(powerManager, proximityScreenOffWakeLock)) {
				return null;
			}

			return powerManager.newWakeLock(proximityScreenOffWakeLock, "Kontalk:SimlarProximityWakeLock");
		}
		catch (final NoSuchFieldException ex) {
			Log.w(TAG, "NoSuchFieldException while accessing PROXIMITY_SCREEN_OFF_WAKE_LOCK");
		}
		catch (final IllegalAccessException ex) {
			Log.w(TAG, "IllegalAccessException while accessing PROXIMITY_SCREEN_OFF_WAKE_LOCK");
		}
		return null;
	}

	private static boolean checkNativeSupport(final PowerManager powerManager, final int proximityScreenOffWakeLock)
	{
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
				final Method method = powerManager.getClass().getDeclaredMethod("isWakeLockLevelSupported", int.class);
				return (Boolean) method.invoke(powerManager, proximityScreenOffWakeLock);
			}

			final Method method = powerManager.getClass().getDeclaredMethod("getSupportedWakeLockFlags");
			final int supportedFlags = (Integer) method.invoke(powerManager);
			return (supportedFlags & proximityScreenOffWakeLock) != 0x0;
		}
		catch (final NoSuchMethodException ex) {
			Log.w(TAG, "NoSuchMethodException while checking native support");
		}
		catch (final IllegalAccessException ex) {
			Log.w(TAG, "IllegalAccessException while checking native support");
		}
		catch (final InvocationTargetException ex) {
			Log.w(TAG, "InvocationTargetException while checking native support");
		}
		return false;
	}

	private static Method initPowerLockReleaseMethod(final PowerManager.WakeLock proximityWakeLock)
	{
		if (proximityWakeLock == null) {
			return null;
		}

		try {
			return proximityWakeLock.getClass().getDeclaredMethod("release", int.class);
		}
		catch (final NoSuchMethodException ex) {
			Log.w(TAG, "NoSuchMethodException release");
			return null;
		}
	}

	@Override
	public void acquire()
	{
		if (mProximityWakeLock == null) {
			return;
		}

		if (mProximityWakeLock.isHeld()) {
			Log.v(TAG, "acquire triggered but already acquired");
			return;
		}

		Log.v(TAG, "acquiring");
		mProximityWakeLock.acquire();
	}

	@Override
	public void release(final boolean immediately)
	{
		if (mProximityWakeLock == null) {
			return;
		}

		if (!mProximityWakeLock.isHeld()) {
			Log.v(TAG, "release triggered but not held");
			return;
		}

		if (releaseWithNativeFunction(immediately)) {
			Log.v(TAG, "released using native function");
		}
		else {
			mProximityWakeLock.release();
			Log.v(TAG, "released using old function");
		}
	}

	private boolean releaseWithNativeFunction(final boolean immediately)
	{
		if (mPowerLockReleaseMethod == null) {
			return false;
		}

		try {
			mPowerLockReleaseMethod.invoke(mProximityWakeLock, immediately ? 0 : 1);
			return true;
		}
		catch (final IllegalAccessException ex) {
			Log.w(TAG, "IllegalAccessException calling native release method");
		}
		catch (final IllegalArgumentException ex) {
			Log.w(TAG, "IllegalArgumentException calling native release method");
		}
		catch (final InvocationTargetException ex) {
			Log.w(TAG, "InvocationTargetException calling native release method");
		}

		return false;
	}
}
