package com.example.kevin;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;
import android.util.Log;


import androidx.core.app.NotificationManagerCompat;

import java.util.UUID;

public class BLEForegroundService extends Service {

    private String CHANNEL_ID = "BLENotif";
    private boolean inNav = false;
    private BluetoothGatt ble_gatt = null;
    private int ONGOING_NOTIF_ID = 1;
    private boolean isDisconnected; // = false;
    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
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
                    isDisconnected = true;

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
    public int onStartCommand(Intent intent, int flags, int startId){
        // Receive MAC Address
        String dev_addr = intent.getStringExtra("DEV_ADDR");


        //Create notification channel
        // Create notificatin and start foreground in onBind()


        createNotificationChannel();







        // Perform connection
        if(!dev_addr.equals("skip")) {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(dev_addr);
            ble_gatt = device.connectGatt(BLEForegroundService.this, false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);
            isDisconnected = false;
        }


        return START_NOT_STICKY;
    }

    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder{
        BLEForegroundService getService(){
            return BLEForegroundService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent){
        inNav = intent.getBooleanExtra("inNav", false);

        // If !inNav, return to Act2
        // If inNav, intent to mapsactivity

        Intent notifIntent;
        CharSequence notif_message;
        if(!inNav){
            notifIntent = new Intent(this, Act2.class);
            notif_message = getText(R.string.notif_message_notnav);
        } else {
            notifIntent = new Intent(this, MapsActivity.class);
            notif_message = getText(R.string.notif_message_nav);
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification =
                new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle(getText(R.string.notif_title))
                        .setContentText(notif_message)
                        .setContentIntent(pendingIntent)
                        .setSmallIcon(R.mipmap.ic_launcher_foreground)
                        .build();

        startForeground(ONGOING_NOTIF_ID, notification);
        return binder;
    }




    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library

        CharSequence name = getString(R.string.channel_name);

        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);

        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

    }
    // Methods for clients to call
        // Send BLE data for vibration!

    public void writeMessage(byte[] message){
        UUID UART_UUID = ble_gatt.getServices().get(2).getUuid();
        BluetoothGattCharacteristic RX_CHAR = ble_gatt.getServices().get(2).getCharacteristics().get(1);
        RX_CHAR.setValue(message);
        RX_CHAR.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        ble_gatt.writeCharacteristic(RX_CHAR);
    }

    public boolean isDisconnected(){
        return isDisconnected;
    }

    public void stopService(){
        if(isDisconnected) this.stopSelf();
    }
}
