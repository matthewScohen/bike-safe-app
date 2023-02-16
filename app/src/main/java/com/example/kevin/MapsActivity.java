package com.example.kevin;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.kevin.databinding.ActivityMapsBinding;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
    BLEForegroundService mService;
    boolean mBound = false;
    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    String response;
    FusedLocationProviderClient fusedLocationProviderClient;
    LocationRequest locationRequest;
    LocationCallback locationCallback;
    LinkedList<Pair<LatLng, String>> stepsFIFO = new LinkedList<>();
    byte[] slightLeft = "0255550000".getBytes(StandardCharsets.UTF_8);
    byte[] slightRight = "0255551111".getBytes(StandardCharsets.UTF_8);
    byte[] right = "0505551111".getBytes(StandardCharsets.UTF_8);
    byte[] left = "0505550000".getBytes(StandardCharsets.UTF_8);
    byte[] offLeft = "0005550000".getBytes(StandardCharsets.UTF_8);
    byte[] offRight = "0005551111".getBytes(StandardCharsets.UTF_8);

    boolean wasLeft;
    boolean wasRight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        // Bind to BLE service
            // Can never arrive at this activity unless the service is started, so doing
            // this in onCreate without a check is fine
        Intent intentbind = new Intent(this, BLEForegroundService.class);
        intentbind.putExtra("inNav", true);
        bindService(intentbind, connection, Context.BIND_AUTO_CREATE);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        Intent intent = getIntent();
        response = intent.getStringExtra("API_RESP");

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        // Tinker with numbers to see how long vibration goes for
        // Consider sleeping on vibration in code.py to then send vibration with 0 strength. handle on hardware side
        locationRequest = new LocationRequest.Builder(102, 500).setMinUpdateDistanceMeters(2).build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {

                if(mService.isDisconnected()){

                    mService.stopService();
                    unbindService(connection);

                    // Once stopped, on serviceDisconnected should be
                    // called, which will return user to mainactivity
                }


                if (locationResult == null) {
                    return;
                }


                // After for loop, turn off motor if not near a location within location list
                // When not within range but still navigating, turn off motors
                if(wasRight){
                    mService.writeMessage(offRight);
                    wasRight = false;
                }
                else if(wasLeft){
                    mService.writeMessage(offLeft);
                    wasLeft = false;
                }


                for (Location location : locationResult.getLocations()) {
                    //TODO:
                    // see if close to right/left turn
                    // see if on the right path (not for beta build)
//                    Log.d("LOCATION UPDATE:", "NEW LOCATION" + location.toString());


                    // Get range (distance) from turn coordinates
                    // If pastRange > 10m and currRange < 10m , vibrate for x seconds
                    // Otherwise don't vibrate, still in the midst of turn
                    if(isWithinRange(new LatLng(location.getLatitude(), location.getLongitude()), 10)){
                        //if within 10 meters
                        stepsFIFO.removeFirst();
                        if(stepsFIFO.isEmpty()){
                            //send both motors command
                            mService.writeMessage(left);
                            mService.writeMessage(right);
                        }
                        else{
                            String maneuver = stepsFIFO.get(0).second;
                            Log.d("TURN COMMAND: ", maneuver);
                            switch(maneuver) {
                                case "turn-right":
                                    //send right motor command
                                    mService.writeMessage(right);
                                    wasRight = true;
                                    break;
                                case "turn-left":
                                    //send left motor command
                                    mService.writeMessage(left);
                                    wasLeft = true;
                                    break;
                                case "turn-slight-left":
                                    //send slight-left motor command
                                    wasLeft = true;
                                    mService.writeMessage(slightLeft);
                                    break;
                                case "turn-slight-right":
                                    //send slight-right motor command
                                    wasRight = true;
                                    mService.writeMessage(slightRight);
                                    break;
                                default:
                                    break;
                            }
                        }
                    }


                }

            }
        };
        startLocationUpdates();
    }

    @Override
    protected void onStop(){
        super.onStop();
    }

    public boolean isWithinRange(LatLng coords, double range){
        LatLng turn = stepsFIFO.get(0).first;

//        double distance = 6371000 * Math.acos(Math.sin(coords.latitude)*Math.sin(turn.latitude) + Math.cos(coords.latitude)*Math.cos(turn.latitude)*Math.cos(coords.longitude - turn.longitude));
        double distance = 111139 * Math.sqrt(Math.pow((turn.latitude - coords.latitude), 2) + Math.pow((turn.longitude - coords.longitude), 2));
        return distance <= range;
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
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
        JSONObject json;
        JSONArray routes;
        JSONObject overview;
        String points;
        LatLng sw, ne;
        LatLng destination;
        JSONArray steps;

        try {
            json = new JSONObject(response);
            routes = json.getJSONArray("routes");
            overview = routes.getJSONObject(0).getJSONObject("overview_polyline");
            points = overview.getString("points");
            ne = new LatLng(Double.parseDouble(routes.getJSONObject(0).getJSONObject("bounds").getJSONObject("northeast").getString("lat")),
                    Double.parseDouble(routes.getJSONObject(0).getJSONObject("bounds").getJSONObject("northeast").getString("lng")));
            sw = new LatLng(Double.parseDouble(routes.getJSONObject(0).getJSONObject("bounds").getJSONObject("southwest").getString("lat")),
                    Double.parseDouble(routes.getJSONObject(0).getJSONObject("bounds").getJSONObject("southwest").getString("lng")));
            destination = new LatLng(Double.parseDouble(routes.getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONObject("end_location").getString("lat")),
                    Double.parseDouble(routes.getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONObject("end_location").getString("lng")));
            steps = routes.getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONArray("steps");
            getQ(steps);

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        List<LatLng> lat_lang = decodePoly(points);
        PolylineOptions options = new PolylineOptions().geodesic(true).addAll(lat_lang);

        Polyline line = mMap.addPolyline(options);
        line.setWidth(15);
        line.setColor(0xff0082ff);
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(new LatLngBounds(sw, ne), 100));
        mMap.addMarker(new MarkerOptions().position(destination));

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(true);
    }

    private void getQ(JSONArray steps){
        //init stepsQ

        JSONObject tempStep;
        LatLng tempLatLng;
        String tempManeuver = "";
        try {
            for (int i = 0; i < steps.length(); i++) {
                //get ith item
                tempStep = steps.getJSONObject(i);

                //get lat/lng of step->end_location->lat/lng
                tempLatLng = new LatLng(Double.parseDouble(tempStep.getJSONObject("end_location").getString("lat")), Double.parseDouble(tempStep.getJSONObject("end_location").getString("lng")));

                //get maneuver of step->maneuver
                if(tempStep.has("maneuver")) {
                    tempManeuver = tempStep.getString("maneuver");
                }
                else{
                    tempManeuver = "none";
                }
                //add pair to queue
                stepsFIFO.add(new Pair(tempLatLng, tempManeuver));
            }
        } catch(Exception e){
            e.printStackTrace();
            return;
        }
    }

    //decode function from @'Mushahid Khatri' at https://stackoverflow.com/questions/39454857/how-to-buffer-a-polyline-in-android-or-draw-a-polygon-around-a-polyline/42664925#42664925
    //based on the encoding found at https://developers.google.com/maps/documentation/utilities/polylinealgorithm
    private List<LatLng> decodePoly(String points) {

        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = points.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = points.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = points.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }
        return poly;
    }

    private ServiceConnection connection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName className, IBinder service){
            BLEForegroundService.LocalBinder binder = (BLEForegroundService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0){
            mBound = false;
            // Go back to connection screen, tell user they've been disconnected
            Intent intent = new Intent(MapsActivity.this, MainActivity.class);
            intent.putExtra("dc_from_BLE", "DISCONNECTED");
            startActivity(intent);
        }
    };
}