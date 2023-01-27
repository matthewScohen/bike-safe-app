package com.example.kevin;

import static com.example.kevin.BuildConfig.MAPS_API_KEY;

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

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



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_act2);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Receive the extra
        Intent intent = getIntent();
        String dev_addr = intent.getStringExtra("DEV_ADDR");




        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(dev_addr);
        BluetoothGatt ble_gatt = device.connectGatt(Act2.this, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);

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
                getResponse(v);
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

    public void getResponse(View view){
        String starting = "Reitz";
        String destination = "SWRC";
        String requestStart = "https://maps.googleapis.com/maps/api/directions/json?origin=";
        String requestMid = "&destination=";
        String requestEnd = "&key=";
        String request = requestStart + starting + requestMid + destination + requestEnd + MAPS_API_KEY;

        //Snackbar.make(view, request, Snackbar.LENGTH_LONG).show();
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
                Log.d("ME", inpLine);
            }
            in.close();

        } catch (Exception e){
            e.printStackTrace();
        } finally {
            if(urlConnection != null) urlConnection.disconnect();
        }

    }








}