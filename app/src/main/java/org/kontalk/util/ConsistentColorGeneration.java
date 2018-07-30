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

package org.kontalk.util;

import org.jivesoftware.smack.util.SHA1;


/**
 * Implementation of XEP-0392: Consistent Color Generation<br>
 * TODO this class is a brutal porting from JavaScript code and should be optimized
 * @see <a href="https://xmpp.org/extensions/xep-0392.html">XEP-0392</a>
 * @see <a href="https://github.com/jsxc/consistent-color-generation">Ported from JavaScript implementation</a>
 */
public class ConsistentColorGeneration {

    public static final int CORRECTION_NONE = 0;
    public static final int CORRECTION_REDGREEN = 1;
    public static final int CORRECTION_BLUE = 2;

    private static final double KR = 0.299;
    private static final double KG = 0.587;
    private static final double KB = 0.114;

    private ConsistentColorGeneration() {}

    public static double[] getRGB(String identifier, int correction) {
        return getRGB(identifier, correction, 0.732);
    }

    public static double[] getRGB(String identifier, int correction, double y) {
        double angle = generateAngle(identifier, correction);
        double[] space = generateCbCr(angle);

        return cbCrToRGB(space[0], space[1], y);
    }

    private static double[] generateCbCr(double angle) {
        double cr = Math.sin(angle);
        double cb = Math.cos(angle);
        double factor;

        if (Math.abs(cr) > Math.abs(cb)) {
            factor = 0.5d / Math.abs(cr);
        }
        else {
            factor = 0.5d / Math.abs(cb);
        }

        cb = cb * factor;
        cr = cr * factor;

        return new double[] { cb, cr };
    }

    private static double generateAngle(String identifier, int correction) {
        String hash = SHA1.hex(identifier);
        String first16bits = hash.substring(0, 4);
        String littleEndian = first16bits.substring(2) + first16bits.substring(0, 2);
        double angle = (double) Integer.parseInt(littleEndian, 16) / 65535 * 2 * Math.PI;

        switch (correction) {
            case CORRECTION_REDGREEN:
                angle /= 2;
                break;
            case CORRECTION_BLUE:
                angle = (angle / 2) + (Math.PI / 2);
                break;
        }

        return angle;
    }

    private static double clipValueToRangeFrom0To1(double value) {
        return Math.max(0, Math.min(value, 1));
    }

    private static double[] cbCrToRGB(double cb, double cr, double y) {
        double r = 2 * (1 - KR) * cr + y;
        double b = 2 * (1 - KB) * cb + y;
        double g = (y - KR * r - KB * b) / KG;

        return new double[] {
            clipValueToRangeFrom0To1(r),
            clipValueToRangeFrom0To1(g),
            clipValueToRangeFrom0To1(b),
        };
    }

}
