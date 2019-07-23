package com.russ.locationalarm;

import android.Manifest;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.material.snackbar.Snackbar;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import android.location.LocationListener;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;




public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        SharedPreferences.OnSharedPreferenceChangeListener  {

    private GoogleMap mMap;
    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;
    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    private static final long UPDATE_INTERVAL_FAST = 10000; //in ms
    private static final long FASTEST_UPDATE_INTERVAL_FAST = 10000; //in ms
    private static final long MAX_WAIT_TIME_FAST = UPDATE_INTERVAL_FAST * 3;

    private static final long UPDATE_INTERVAL_SLOW = 120000; //in ms
    private static final long FASTEST_UPDATE_INTERVAL_SLOW = 120000; //in ms
    private static final long MAX_WAIT_TIME_SLOW = UPDATE_INTERVAL_SLOW * 3;

    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    public LocationRequest mLocationRequestFast;
    public LocationRequest mLocationRequestSlow;

    /**
     * Provides access to the Fused Location Provider API.
     */
    public FusedLocationProviderClient mFusedLocationClientFast;
    public FusedLocationProviderClient mFusedLocationClientSlow;

    // UI Widgets.
  //  private Button mRequestUpdatesButton;
  //  private Button mRemoveUpdatesButton;
  //  private TextView mLocationUpdatesResultView;


    public TileOverlay tileOverlay;
    public boolean movedToLocation = false;
    public static boolean updatingFast = true;

    private TextView currentLatLngTextView;
    private TextView currentModeTextView;
    private Button resetHeatMapButton;

    private Timer updateTimer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        loadStoredLatLngs();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        currentLatLngTextView = (TextView)findViewById(R.id.current_latlng_textView);
        currentModeTextView = (TextView)findViewById(R.id.current_mode_textView);
        resetHeatMapButton = (Button)findViewById(R.id.reset_button);

        resetHeatMapButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                //storedLatLngs = new HashSet<>();
                HeatmapLogic.resetStoredLatLngs();
                buildAndDisplayHeatMap();
            }
        });

        if(updatingFast==true){
            currentModeTextView.setText("location update mode = Fast (every 15sec)");

        }
        else
            currentModeTextView.setText("location update mode = Slow (every 60sec)");

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Check if the user revoked runtime permissions.
        if (!checkPermissions()) {
            requestPermissions();
        }

        mFusedLocationClientFast = LocationServices.getFusedLocationProviderClient(this);
        mFusedLocationClientSlow = LocationServices.getFusedLocationProviderClient(this);


        createLocationRequest();
        updateDisplay();

        periodicallySave();
    }

    public MapsActivity getActivity(){
        return this;
    }

    public static <T> T getLastElement(final Iterable<T> elements) {
        T lastElement = null;

        for (T element : elements) {
            lastElement = element;
        }

        return lastElement;
    }

    public void loadStoredLatLngs(){
        try {

            FileInputStream hashFis = getActivity().openFileInput("savedHashSet");
            ObjectInputStream hashIs = new ObjectInputStream(hashFis);
            HashSet<SerialLatLng> loadedHashSetLatLngs = (HashSet<SerialLatLng>) hashIs.readObject();
            hashIs.close();
            hashFis.close();

            FileInputStream listFis = getActivity().openFileInput("savedList");
            ObjectInputStream listIs = new ObjectInputStream(listFis);
            ArrayList<SerialLatLng> loadedListLatLngs = (ArrayList<SerialLatLng>) listIs.readObject();
            listIs.close();
            listFis.close();

            //storedLatLngs = loadedLatLngs;
            HeatmapLogic.setStoredHashSet(loadedHashSetLatLngs);
            HeatmapLogic.setStoredList(loadedListLatLngs);

            if(loadedHashSetLatLngs == null || loadedListLatLngs == null) {
                //storedLatLngs = new HashSet<>();
                HeatmapLogic.resetStoredLatLngs();
                System.out.println("couldn't load, reinitializing");
            }
            else
                System.out.println("LOADED SUCCESSFULLY");
        }
        catch(Exception e){e.printStackTrace();}
    }

    public void periodicallySave(){
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {

                            FileOutputStream listFos = getActivity().openFileOutput("savedList", Context.MODE_PRIVATE);
                            ObjectOutputStream listOs = new ObjectOutputStream(listFos);
                            listOs.writeObject(HeatmapLogic.getListLatLngs());
                            listOs.close();
                            listFos.close();

                            FileOutputStream hashFos = getActivity().openFileOutput("savedHashSet", Context.MODE_PRIVATE);
                            ObjectOutputStream hashOs = new ObjectOutputStream(hashFos);
                            hashOs.writeObject(HeatmapLogic.getHashSetLatLngs());
                            hashOs.close();
                            hashFos.close();

                            System.out.println("saved set!");
                        }
                        catch(Exception e){e.printStackTrace();}
                    }
                });
            }
        }, 0, 30000); // End of your timer code.
    }


    public void updateDisplay() {

        updateTimer = new Timer();
        updateTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                // Your logic here...

                // When you need to modify a UI element, do so on the UI thread.
                // 'getActivity()' is required as this is being ran from a Fragment.

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(HeatmapLogic.getListLatLngs()!= null)
                            if(HeatmapLogic.getListLatLngs().size()>0) {

                                //SerialLatLng last = getLastElement(storedLatLngs);
                                SerialLatLng last =
                                        HeatmapLogic.getListLatLngs().get(HeatmapLogic.getListLatLngs().size()-1);

                                double latitude = last.getLatitude();
                                double longitude = last.getLongitude();
                                updateLocation(latitude, longitude);

                            }
                        // This code will always run on the UI thread, therefore is safe to modify UI elements.
                        //myTextBox.setText("my text");
                    }
                });
            }
        }, 0, 10000); // End of your timer code.

    }


    @Override
    protected void onStart() {
        super.onStart();
       // movedToLocation = false;
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
       // movedToLocation = false;
        //updateButtonsState(Utils.getRequestingLocationUpdates(this));
        //mLocationUpdatesResultView.setText(Utils.getLocationUpdatesResult(this));
        // This registers mMessageReceiver to receive messages.
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mMessageReceiver,
                        new IntentFilter("my-integer"));
    }




    // Handling the received Intents for the "my-integer" event
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Extract data included in the Intent
            int msg = intent.getIntExtra("message",-1/*default value*/);
            System.out.println("MESSAGE RECEIVED = " + msg);
            if(msg==0 && updatingFast) {
                updatingFast = false;
                currentModeTextView.setText(" Location update mode = Slow (every 2 minutes)");
                switchToSlowLocationUpdates();
            }
            else if(msg==1 && !updatingFast) {
                updatingFast = true;
                currentModeTextView.setText(" Location update mode = Fast (every 25 seconds)");
                switchToFastLocationUpdates();
            }
        }
    };

    @Override
    protected void onStop() {


        updateTimer.cancel();
        updateTimer.purge();
        updateTimer = null;


        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);


        super.onStop();
    }

    public void moveToCurrentLocation(double latitude, double longitude){
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(latitude,longitude), 15.0f));

        HeatmapTileProvider mProvider = new HeatmapTileProvider.Builder().data(
                HeatmapLogic.serialLatLngToLatLng(HeatmapLogic.getListLatLngs())).build();
        tileOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
    }




    public void buildAndDisplayHeatMap(){

        if(!HeatmapLogic.getListLatLngs().isEmpty()) {
            HeatmapTileProvider mProvider = new HeatmapTileProvider.Builder().data(
                    HeatmapLogic.serialLatLngToLatLng(HeatmapLogic.getListLatLngs())).build();
            mProvider.setRadius(10);
            tileOverlay.remove();
            tileOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
        }
    }

    public void updateLocation(double latitude, double longitude){

        currentLatLngTextView.setText("Current Lat/Long = " + latitude+ "/" + longitude);

        if(!movedToLocation){
            moveToCurrentLocation(latitude,longitude);
            movedToLocation = true;
        }
        else
            buildAndDisplayHeatMap();
    }

    /**
     * Sets up the location request. Android has two location request settings:
     * {@code ACCESS_COARSE_LOCATION} and {@code ACCESS_FINE_LOCATION}. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     * <p/>
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     * <p/>
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    private void createLocationRequest() {

        mLocationRequestFast = new LocationRequest();
        mLocationRequestFast.setInterval(UPDATE_INTERVAL_FAST);
        mLocationRequestFast.setFastestInterval(FASTEST_UPDATE_INTERVAL_FAST);
        mLocationRequestFast.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequestFast.setMaxWaitTime(MAX_WAIT_TIME_FAST);

        mLocationRequestSlow = new LocationRequest();
        mLocationRequestSlow.setInterval(UPDATE_INTERVAL_SLOW);
        mLocationRequestSlow.setFastestInterval(FASTEST_UPDATE_INTERVAL_SLOW);
        mLocationRequestSlow.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        mLocationRequestSlow.setMaxWaitTime(MAX_WAIT_TIME_SLOW);

    }

    private PendingIntent getPendingIntent() {
        // Note: for apps targeting API level 25 ("Nougat") or lower, either
        // PendingIntent.getService() or PendingIntent.getBroadcast() may be used when requesting
        // location updates. For apps targeting API level O, only
        // PendingIntent.getBroadcast() should be used. This is due to the limits placed on services
        // started in the background in "O".

        // TODO(developer): uncomment to use PendingIntent.getService().
        Intent intent = new Intent(this, LocationUpdatesIntentService.class);
        intent.setAction(LocationUpdatesIntentService.ACTION_PROCESS_UPDATES);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

      //  Intent intent = new Intent(this, LocationUpdatesBroadcastReceiver.class);
      //  intent.setAction(LocationUpdatesBroadcastReceiver.ACTION_PROCESS_UPDATES);
      //  return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }




    /**
     * Return the current state of the permissions needed.
     */
    private boolean checkPermissions() {
        int fineLocationPermissionState = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION);

        int backgroundLocationPermissionState = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION);

        return (fineLocationPermissionState == PackageManager.PERMISSION_GRANTED) &&
                (backgroundLocationPermissionState == PackageManager.PERMISSION_GRANTED);
    }

    private void requestPermissions() {

        boolean permissionAccessFineLocationApproved =
                ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        boolean backgroundLocationPermissionApproved =
                ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        boolean shouldProvideRationale =
                permissionAccessFineLocationApproved && backgroundLocationPermissionApproved;

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            Snackbar.make(
                    findViewById(R.id.map),
                    R.string.permission_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            // Request permission
                            ActivityCompat.requestPermissions(MapsActivity.this,
                                    new String[] {
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_BACKGROUND_LOCATION },
                                    REQUEST_PERMISSIONS_REQUEST_CODE);
                        }
                    })
                    .show();
        } else {
            Log.i(TAG, "Requesting permission");
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(MapsActivity.this,
                    new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION },
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionResult");
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");

            } else if ((grantResults[0] == PackageManager.PERMISSION_GRANTED) && true
                  //  (grantResults[1] == PackageManager.PERMISSION_GRANTED)
            ) {
                // Permission was granted.
                requestLocationUpdates(null);

            } else {
                // Permission denied.

                // Notify the user via a SnackBar that they have rejected a core permission for the
                // app, which makes the Activity useless. In a real app, core permissions would
                // typically be best requested during a welcome-screen flow.

                // Additionally, it is important to remember that a permission might have been
                // rejected without asking the user for permission (device policy or "Never ask
                // again" prompts). Therefore, a user interface affordance is typically implemented
                // when permissions are denied. Otherwise, your app could appear unresponsive to
                // touches or interactions which have required permissions.
                Snackbar.make(
                        findViewById(R.id.map),
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.settings, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Build intent that displays the App settings screen.
                                Intent intent = new Intent();
                                intent.setAction(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package",
                                        BuildConfig.APPLICATION_ID, null);
                                intent.setData(uri);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        })
                        .show();
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals(Utils.KEY_LOCATION_UPDATES_RESULT)) {
           // mLocationUpdatesResultView.setText(Utils.getLocationUpdatesResult(this));
        } else if (s.equals(Utils.KEY_LOCATION_UPDATES_REQUESTED)) {
            updateButtonsState(Utils.getRequestingLocationUpdates(this));
        }
    }

    /**
     * Handles the Request Updates button and requests start of location updates.
     */
    public void requestLocationUpdates(View view) {
        try {
            Log.i(TAG, "Starting location updates");
            Utils.setRequestingLocationUpdates(this, true);
            mFusedLocationClientFast.requestLocationUpdates(mLocationRequestFast, getPendingIntent());
        } catch (SecurityException e) {
            Utils.setRequestingLocationUpdates(this, false);
            e.printStackTrace();
        }
    }

    /**
     * Handles the Remove Updates button, and requests removal of location updates.
     */
    public void removeLocationUpdates(View view) {
        Log.i(TAG, "Removing location updates");
        Utils.setRequestingLocationUpdates(this, false);
        mFusedLocationClientFast.removeLocationUpdates(getPendingIntent());
        mFusedLocationClientSlow.removeLocationUpdates(getPendingIntent());
    }

    public void switchToSlowLocationUpdates(){
        System.out.println("SWITCHING TO SLOW MODE");
        Utils.setRequestingLocationUpdates(this, false);
        mFusedLocationClientFast.removeLocationUpdates(getPendingIntent());
        Utils.setRequestingLocationUpdates(this, true);
        try {
            mFusedLocationClientSlow.requestLocationUpdates(mLocationRequestSlow, getPendingIntent());
        }catch(SecurityException e){System.out.println("security exception"); }
    }

    public void switchToFastLocationUpdates(){
        System.out.println("SWITCHING TO FAST MODE");
        Utils.setRequestingLocationUpdates(this, false);
        mFusedLocationClientFast.removeLocationUpdates(getPendingIntent());
        Utils.setRequestingLocationUpdates(this, true);
        try {
            mFusedLocationClientFast.requestLocationUpdates(mLocationRequestFast, getPendingIntent());
        }catch(SecurityException e){System.out.println("security exception"); }
    }

    /**
     * Ensures that only one button is enabled at any time. The Start Updates button is enabled
     * if the user is not requesting location updates. The Stop Updates button is enabled if the
     * user is requesting location updates.
     */
    private void updateButtonsState(boolean requestingLocationUpdates) {
        if (requestingLocationUpdates) {
         //   mRequestUpdatesButton.setEnabled(false);
         //   mRemoveUpdatesButton.setEnabled(true);
        } else {
         //   mRequestUpdatesButton.setEnabled(true);
         //   mRemoveUpdatesButton.setEnabled(false);
        }
    }

}
