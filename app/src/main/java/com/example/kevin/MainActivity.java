package com.example.kevin;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;
// GITHUB WORKING PROJECT
public class MainActivity extends AppCompatActivity {
    //Device container for display
    private ArrayList<BluetoothDevice> leDevices = new ArrayList<BluetoothDevice>();

    // Callback from LE Device Scanning
        // Adds found devices to list
    private ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    if(!leDevices.contains(result.getDevice())){
                        leDevices.add(result.getDevice());
                    }
                }
            };

    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning;
    private Handler handler = new Handler();

    private BluetoothGatt ble_gatt = null;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    // Have to trigger this multiple times for some reason?
    private void scanLeDevice() {
        if (!scanning) {
            // Stops scanning after a predefined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            scanning = true;
            bluetoothLeScanner.startScan(leScanCallback);
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

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
                    gatt.close();
                } else {
                    // We're CONNECTING or DISCONNECTING, ignore for now
                }
            } else {
                // An error happened...figure out what happened!
                gatt.close();
            }
        }



        @Override
        public void onServicesDiscovered (BluetoothGatt gatt, int status)
        {
            ble_gatt = gatt;

            // the 3rd service in the list is the UART service

            UUID UART_UUID = gatt.getServices().get(2).getUuid();
            BluetoothGattCharacteristic RX_CHAR = gatt.getServices().get(2).getCharacteristics().get(1);
            UUID UART_WRITE_CHARACTERISTIC = RX_CHAR.getUuid();

            Log.d("bike_safe_only", "found service " + UART_UUID);
            Log.d("bike_safe_only", "found characteristic " + UART_WRITE_CHARACTERISTIC);

//            byte[] message = phoneNum.getText().toString().getBytes(StandardCharsets.UTF_8);
//            RX_CHAR.setValue(message);
//            RX_CHAR.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
//            gatt.writeCharacteristic(RX_CHAR);
        }


    };





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Define intent launcher for the call to have users turn on their bluetooth
        ActivityResultLauncher<Intent> bleResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_CANCELED) {
                            Toast.makeText(MainActivity.this, R.string.ble_denied, Toast.LENGTH_LONG).show();

                        }
                    }
                });
        // Display layout
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Check for Bluetooth Functionality on Phone
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            //finish();
        }


        // Scan button
        Button btnScan = findViewById(R.id.btn_scan);
        btnScan.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                /*
                LinearLayout devlist = findViewById(R.id.ll_devlist);
                TextView tv = new TextView(MainActivity.this);
                tv.setText(etItem.getText());
                devlist.addView(tv);

                 */
                // Get Bluetooth Adapter (used for all BLE functionality)
                BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
                BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
                if (bluetoothAdapter == null) {
                    // Device doesn't support Bluetooth
                    Toast.makeText(MainActivity.this, R.string.ble_off, Toast.LENGTH_SHORT).show();
                    //finish();
                }
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

                // Check if BLE is on, otherwise ask to turn it on
                if (!bluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    bleResultLauncher.launch(enableBtIntent);

                    //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }


                // Initiate scan for BLE devices -
                    // Add available devices as views to scrolling list in mainactivity
                        // Those views in scrolling list will have onClicks that initiate
                        // Connection to the associated device
                scanLeDevice();



                for(int i = 0; i < leDevices.size(); i++){
                    LinearLayout devlist = findViewById(R.id.ll_devlist);
                    Button newBtn = new Button(MainActivity.this);
                    newBtn.setText(leDevices.get(i).getName() + " " +  leDevices.get(i).getAddress());

                    String currAddress = leDevices.get(i).getAddress();
                    newBtn.setOnClickListener(new View.OnClickListener(){
                        public void onClick(View v){
                            // Connect to device

                            //BluetoothDevice device = bluetoothAdapter.getRemoteDevice(currAddress);

                            //int deviceType = device.getType();

                            //if(deviceType == BluetoothDevice.DEVICE_TYPE_UNKNOWN){
                            //    Log.d("bike_safe_only"),
                            //}

                            //BluetoothGatt gatt = device.connectGatt(MainActivity.this, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);

                            Intent intent = new Intent(MainActivity.this, Act2.class);
                            intent.putExtra("DEV_ADDR", currAddress);
                            startActivity(intent);
                        }
                    });
                    // If it's a bikesafe device, and the same one isn't already displayed..
                    if(newBtn.getText().toString().contains("CIRCUITPY")) {
                        boolean isDuplicate = false;
                        for (int j = 0; j < devlist.getChildCount(); j++) {
                            Button curr = (Button)(devlist.getChildAt(j));
                            if(curr.getText().toString().contains(leDevices.get(i).getAddress()))
                                isDuplicate = true;
                        }
                        if(!isDuplicate)
                            devlist.addView(newBtn);

                    }

                }
            }
        });



    }

    public void skipBtn(View view){
        Intent intent = new Intent(MainActivity.this, Act2.class);
        intent.putExtra("DEV_ADDR", "skip");
        startActivity(intent);
    }

}