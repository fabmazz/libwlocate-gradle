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
