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

import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.util.*;

import android.content.*;
import android.net.wifi.*;
import android.location.*;
import android.os.*;
import android.os.Handler;
import android.util.Log;


/**
 * Internal class, used for storing the position information
 */
class WlocPosition
{

   double lat,lon;
   short  quality;
   short countryCode;
   WlocPosition (){

   }
   WlocPosition(double latitude, double longitude)
   {
      this.lat = latitude;
      this.lon = longitude;
   }

   public short getCountryCode() {
      return countryCode;
   }

   public void setCountryCode(short countryCode) {
      this.countryCode = countryCode;
   }

   public short getQuality() {
      return quality;
   }

   public void setQuality(short quality) {
      this.quality = quality;
   }

}

/**
 * This class should handle the website request instead of the WLocate.
 * We need a Messenger and an Handler to communicate between the two
 *
 * @author fabmazz
 */
class DatabaseRequester implements Runnable {
    private Messenger messenger;
    private String postData;
    private  String websiteURL;
    private HttpURLConnection con;
    private BufferedOutputStream outputStream;
    private WlocPosition position;

    /**
     *
     * @param url this is the URL to make the connection to, already prepared
     * @param postData The data to send in the POST request, already compressed in one string
     * @param incomingMsg the messenger needed for IPC
     */
    DatabaseRequester(String url, String postData, Messenger incomingMsg) {
        this.messenger = incomingMsg;
        this.websiteURL = url;
        this.postData =  postData;
        outputStream = null;
        con = null;
    }

    @Override
    public void run() {
        int rc;
        position = new WlocPosition();
        try {
            URL serverURL= new URL(websiteURL);
            con = (HttpURLConnection) serverURL.openConnection();
            if (con == null) return;
            con.setDoOutput(true); // enable POST
            con.setRequestMethod("POST");
            con.addRequestProperty("Content-Type", "application/x-www-form-urlencoded, *.*");
            con.addRequestProperty("Content-Length", "" + postData.length());
            outputStream = new BufferedOutputStream(con.getOutputStream());
            outputStream.write(postData.getBytes(), 0, postData.length());
            outputStream.flush();
            outputStream.close();
            rc = con.getResponseCode();
            if (rc != HttpURLConnection.HTTP_OK) {
                sendErrorMessage(WLocate.WLOC_CONNECTION_ERROR);
                return;
            }
            BufferedReader buffReader = new BufferedReader(new InputStreamReader(con.getInputStream()));
            try {
                String line;
                while ((line = buffReader.readLine())!=null) {
                    line = line.trim();
                    if (line.contains("result=0"))
                        sendErrorMessage(WLocate.PARSING_RESPONSE_ERROR); //todo thread stopping
                    else if (line.contains("quality=")) {
                        line = line.substring(8);
                        position.setQuality((short) Integer.parseInt(line));
                    } else if (line.contains("lat=")) {
                        line = line.substring(4);
                        position.lat = Double.parseDouble(line);
                    } else if (line.contains("lon=")) {
                        line = line.substring(4);
                        position.lon = Double.parseDouble(line);
                    }
                }
            } catch (NumberFormatException nfe) {
                buffReader.close();
                con.disconnect();
                sendErrorMessage(WLocate.WLOC_SERVER_ERROR);
                return;
            }
            buffReader.close();
            con.disconnect();
        } catch (IOException excep) {
            excep.printStackTrace();
            sendErrorMessage(WLocate.IO_ERROR);
            return;
        }
        sendPosition();
    }

    private void sendPosition() {
        Message msg =  new Message();
        msg.what = WLocate.WLOC_OK;
        msg.obj = position;
        try {
            messenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    private void sendErrorMessage(int error) {
        Message msg = new Message();
        msg.what=error;
        try {
            messenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}

/**
 * Geopositioning/location class to evaluate the current position without using the standard location mechanisms
 * where the privacy rules are not clear. It tries to evaluate the current geographic position by using GPs and
 * - in case this fails or GPS is not available - by using other parameters like surrounding WLAN networks.<BR><BR>
 *
 * The usage is quite simple: create an own class that inherits from WLocate and overwrite method
 * wlocRequestPosition(). Call wlocRequestPosition() to start position evaluation. The resulting is returned via
 * given interface asynchronously.<BR><BR>
 *
 * IMPORTANT: The results are given via the WLocListener interface, but there is a fallback on the old methods.
 *
 * Beside of that it is recommended to call the doPause() and doResume() methods whenever an onPause() and onResume()
 * event occurs in main Activity to avoid Exceptions caused by the WiFi-receiver.
 */
public class WLocate
{
    //These flags are not really needed to work.
   public static final int FLAG_NO_NET_ACCESS =0x0001; /** Don't perform any network accesses to evaluate the position data, this option disables the WLAN_based position retrieval */
   public static final int FLAG_NO_GPS_ACCESS =0x0002; /** Don't use a GPS device to evaluate the position data, this option disables the WLAN_based position retrieval */
   public static final int FLAG_NO_IP_LOCATION=0x0004; /** Don't send a request to the server for IP-based location in case no WLANs are available */
   public static final int FLAG_UPDATE_AGPS   =0x0008; /** Update AGPS data to get better/faster/mor accurate GPS fixes; this flag is useless when FLAG_NO_GPS_ACCESS is set too */

   public static final int WLOC_OK=0;               /** Result code for position request, given position information are OK */
   public static final int WLOC_CONNECTION_ERROR=1; /** Result code for position request, a connection error occurred, no position information are available */
   public static final int WLOC_SERVER_ERROR=2;
   public static final int PARSING_RESPONSE_ERROR =3;   /** Result code for position request, error occured while parsing the server response, no position information are available */
   public static final int WLOC_ERROR=100;
    public static final int WIFI_DISABLED = 6;
    public static final int THREAD_ALREADY_RUNNING=7;
   public static final String LOC_SERVER_OPENWLANMAP = "http://openwlanmap.org/";
   public static final String LOC_SERVER_OPENWIFISU = "http://openwifi.su/";
   private static final int WLOC_RESULT_OK=1;
    public static final int IO_ERROR = 4;

   private Location            lastLocation=null;
   private LocationManager     location;
   private GPSLocationListener locationListener;
   private GPSStatusListener   statusListener;
   private WifiManager         wifiMgr;
   private WifiReceiver        receiverWifi = new WifiReceiver();
   boolean   gpsLocationWanted;
   private boolean             GPSAvailable=false,scanStarted=false,AGPSUpdated=false;
   private double              m_lat,m_lon;
   private float               m_radius=1.0f,m_speed=-1.0f,m_cog=-1.0f;
   private int                 scanFlags;
   private long                lastLocationMillis=0;
   private Context             ctx;
   private loc_info            locationInfo=new loc_info();
   private Thread              netThread=null;
    private String              locatorURL;
    protected WLocListener wLocListener;


   /**
    * Constructor for WLocate class, this constructor has to be overwritten by inheriting class
    * @param ctx current context, hand over Activity object here
    * @param url domain name / URL (with appended slash!) where getpos.php for position retrieval can be found
    *            either OpenWlanMap.org or Openwifi.su
    * @param useGps whether or not the app wants gps location. In this case, it should handle permissions by itself
    */
   public WLocate(Context ctx,String url, boolean useGps)
   throws IllegalArgumentException
   {
      gpsLocationWanted = useGps;
      locatorURL=url;
      wifiMgr = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
      this.ctx=ctx;
      if(gpsLocationWanted) startGPSLocation();
       doResume();
   }


   /**
    * Constructor for WLocate class, this constructor has to be overwritten by inheriting class
    * @param ctx current context, hand over Activity object here
    */
   public WLocate(Context ctx)
   throws IllegalArgumentException
   {
      this(ctx,LOC_SERVER_OPENWLANMAP);
   }
   public WLocate(Context ctx,String url)
           throws IllegalArgumentException
   {
      this(ctx,url, false);
   }


    public void setLocListener(WLocListener wLocListener) {
        this.wLocListener = wLocListener;
    }


    private void startGPSLocation()
   {
      location= (LocationManager)ctx.getSystemService(Context.LOCATION_SERVICE);
      locationListener = new GPSLocationListener();
      location.requestLocationUpdates(LocationManager.GPS_PROVIDER,350,0,(LocationListener)locationListener);
      statusListener=new GPSStatusListener();
      location.addGpsStatusListener(statusListener);
   }
      
   /**
   * Send pause-information to active WLocate object. This method should be called out of the main Activity
   * whenever an onPause() event occurs to avoid leaked IntentReceiver exceptions caused by the receiver
   * when it is still registered after application switched over to pause state
   */
   public void doPause()
   {
      try
	  {
         ctx.unregisterReceiver(receiverWifi);
	  }
      catch (IllegalArgumentException iae) // just in case receiverWifi is not registered yet
      {
    	  iae.printStackTrace();
      }
   }
   
   
   
   /**
   * Send resume-information to active WLocate object. This method has to be called out of the main Activity
   * whenever an onResume() event occurs and in case the doPause() method is used to reactivate the WiFi
   * receiver
   */
   public void doResume()
   {
      ctx.registerReceiver(receiverWifi, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));            
   }
   
   
   
   /**
    * Start position evaluation process, the result is returned via method wloc_return_position()
    * that may be called asynchronously
    * @param flags specifies how the position has to be evaluated using the FLAG_NO_xxx_ACCESS-values
    *              it's quite ok to just put 0
    */
   public void wlocRequestPosition(int flags)
   {
      scanFlags=flags;
      scanStarted=true;
       if(gpsLocationWanted)
      if (((scanFlags & FLAG_NO_GPS_ACCESS)==0) && ((scanFlags & FLAG_UPDATE_AGPS)!=0) && (!AGPSUpdated))
      {
    	 location.sendExtraCommand(LocationManager.GPS_PROVIDER,"delete_aiding_data", null);
    	 Bundle bundle = new Bundle();
    	 location.sendExtraCommand("gps", "force_xtra_injection", bundle);
    	 location.sendExtraCommand("gps", "force_time_injection", bundle);
         AGPSUpdated=true;
      }
      if (!wifiMgr.isWifiEnabled()) sendLocationError(WIFI_DISABLED);
       wifiMgr.startScan();
   }




    public loc_info last_location_info()
   {
      return locationInfo;
   }

    /**
     *
     * @param request the request data
     * @return 0 if the thread has started
     */
   private int get_position(wloc_req request)
   {
       StringBuilder sb=new StringBuilder();
       Handler mhandler = new PositionHandler(new WeakReference<WLocate>(this));
       Messenger messenger = new Messenger(mhandler);
       for (int i=0; i<wloc_req.WLOC_MAX_NETWORKS; i++)
       if ((request.bssids[i]!=null) && (request.bssids[i].length()>0)) {
           sb.append(request.bssids[i]);
           sb.append("\r\n");
       }
       if (netThread==null || !(netThread.isAlive())){
       Runnable runnable = new DatabaseRequester(locatorURL+"getpos.php", sb.toString(), messenger);
       netThread = new Thread(runnable);
       netThread.start();
           return 0;
       }
       else return THREAD_ALREADY_RUNNING;
   }

    /**
     * Send the location back to the calling class
     * @param latitude the position latitude
     * @param longitude the position longitude
     * @param radius the position radius of uncertainty
     *               can be quite random, as it isn't very clear on how it obtained
     */
    private void returnPosition(double latitude, double longitude, float radius) {
        if(wLocListener!=null)
        wLocListener.onLocationReceived(latitude,longitude,radius);
        else {
            //fallback on previous methods
        wloc_return_position(WLOC_OK,latitude,longitude,radius,(short)0);
        wloc_return_position(WLOC_OK,latitude,longitude,radius,(short)0,m_cog);
        }
    }

    /**
     * Inform the location-awaiting class that some error happened
     * @param errorcode the code error
     */
    private void sendLocationError(int errorcode) {
        if(wLocListener!=null)
            wLocListener.onLocationError(errorcode);
    }
    /**
     * Handler for receiving the server response
     *
     *  @author fabmazz
     */

    static class PositionHandler extends Handler {
        PositionHandler(WeakReference<WLocate> WeakReference) {
            this.wLocateWeakReference=WeakReference;
        }
        WeakReference<WLocate> wLocateWeakReference;
        WlocPosition position;
        @Override
        public void handleMessage(Message msg) {
            if(msg.what==WLOC_OK){
                position=(WlocPosition)msg.obj;
                wLocateWeakReference.get().returnPosition(position.lat,position.lon,120-position.quality);
            }
           else if(msg.what<5 && msg.what>0)

                wLocateWeakReference.get().sendLocationError(msg.what);
        }
    }
   
   class WifiReceiver extends BroadcastReceiver 
   {
      public void onReceive(Context c, Intent intent) 
      {
         int           netCnt=0;
          //Comparator to compare the signal strengths
          Comparator<ScanResult> strongerSignalComp = new Comparator<ScanResult>() {
              @Override
              public int compare(ScanResult lhs, ScanResult rhs) {
                  //if level is higher then should be on top -> order is reversed
                  if(lhs.level < rhs.level) return 1;
                  else if(lhs.level==rhs.level) return 0;
                  else return -1;
              }
          };

         if (!scanStarted) return;
         scanStarted=false;
         List<ScanResult> configs= wifiMgr.getScanResults();
         if (configs==null) return;
          //Testing ordering list by stronger signal
          Collections.sort(configs,strongerSignalComp);
          locationInfo.wifiScanResult=configs;
         locationInfo.requestData=new wloc_req();



         if (configs.size()>0) for (ScanResult config : configs) 
         {            
            config.BSSID=config.BSSID.toUpperCase(Locale.US).replace(".",""); // some strange devices use a dot instead of :
            config.BSSID=config.BSSID.toUpperCase(Locale.US).replace(":",""); // some strange devices use a dot instead of :
            if (config.BSSID.equalsIgnoreCase("000000000000")) continue; // invalid BSSID
            locationInfo.requestData.bssids[netCnt]=config.BSSID;
            locationInfo.requestData.signal[netCnt]=(byte)Math.abs(config.level);               
            Log.d("LocDemo wifiScan", "Current network RSSI: "+config.level);
            netCnt++;
            if (netCnt>=wloc_req.WLOC_MAX_NETWORKS) break;   
         }

         locationInfo.lastLocMethod=loc_info.LOC_METHOD_NONE;
         locationInfo.lastSpeed=-1.0f;
          /**
           * The library checks if a  GPS Location is available before sending the wlocation request
           *
           */
          if (GPSAvailable  && gpsLocationWanted) GPSAvailable=(SystemClock.elapsedRealtime()-lastLocationMillis) < 7500;
          if(GPSAvailable && gpsLocationWanted){
              returnPosition(m_lat,m_lon,m_radius);
              return;
          }
          /**
           * If there is none, use wifi
           */
          if(configs.size()>0){
              int errcode = get_position(locationInfo.requestData);
              if (errcode!=0) sendLocationError(errcode);
          }

          //todo separate GPS and WLAN positioning methods, so that one can choose the preferred method
      }
   }



   
   
   /**
    * This method is called as soon as a result of a position evaluation request is available.
    * Thus this method should be overwritten by the inheriting class to receive the results there.
    * @param ret the return code that informs if the location evaluation request could be fulfilled
    *        successfully or not. Only in case this parameter is equal to WLOC_OK all the other
    *        ones can be used, elsewhere no position information could be retrieved.
    * @param lat the latitude of the current position 
    * @param lon the latitude of the current position 
    * @param radius the accuracy of the position information, this radius specifies the range around
    *        the given latitude and longitude information of the real position. The smaller this value
    *        is the more accurate the given position information is.
    * @param ccode code of the country where the current position is located within, in case the
    *        country is not known, 0 is returned. The country code can be converted to a text that
    *        specifies the country by calling wloc_get_country_from_code()
    *  @deprecated
    */
   protected void wloc_return_position(int ret,double lat,double lon,float radius,short ccode)
   {
      
   }

   
   
   /**
    * This method is called as soon as a result of a position evaluation request is available.
    * Thus this method should be overwritten by the inheriting class to receive the results there.
    * @param ret the return code that informs if the location evaluation request could be fulfilled
    *        successfully or not. Only in case this parameter is equal to WLOC_OK all the other
    *        ones can be used, elsewhere no position information could be retrieved.
    * @param lat the latitude of the current position 
    * @param lon the latitude of the current position 
    * @param radius the accuracy of the position information, this radius specifies the range around
    *        the given latitude and longitude information of the real position. The smaller this value
    *        is the more accurate the given position information is.
    * @param ccode code of the country where the current position is located within, in case the
    *        country is not known, 0 is returned. The country code can be converted to a text that
    *        specifies the country by calling wloc_get_country_from_code()
    * @param cog the actual course over ground / bearing in range 0.0..360.0 degrees. This value is not
    *        always available, in case the current course over ground is not known or could not evaluated
    *        -1.0 is returned here.
    *  @deprecated
    */
   protected void wloc_return_position(int ret,double lat,double lon,float radius,short ccode,float cog)
   {
      
   }

   
   
   /**
    * Convert a country code to a more easy to read short text that specifies a country.
    * @param ccode the country code to be converted
    * @return the short text that names the country or an empty string an unknown country
    *         specifier was given or the country code was 0
    */
   public String wloc_get_country_from_code(short ccode)
   {
      switch (ccode)
      {
         case 1:
            return "DE";
         case 2:
            return "AT";
         case 3:
            return "CH";
         case 4:
            return "NL";
         case 5:
            return "BE";
         case 6:
            return "LU";
         case 7:
            return "NO";
         case 8:
            return "SE";
         case 9:
            return "DK";
         case 10:
            return "AF";
         case 12:
            return "AL";
         case 13:
            return "DZ";
         case 17:
            return "AN";
         case 18:
            return "AG";
         case 19:
            return "AR";
         case 20:
            return "AM";
         case 21:
            return "AU";
         case 23:
            return "BS";
         case 24:
            return "BH";
         case 25:
            return "BD";
         case 26:
            return "BB";
         case 27:
            return "BY";
         case 28:
            return "BZ";
         case 29:
            return "BJ";
         case 30:
            return "BM";
         case 32:
            return "BO";
         case 33:
            return "BA";
         case 36:
            return "BR";
         case 37:
            return "BN";
         case 38:
            return "BG";
         case 43:
            return "CA";
         case 44:
            return "CV";
         case 47:
            return "CL";
         case 48:
            return "CN";
         case 49:
            return "CO";
         case 52:
            return "CR";
         case 53:
            return "HR";
         case 55:
            return "CY";
         case 56:
            return "CZ";
         case 59:
            return "DO";
         case 60:
            return "EC";
         case 61:
            return "EG";
         case 66:
            return "ET";
         case 68:
            return "FI";
         case 69:
            return "FR";
         case 73:
            return "GH";
         case 75:
            return "GR";
         case 76:
            return "GL";
         case 77:
            return "GD";
         case 78:
            return "GU";
         case 79:
            return "GT";
         case 82:
            return "HT";
         case 83:
            return "HN";
         case 84:
            return "HK";
         case 85:
            return "HU";
         case 86:
            return "IS";
         case 87:
            return "IN";
         case 88:
            return "ID";
         case 89:
            return "IR";
         case 90:
            return "IQ";
         case 91:
            return "IE";
         case 93:
            return "IT";
         case 94:
            return "JM";
         case 95:
            return "JP";
         case 97:
            return "JO";
         case 98:
            return "KZ";
         case 99:
            return "KE";
         case 102:
            return "KR";
         case 103:
            return "KW";
         case 104:
            return "KG";
         case 105:
            return "LA";
         case 106:
            return "LV";
         case 107:
            return "LB";
         case 108:
            return "LS";
         case 111:
            return "LT";
         case 115:
            return "MY";
         case 116:
            return "MV";
         case 118:
            return "MT";
         case 119:
            return "MQ";
         case 121:
            return "MU";
         case 123:
            return "MX";
         case 124:
            return "MC";
         case 125:
            return "MN";
         case 126:
            return "MA";
         case 127:
            return "MZ";
         case 131:
            return "NZ";
         case 133:
            return "NI";
         case 135:
            return "NG";
         case 137:
            return "OM";
         case 138:
            return "PK";
         case 141:
            return "PA";
         case 142:
            return "PY";
         case 144:
            return "PE";
         case 145:
            return "PH";
         case 147:
            return "PL";
         case 148:
            return "PT";
         case 149:
            return "PR";
         case 150:
            return "QA";
         case 151:
            return "RO";
         case 152:
            return "RU";
         case 155:
            return "SM";
         case 157:
            return "SA";
         case 158:
            return "SN";
         case 161:
            return "SG";
         case 162:
            return "SK";
         case 163:
            return "SI";
         case 166:
            return "ZA";
         case 167:
            return "ES";
         case 168:
            return "LK";
         case 169:
            return "SD";
         case 170:
            return "SR";
         case 172:
            return "SY";
         case 173:
            return "TW";
         case 174:
            return "TJ";
         case 175:
            return "TZ";
         case 176:
            return "TH";
         case 179:
            return "TT";
         case 180:
            return "TN";
         case 181:
            return "TR";
         case 182:
            return "TM";
         case 185:
            return "UA";
         case 186:
            return "AE";
         case 187:
            return "UK";
         case 188:
            return "US";
         case 189:
            return "UY";
         case 191:
            return "VE";
         case 192:
            return "VN";
         case 195:
            return "ZM";
         case 196:
            return "ZW";
         default:
            return "";
      }
   }

   
   
   private class GPSStatusListener implements GpsStatus.Listener 
   {
      
      public void onGpsStatusChanged(int event) 
      {
         switch (event) 
         {
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
               if (lastLocation != null)
               {
                  GPSAvailable=(SystemClock.elapsedRealtime()-lastLocationMillis) < 3500;
               }
               break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
               // Do something.
               GPSAvailable=true;
               break;
            case GpsStatus.GPS_EVENT_STOPPED:
               GPSAvailable=false;
               break;
         }
      }
   }   
    
   
   private class GPSLocationListener implements LocationListener 
   {
      public void onLocationChanged(Location gLocation) 
      {
         if (location == null) return;
         lastLocationMillis = SystemClock.elapsedRealtime();
         lastLocation =new Location(gLocation);         
//         GPSAvailable=true;
         m_lat=gLocation.getLatitude();
         m_lon=gLocation.getLongitude();
         m_cog=gLocation.getBearing(); // course over ground/orientation
         if (gLocation.hasSpeed()) m_speed=gLocation.getSpeed(); //m/sec
         else m_speed=-1;
         if (gLocation.hasAccuracy()) m_radius=gLocation.getAccuracy();
         else m_radius=-1;
      }

      public void onStatusChanged(String provider, int status, Bundle extras)
      {
         if ((provider!=null) && (provider.equalsIgnoreCase(LocationManager.GPS_PROVIDER)))
         {
            if (status!=LocationProvider.AVAILABLE) GPSAvailable=false;
         }
      }

      public void onProviderEnabled(String provider)
      {
      }

      public void onProviderDisabled(String provider)
      {
         GPSAvailable=false;
      }
   };
   
}


