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

package org.kontalk.position;

import android.location.Location;


/**
 * Very dummy interface extracted from PlacesAdapter.
 * @author Daniele Ricci
 */
public interface IPlacesAdapter {

    Position getVenuesItem(int i);

    void setOverScrollHeight(int value);

    void setGpsPosition(Location location);

    void setCustomLocation(Location location);

    void searchPlaces(Location location);

}
