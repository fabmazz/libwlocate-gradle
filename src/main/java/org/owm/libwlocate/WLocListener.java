/*
 * Copyright (C) 2016  Fabio Mazza
 * This file is part of libwlocate-gradle
 *
 * libwlocate-gradle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.owm.libwlocate;

/**
 * Created by fabio on 18/04/16.
 */
public interface WLocListener {
    /**
     * Useful method to let the activity use the location received
     * @param lat the latitude
     * @param lon the longitude
     * @param radius radius of indetermination (I still don't understand how it is measured)
     */
    void onLocationReceived(double lat, double lon, float radius);

    /**
     * The library calls this method when something goes wrong, e.g. there's no connection.
     * You should implement this in the activity, otherwise you have no way of debugging.
     * @param code the code related to the error, see WLocate main class for the complete list
     */
    void onLocationError(int code);
}
