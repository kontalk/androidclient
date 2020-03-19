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

package org.kontalk.util;


import java.util.concurrent.TimeUnit;

/**
 * Step timer mainly used to slow down notification updates.<br>
 * Uses {@link System#nanoTime()}.
 * @author Daniele Ricci
 */
public class StepTimer {

    private final long mMinDelay;

    /** Marked timestamp. */
    private long mTimestamp;
    /** Delay accumulator. */
    private long mDelay;

    /** The delay for the step in milliseconds. */
    public StepTimer(long delay) {
        mMinDelay = delay;
        reset();
    }

    public void reset() {
        mTimestamp = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        mDelay = 0;
    }

    /** Returns true if the defined step delay has passed, optionally resetting the timer if that's true. */
    public boolean isStep(boolean resetIfPassed) {
        boolean r = isStep();
        if (r && resetIfPassed)
            reset();
        return r;
    }

    /** Returns true if the defined step delay has passed. */
    public boolean isStep() {
        final long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        long delay = now - mTimestamp;
        if (delay < 1) delay = 1;
        mDelay += delay;
        mTimestamp = now;
        return mDelay >= mMinDelay;
    }
}
