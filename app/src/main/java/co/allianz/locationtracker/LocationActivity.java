package co.allianz.locationtracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;


public class LocationActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener, ResultCallback<Status> {

    private static final String TAG = LocationActivity.class.getSimpleName();

    private static final String LOCATION_KEY = "location-key";
    private static final String ACTIVITY_KEY = "activity-key";
    private static final String POBABILITY_KEY = "probability-key";

    // Location API
    private GoogleApiClient googleApiClient;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    private Location mLastLocation;

    // Activity Recognition API
    private ActivityDetectionBroadcastReceiver broadcastReceiver;
    @DrawableRes
    private int imageResource = R.drawable.ic_question;

    private int activityProbility;
    // UI
    private TextView latitude;
    private TextView longitude;
    private TextView altitude;
    private TextView probability;
    private ImageView dectectedActivityIcon;

    // Códigos de petición
    public static final int REQUEST_LOCATION = 1;
    public static final int REQUEST_CHECK_SETTINGS = 2;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Referencias UI
        latitude = (TextView) findViewById(R.id.tv_latitude);
        longitude = (TextView) findViewById(R.id.tv_longitude);
        altitude = (TextView) findViewById(R.id.tv_alatitude);
        probability = (TextView) findViewById(R.id.tv_probability);
        dectectedActivityIcon = (ImageView) findViewById(R.id.iv_activity_icon);

        // Establecer punto de entrada para la API de ubicación
        buildGoogleApiClient();

        // Crear configuración de peticiones
        createLocationRequest();

        // Crear opciones de peticiones
        buildLocationSettingsRequest();

        // Verificar ajustes de ubicación actuales
        checkLocationSettings();

        broadcastReceiver = new ActivityDetectionBroadcastReceiver();

        updateValuesFromBundle(savedInstanceState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (googleApiClient.isConnected()) {
            stopLocationUpdates();
            stopActivityUpdates();
        }

        LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(broadcastReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (googleApiClient.isConnected()) {
            startLocationUpdates();
            startActivityUpdates();
        }

        IntentFilter intentFilter = new IntentFilter(Constants.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Protegemos la ubicación actual antes del cambio de configuración
        outState.putParcelable(LOCATION_KEY, mLastLocation);
        outState.putInt(ACTIVITY_KEY, imageResource);
        outState.putInt(POBABILITY_KEY,activityProbility);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.d(TAG, "El usuario permitió el cambio de ajustes de ubicación.");
                        processLastLocation();
                        startLocationUpdates();
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.d(TAG, "El usuario no permitió el cambio de ajustes de ubicación");
                        break;
                }
                break;
        }
    }

    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length == 1
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                startLocationUpdates();

            } else {
                Toast.makeText(this, "Permisos no otorgados", Toast.LENGTH_LONG).show();
            }
        }
    }

    private synchronized void buildGoogleApiClient() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(ActivityRecognition.API)
                .enableAutoManage(this, this)
                .build();
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest()
                .setInterval(Constants.UPDATE_INTERVAL)
                .setFastestInterval(Constants.UPDATE_FASTEST_INTERVAL)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest)
                .setAlwaysShow(true);
        locationSettingsRequest = builder.build();
    }

    private void checkLocationSettings() {
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(
                        googleApiClient, locationSettingsRequest
                );

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult result) {
                Status status = result.getStatus();

                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        Log.d(TAG, "Los ajustes de ubicación satisfacen la configuración.");
                        startLocationUpdates();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            Log.d(TAG, "Los ajustes de ubicación no satisfacen la configuración. " +
                                    "Se mostrará un diálogo de ayuda.");
                            status.startResolutionForResult(
                                    LocationActivity.this,
                                    REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                            Log.d(TAG, "El Intent del diálogo no funcionó.");
                            // Sin operaciones
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        Log.d(TAG, "Los ajustes de ubicación no son apropiados.");
                        break;

                }
            }
        });
    }

    private void updateValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(LOCATION_KEY)) {
                mLastLocation = savedInstanceState.getParcelable(LOCATION_KEY);

                updateLocationUI();
            }

            if (savedInstanceState.containsKey(ACTIVITY_KEY)) {
                imageResource = savedInstanceState.getInt(ACTIVITY_KEY);

                updateRecognitionUI();
            }
            if (savedInstanceState.containsKey(POBABILITY_KEY)){
                activityProbility = savedInstanceState.getInt(POBABILITY_KEY);

            }
        }
    }

    @SuppressLint("DefaultLocale")
    private void updateLocationUI() {
        latitude.setText(String.valueOf(mLastLocation.getLatitude()));
        longitude.setText(String.valueOf(mLastLocation.getLongitude()));
        altitude.setText(String.valueOf(mLastLocation.getAltitude()));
        }

    private void updateRecognitionUI() {
        dectectedActivityIcon.setImageResource(imageResource);
        probability.setText(String.format(Constants.PROBABILITY_FORMAT,activityProbility));
    }

    private void stopActivityUpdates() {
        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                googleApiClient,
                getActivityDetectionPendingIntent()
        ).setResultCallback(this);
    }

    private void stopLocationUpdates() {
        LocationServices.FusedLocationApi
                .removeLocationUpdates(googleApiClient, this);
    }

    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, DetectedActivitiesIntentService.class);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void getLastLocation() {
        if (isLocationPermissionGranted()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        } else {
            manageDeniedPermission();
        }
    }

    private void processLastLocation() {
        getLastLocation();
        if (mLastLocation != null) {
            updateLocationUI();
        }
    }

    private void startActivityUpdates() {
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                googleApiClient,
                Constants.ACTIVITY_RECOGNITION_INTERVAL,
                getActivityDetectionPendingIntent()
        ).setResultCallback(this);
    }


    private void startLocationUpdates() {
        if (isLocationPermissionGranted()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    googleApiClient, locationRequest, this);
            } else {
                manageDeniedPermission();
            }
        }

        private void manageDeniedPermission() {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Aquí muestras confirmación explicativa al usuario
                // por si rechazó los permisos anteriormente
            } else {
                ActivityCompat.requestPermissions(
                        this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_LOCATION);
            }
        }

        private boolean isLocationPermissionGranted() {
            int permission = ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION);
            return permission == PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {

            // Obtenemos la última ubicación al ser la primera vez
            processLastLocation();
            // Iniciamos las actualizaciones de ubicación
            startLocationUpdates();
            // Y también las de reconocimiento de actividad
            startActivityUpdates();
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "Conexión suspendida");
            googleApiClient.connect();
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Toast.makeText(
                    this,
                    "Error de conexión con el código:" + connectionResult.getErrorCode(),
                    Toast.LENGTH_LONG)
                    .show();

        }

        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG, String.format("Nueva ubicación: (%s, %s)",
                    location.getLatitude(), location.getLongitude()));
            mLastLocation = location;
            updateLocationUI();
        }


        @Override
        public void onResult(@NonNull Status status) {
            if (status.isSuccess()) {
                Log.d(TAG, "Detección de actividad iniciada");

            } else {
                Log.e(TAG, "Error al iniciar/remover la detección de actividad: "
                        + status.getStatusMessage());
            }
        }

        public class ActivityDetectionBroadcastReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                int type = intent.getIntExtra(Constants.ACTIVITY_KEY, -1);
                int probability = intent.getIntExtra(Constants.PROBABILITY, 0);

                imageResource = Constants.getActivityIcon(type);
                activityProbility = probability;
                updateRecognitionUI();
            }

        }
}
