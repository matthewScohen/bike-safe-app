package com.example.kevin;

import static com.example.kevin.BuildConfig.MAPS_API_KEY;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

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
    BLEForegroundService mService;
    boolean mBound = false;
    EditText et_Phone;
    Button btn_Phone;
    EditText et_Dest;
    Button btn_goMaps;
    Button btn_sendText;
    String dev_addr;

    int PERMISSION_ID = 44;
    String origin_Coords = "29.6465, -82.3479";
    FusedLocationProviderClient FLocationClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_act2);
        et_Phone = (EditText) findViewById(R.id.et_Phone);
        btn_Phone = (Button) findViewById(R.id.btn_Phone);
        et_Dest = (EditText) findViewById(R.id.et_Dest);
        btn_goMaps = (Button) findViewById(R.id.btn_goMaps);
        btn_sendText = (Button) findViewById(R.id.btn_sendText);
        FLocationClient = LocationServices.getFusedLocationProviderClient(this);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        // Receive the extra
        Intent intent = getIntent();
        dev_addr = intent.getStringExtra("DEV_ADDR");

        //Bind to the service, which the prior activity
        // started before transitioning here

        if(!dev_addr.equals("skip")) {
            Intent intentbind = new Intent(this, BLEForegroundService.class);
            intentbind.putExtra("inNav", false);
            bindService(intentbind, connection, Context.BIND_AUTO_CREATE);
        }

        String origin = getUserLocation();

        btn_Phone.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){

                if(!validPhone()){
                    Snackbar.make(v, "Please enter a valid phone number", Snackbar.LENGTH_LONG).show();
                    return;
                }

                if(dev_addr.equals("skip")){
                    Snackbar.make(v, "Please return to previous screen and connect to a bluetooth device", Snackbar.LENGTH_LONG).show();
                    return;
                }

                byte[] message = getMessage();
                Log.d("Phone", new String(message, StandardCharsets.UTF_8));


                // Call service function to send phone number
                // Make sure service is started and THEN bound to so
                // the service outlives the activity
                if(mBound){
                    mService.writeMessage(message);
                }

            }
        });


        btn_goMaps.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                // get current userlocation from which to show directions


                String dest = et_Dest.getText().toString();
                String response = getResponse(v, origin_Coords, dest);
                Log.d("connect", response);

                if(!dev_addr.equals("skip")) {
                    Intent intent = new Intent(Act2.this, MapsActivity.class);
                    intent.putExtra("API_RESP", response);
                    startActivity(intent);
                }
                else {
                    Snackbar.make(findViewById(R.id.constrlay), "Connect to a BikeSafe device before starting navigation!", Snackbar.LENGTH_LONG).show();
                }
            }
        });

        btn_sendText.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                sendSMS();
            }
        });

    }

    protected void sendSMS() {
        Log.d("Send SMS", "");
        Intent smsIntent = new Intent(Intent.ACTION_VIEW);

        smsIntent.setData(Uri.parse("smsto:"));
        smsIntent.setType("vnd.android-dir/mms-sms");
        smsIntent.putExtra("address"  , new String ("4079248680"));
        smsIntent.putExtra("sms_body"  , "Find my bike");

        try {
            startActivity(smsIntent);
            finish();
            Log.d("Finished sending SMS...", "");
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(Act2.this,
                    "SMS faild, please try again later.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStop(){
        super.onStop();
        if(!dev_addr.equals("skip")) unbindService(connection);
        mBound = false;
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
            Intent intent = new Intent(Act2.this, MainActivity.class);
            intent.putExtra("dc_from_BLE", "DISCONNECTED");
            startActivity(intent);
        }
    };

    public boolean validPhone(){
        String number = et_Phone.getText().toString();
        Log.d("PHONE", "starting check with number " + number);
        Log.d("PHONE", "Length: " + number.length());
        if(number.length() == 12){
            Log.d("PHONE", "checking for hyphens");
            if(!((number.charAt(3) == '.' || number.charAt(3) == '-') && (number.charAt(7) == '.' || number.charAt(7) == '-'))){
                //wrong delimiters, return
                return false;
            }

            Log.d("PHONE", "trying to remove hyphens");
            // XXX-XXX-XXXX
            //remove hyphens
            number = number.substring(0,3) + number.substring(4,7) + number.substring(8);
        }
        if(number.length() == 10){
            try{
                long test = Long.parseLong(number);
                if(test < 0){
                    return false;
                }
            }
            catch (Exception e){
                return false;
            }
            return true;
        }
        return false;
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
                FLocationClient.getCurrentLocation(100, null).addOnCompleteListener(new OnCompleteListener<Location>() {
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