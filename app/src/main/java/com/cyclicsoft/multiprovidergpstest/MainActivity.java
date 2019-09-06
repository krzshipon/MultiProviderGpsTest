package com.cyclicsoft.multiprovidergpstest;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity implements LocationListener, View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    // Thresholds for location updates
    private static final long MIN_TIME = 1000;//1s
    private static final float MIN_DIST = 1; //1 metre
    private static final long TIME_FOR_LOCATION_UPDATE_REMOVE = 3 * 60 * 1000;//Three minutes
    // Location manager
    private LocationManager mLocationManager;
    private String provider;
    private boolean isRequesting = false;
    // Time Tracking
    Handler handler = new Handler();
    Runnable runner = new Runnable() {
        @Override
        public void run() {
            stopLocationUpdates();
        }
    };
    // lists for permissions
    private ArrayList<String> permissionsToRequest;
    private ArrayList<String> permissionsRejected = new ArrayList<>();
    private ArrayList<String> permissions = new ArrayList<>();
    // integer for permissions results request
    private static final int ALL_PERMISSIONS_RESULT = 1011;
    // Locations
    private String pastLoc = "";
    private String currentLoc = "";
    // Ui
    private RadioGroup radioGroup;
    TextView tvUpdateStatus, tvProvider, tvPastLoc, tvCurrentLoc, tvTime;
    Button btStartUpdate, btRemoveUpdate, btLastKnownLoc, btReset;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Location manager initialization
        init();
        // Ui initialization
        initUi();

        // we add permissions we need to request location of the users
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissionsToRequest = permissionsToRequest(permissions);
        // Request permissions if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (permissionsToRequest.size() > 0) {
                requestPermissions(permissionsToRequest.toArray(
                        new String[permissionsToRequest.size()]), ALL_PERMISSIONS_RESULT);
            }
        }

        // Location strategy selection
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {

                switch (checkedId) {
                    case R.id.radio_button_gp:
                        stopLocationUpdates();
                        updateStrategyForGPS();
                        clearLoc();
                        return;
                    case R.id.radio_button_np:
                        stopLocationUpdates();
                        updateStrategyForNetwork();
                        clearLoc();
                        return;
                    case R.id.radio_button_bp:
                        stopLocationUpdates();
                        updateStrategyForBestProvider();
                        clearLoc();
                        return;
                    default:
                        updateStrategyForGPS();
                }
            }
        });

        btStartUpdate.setOnClickListener(this);
        btRemoveUpdate.setOnClickListener(this);
        btLastKnownLoc.setOnClickListener(this);
        btReset.setOnClickListener(this);

        //check provider
        if(!isProviderEnabled()){
            buildAlertMessageNoGps();
        }



    }



    private void init() {
        Log.d(TAG, "init()");
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        provider = LocationManager.GPS_PROVIDER;
    }


    private void initUi() {
        Log.d(TAG, "initUi()");
        radioGroup = findViewById(R.id.radio_group_providers);
        tvUpdateStatus = findViewById(R.id.tv_update_status);
        tvProvider = findViewById(R.id.tv_provider);
        btStartUpdate = findViewById(R.id.btn_start_update);
        btRemoveUpdate = findViewById(R.id.btn_stop_update);
        tvPastLoc = findViewById(R.id.tv_past_loc);
        tvCurrentLoc = findViewById(R.id.tv_current_loc);
        btLastKnownLoc = findViewById(R.id.btn_last_known_loc);
        btReset = findViewById(R.id.btn_reset);
        tvTime = findViewById(R.id.tv_time);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        Log.d(TAG, "onClick(): ID> " + id);
        switch (id) {
            case R.id.btn_start_update:
                startLocationUpdates();
                return;
            case R.id.btn_stop_update:
                stopLocationUpdates();
                return;
            case R.id.btn_last_known_loc:
                stopLocationUpdates();
                getLastKnownLoc();
                return;
            case R.id.btn_reset:
                reset();
                return;
        }

    }




    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "onLocationChanged()");
        if (location != null) {
            pastLoc = currentLoc;
            currentLoc = "Lat: " + location.getLatitude() + " ,Lon: " + location.getLongitude();
            updateLocUi();
            tvTime.setText(Calendar.getInstance().getTime().toString());
        }

    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
        Log.d(TAG, "onStatusChanged()");

    }

    @Override
    public void onProviderEnabled(String s) {
        Log.d(TAG, "onProviderEnabled()");
        tvProvider.setText(provider);

    }

    @Override
    public void onProviderDisabled(String s) {
        Log.d(TAG, "onProviderDisabled()");
        tvProvider.setText(provider + " provider Disabled");

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult()");
        switch (requestCode) {
            case ALL_PERMISSIONS_RESULT:
                for (String perm : permissionsToRequest) {
                    if (!hasPermission(perm)) {
                        permissionsRejected.add(perm);
                    }
                }

                if (permissionsRejected.size() > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (shouldShowRequestPermissionRationale(permissionsRejected.get(0))) {
                            new AlertDialog.Builder(MainActivity.this).
                                    setMessage("These permissions are mandatory to get your location. You need to allow them.").
                                    setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                requestPermissions(permissionsRejected.
                                                        toArray(new String[permissionsRejected.size()]), ALL_PERMISSIONS_RESULT);
                                            }
                                        }
                                    }).setNegativeButton("Cancel", null).create().show();

                            return;
                        }
                    }
                } else {
                }

                break;
        }
    }

    private boolean hasPermission(String permission) {
        Log.d(TAG, "hasPermission()");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    private ArrayList<String> permissionsToRequest(ArrayList<String> wantedPermissions) {
        Log.d(TAG, "permissionsToRequest()");
        ArrayList<String> result = new ArrayList<>();

        for (String perm : wantedPermissions) {
            if (!hasPermission(perm)) {
                result.add(perm);
            }
        }

        return result;
    }

    private boolean isProviderEnabled(){
        if( mLocationManager!=null ){
            return mLocationManager.isProviderEnabled(provider);
        }
        return false;
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    private void getLastKnownLoc() {
        Log.d(TAG, "getLastKnownLoc()");
        //check provider
        if(!isProviderEnabled()){
            tvProvider.setText(provider+" provider disabled");
            buildAlertMessageNoGps();
        }

        if (mLocationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    if (permissionsToRequest.size() > 0) {
                        requestPermissions(permissionsToRequest.toArray(
                                new String[permissionsToRequest.size()]), ALL_PERMISSIONS_RESULT);
                    }
                    return;
                }
            }
            // Has permission
            Location location = mLocationManager.getLastKnownLocation(provider);
            updateStatus("Last Known Location");
            if (location != null) {
                pastLoc = currentLoc;
                currentLoc = "Lat: " + location.getLatitude() + " ,Lon: " + location.getLongitude();
                updateLocUi();
            }else {
                currentLoc = "";
                Toast.makeText(this, "Last Known Location--->Null!!", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void startLocationUpdates() {
        Log.d(TAG, "startLocationUpdates()");
        //check provider
        if(!isProviderEnabled()){
            buildAlertMessageNoGps();
        }
        if (mLocationManager != null) {
            if (Build.VERSION.SDK_INT >= 23)
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    if (permissionsToRequest.size() > 0) {
                        requestPermissions(permissionsToRequest.toArray(
                                new String[permissionsToRequest.size()]), ALL_PERMISSIONS_RESULT);
                    }
                    return;
                }
            if(!isRequesting) {
                Toast.makeText(this, "Requesting location updates", Toast.LENGTH_SHORT).show();
                mLocationManager.requestLocationUpdates(provider, MIN_TIME, MIN_DIST, this);
                updateStatus("Request Ongoing");
                isRequesting = true;
                handler.postDelayed(runner, TIME_FOR_LOCATION_UPDATE_REMOVE);
            }else {
                Toast.makeText(this, "Already requesting", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void stopLocationUpdates() {
        Log.d(TAG, "stopLocationUpdates()");
        if(mLocationManager != null && isRequesting){
            mLocationManager.removeUpdates(this);
            isRequesting = false;
            handler.removeCallbacks(runner);
            updateStatus("Request Stopped");
        }

    }


    private void updateStrategyForGPS() {
        Log.d(TAG, "updateStrategyForGPS()");
        provider = LocationManager.GPS_PROVIDER;
        tvProvider.setText(provider);

    }
    private void updateStrategyForNetwork() {
        Log.d(TAG, "updateStrategyForNetwork()");
        provider = LocationManager.NETWORK_PROVIDER;
        tvProvider.setText(provider);

    }

    private void updateStrategyForBestProvider() {
        Log.d(TAG, "updateStrategyForBestProvider()");
        provider = mLocationManager.getBestProvider(new Criteria(), false);
        tvProvider.setText(provider);
    }

    private void updateStatus(String status) {
        Log.d(TAG, "updateStatus()");
        tvUpdateStatus.setText(status);
    }

    private void updateLocUi() {
        Log.d(TAG, "updateLocUi()");
        clearLocUI();
        tvPastLoc.setText(pastLoc);
        tvCurrentLoc.setText(currentLoc);
    }

    private void clearLocUI() {
        tvPastLoc.setText("");
        tvCurrentLoc.setText("");
    }

    private void clearLoc(){
        pastLoc = "";
        currentLoc = "";
        clearLocUI();
    }


    private void reset() {
        stopLocationUpdates();
        pastLoc = "";
        currentLoc = "";
        updateLocUi();
        provider = LocationManager.GPS_PROVIDER;
        tvProvider.setText(provider);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart()");
        super.onStart();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        stopLocationUpdates();
        super.onDestroy();
    }
}
