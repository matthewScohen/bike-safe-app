package com.example.kevin;

import static com.example.kevin.BuildConfig.MAPS_API_KEY;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;


public class Act2 extends AppCompatActivity {

    EditText et_Phone;
    Button btn_Phone;
    EditText et_Dest;
    Button btn_goMaps;
    private BluetoothGatt ble_gatt = null;
    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // We successfully connected, proceed with service discovery
                    Log.d("bike_safe_only", "device connected");
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // We successfully disconnected on our own request


                    // Probably should change back to connection activity


                    gatt.close();
                } else {
                    // We're CONNECTING or DISCONNECTING, ignore for now
                }
            } else {
                // An error happened...figure out what happened!

                // Probably should change back to connection activity
                gatt.close();
            }
        }
    };
    int PERMISSION_ID = 44;
    String origin_Coords = "29.6465, -82.3479";
    FusedLocationProviderClient FLocationClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_act2);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Receive the extra
        Intent intent = getIntent();
        String dev_addr = intent.getStringExtra("DEV_ADDR");



        if(!dev_addr.equals("skip")) {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(dev_addr);
            BluetoothGatt ble_gatt = device.connectGatt(Act2.this, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);
        }

        FLocationClient = LocationServices.getFusedLocationProviderClient(this);


        String origin = getUserLocation();


        et_Phone = (EditText) findViewById(R.id.et_Phone);
        btn_Phone = (Button) findViewById(R.id.btn_Phone);
        et_Dest = (EditText) findViewById(R.id.et_Dest);
        btn_goMaps = (Button) findViewById(R.id.btn_goMaps);

        btn_Phone.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                byte[] message = getMessage();
                Log.d("Phone", new String(message, StandardCharsets.UTF_8));

                UUID UART_UUID = ble_gatt.getServices().get(2).getUuid();
                BluetoothGattCharacteristic RX_CHAR = ble_gatt.getServices().get(2).getCharacteristics().get(1);
                RX_CHAR.setValue(message);
                RX_CHAR.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                ble_gatt.writeCharacteristic(RX_CHAR);
            }
        });


        btn_goMaps.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                // get current userlocation from which to show directions


                String dest = et_Dest.getText().toString();
                String response = getResponse(v, origin_Coords, dest);
                Log.d("connect", response);

                Intent intent = new Intent(Act2.this, MapsActivity.class);
                intent.putExtra("API_RESP", response);
                startActivity(intent);
            }
        });


    }

    public byte[] getMessage(){
        String number = et_Phone.getText().toString();
        if(number.length() == 12){
            number = number.substring(0,3) + number.substring(4,7) + number.substring(8);
        }
        return number.getBytes(StandardCharsets.UTF_8);
    }

    public String getResponse(View view, String origin, String dest){
        String starting = origin;
        String destination = dest;
        String requestStart = "https://maps.googleapis.com/maps/api/directions/json?origin=";
        String requestMid = "&destination=";
        String requestEnd = "&mode=bicycling&key=";
        String request = requestStart + starting + requestMid + destination + requestEnd + MAPS_API_KEY;

        //Snackbar.make(view, request, Snackbar.LENGTH_LONG).show();
        String response = "";
        URL url;
        HttpURLConnection urlConnection = null;

        try {
            url = new URL(request);

            urlConnection = (HttpURLConnection) url.openConnection();

            Log.d("connect", "openconnect success");

            //urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            urlConnection.getInputStream()
                    )
            );

            Log.d("connect", "BufferedReader in made successfully");

            String inpLine;

            while ((inpLine = in.readLine()) != null) {
                response += "\n" +inpLine;
            }
            in.close();

        } catch (Exception e){
            e.printStackTrace();
        } finally {
            if(urlConnection != null) urlConnection.disconnect();
        }
        return response;
    }


    // Following methods use
    // https://geeksforgeeks.org/how-to-get-user-location-in-android
    // as a source for permission checking help.

    public boolean checkPermissions(){
        boolean cl = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean fl = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return cl && fl;
    }

    public void requestPermissions(){
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_ID);
    }

    public boolean isLocationEnabled(){
        LocationManager locMan = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locMan.isProviderEnabled(LocationManager.GPS_PROVIDER) || locMan.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == PERMISSION_ID){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                getUserLocation();
            }
        }
    }

    @Override
    public void onResume(){
        super.onResume();
        if(checkPermissions()){
            getUserLocation();
        }
    }


    @SuppressLint("MissingPermission")
    public String getUserLocation(){


        if(checkPermissions()){
            if(isLocationEnabled()){
                FLocationClient.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        Location loc = task.getResult();
                        if(loc != null){
                            origin_Coords = "";
                            origin_Coords += loc.getLatitude();
                            origin_Coords += ", " + loc.getLongitude();
                        } else {
                            Log.d("location","null location");
                        }
                    }
                });
            } else {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        } else {
            requestPermissions();
        }

        return origin_Coords;
    }









}