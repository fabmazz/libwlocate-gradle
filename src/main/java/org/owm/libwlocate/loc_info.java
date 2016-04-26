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

import java.util.List;

import android.net.wifi.ScanResult;

public class loc_info
{
   public static final int LOC_METHOD_NONE=0;
   public static final int LOC_METHOD_LIBWLOCATE=1;
   public static final int LOC_METHOD_GPS=2;
   
   /** describes based on which method the last location was performed with */
   public int lastLocMethod=LOC_METHOD_NONE;
   
   /** request data that is used for libwlocate-based location request, its member bssids is filled with valid BSSIDs also in case of GPS location */
   public wloc_req requestData;
   
   /** result of last WiFi-scan, this list is filled with valid data also in case of GPS location */
   public List<ScanResult> wifiScanResult;
   
   /** last movement speed in km per hour, if no speed could be evaluated the value is smaller than 0*/
   public float lastSpeed=-1f;
}