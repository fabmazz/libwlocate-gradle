package org.owm.libwlocate;

/**
 * Created by fabio on 18/04/16.
 */
public interface WLocListener {
    void onLocationReceived(double lat, double lon, float radius);
    void onLocationError(int code);
}
