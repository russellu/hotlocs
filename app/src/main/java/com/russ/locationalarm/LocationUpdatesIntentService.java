/**
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.russ.locationalarm;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.gms.location.LocationResult;

import java.util.List;

/**
 * Handles incoming location updates and displays a notification with the location data.
 *
 * For apps targeting API level 25 ("Nougat") or lower, location updates may be requested
 * using {@link android.app.PendingIntent#getService(Context, int, Intent, int)} or
 * {@link android.app.PendingIntent#getBroadcast(Context, int, Intent, int)}. For apps targeting
 * API level O, only {@code getBroadcast} should be used.
 *
 *  Note: Apps running on "O" devices (regardless of targetSdkVersion) may receive updates
 *  less frequently than the interval specified in the
 *  {@link com.google.android.gms.location.LocationRequest} when the app is no longer in the
 *  foreground.
 */
public class LocationUpdatesIntentService extends IntentService {

    public static final String ACTION_PROCESS_UPDATES =
            "com.google.android.gms.location.sample.locationupdatespendingintent.action" +
                    ".PROCESS_UPDATES";
    private static final String TAG = LocationUpdatesIntentService.class.getSimpleName();
    private static Location lastLocation;
    private static int queryCount = 0;


    public LocationUpdatesIntentService() {
        // Name the worker thread.
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_PROCESS_UPDATES.equals(action)) {
                LocationResult result = LocationResult.extractResult(intent);
                if (result != null) {
                    List<Location> locations = result.getLocations();

                    for(int i=0;i<locations.size();i++) {
                        HeatmapLogic.addLocation(new SerialLatLng(locations.get(i).getLatitude(),
                                locations.get(i).getLongitude()));

                        if(i==locations.size()-1 && locations.size() > 0 && lastLocation != null){

                            double latDiff = locations.get(i).getLatitude() - lastLocation.getLatitude();
                            double longDiff = locations.get(i).getLongitude() - lastLocation.getLongitude();
                            double absDiff = Math.abs(latDiff)+Math.abs(longDiff);

                            if(absDiff < 0.0005){
                                System.out.println("motionless, absDiff = " + absDiff + " queryCount = " + queryCount);
                                queryCount+=1;
                                if(queryCount > 5 && MapsActivity.updatingFast==true){
                                    sendMessage(0);
                                    System.out.println("reducing update interval, queryCount = " + queryCount);
                                }
                            }
                            else{
                                System.out.println("moving, absDiff = " + absDiff);
                                queryCount = 0;
                                if(MapsActivity.updatingFast==false) {
                                    sendMessage(1);
                                }
                            }
                            lastLocation = locations.get(i);
                        }
                        else if(i==locations.size()-1 && locations.size() > 0){
                            lastLocation = locations.get(i);
                        }
                    }

                    System.out.println("received location service" +
                            locations.get(0).getLatitude() + " " + locations.get(0).getLongitude());

//                    Utils.setLocationUpdatesResult(this, locations);
//                    Utils.sendNotification(this, Utils.getLocationResultTitle(this, locations));
//                    Log.i(TAG, Utils.getLocationUpdatesResult(this));
                }
            }
        }
    }

    // Supposing that your value is an integer declared somewhere as: int myInteger;
    private void sendMessage(int message) {
        // The string "my-integer" will be used to filer the intent
        Intent intent = new Intent("my-integer");
        // Adding some data
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

}
