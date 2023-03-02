package com.google.android.stardroid.base;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.InjectableActivity;

import java.util.ArrayList;
import java.util.Set;


public class bluetooth extends InjectableActivity {
    private static final String TAG ="MainActivity";

    BluetoothAdapter myBluetooth;
    Button pair_button;
    ListView pairedlist;
    private Set<BluetoothDevice> pairedDevice;
    public static  String EXTRA_ADDRESS ="device_address";


    private final BroadcastReceiver mBroadcasReceiver1 = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
            }
        }
    };

    private final BroadcastReceiver mBroadcasReceiver2 = new BroadcastReceiver() {
        //Burda Bluetooth  görünürlük/görünmezlik işlemleri var.
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)){
                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE,BluetoothAdapter.ERROR);
                switch (mode) {
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Log.d(TAG, "Görünürlük İzni Verildi");
                        Toast.makeText(getApplicationContext(),"Görünürlük 300 Saniye Boyunca Açık",Toast.LENGTH_LONG).show();
                        break;
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        Log.d(TAG, "Görünürlük İzni Verildi,Bağlantı Alınabilir");
                        Toast.makeText(getApplicationContext(),"Görünürlük 300 Saniye Boyunca Açık",Toast.LENGTH_LONG).show();
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Log.d(TAG, "Görünürlük ve Bağlantı İzni Yok");
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        Log.d(TAG, "Bağlanıyor");
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Log.d(TAG, "Bağlantı Sağlandı");
                        break;
                }
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);
        myBluetooth = BluetoothAdapter.getDefaultAdapter();
        pair_button = findViewById(R.id.listDevice);
        pairedlist = findViewById(R.id.listview);


        pair_button.setOnClickListener(v ->
                listDevice()

        );

    }

    private void listDevice() {

            pairedDevice = myBluetooth.getBondedDevices();



           ArrayList list = new ArrayList();
            if(pairedDevice.size() > 0){
                for (BluetoothDevice bt :pairedDevice){
                    list.add(bt.getName()+"\n"+ bt.getAddress());
                }

            }else{
                Toast.makeText(this, "Eşleşmiş cihaz yok", Toast.LENGTH_SHORT).show();
            }

            final ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1,list);
            pairedlist.setAdapter(adapter);
            pairedlist.setOnItemClickListener(selectDevice);

      }
      public AdapterView.OnItemClickListener selectDevice = new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                   String info = ((TextView) view).getText().toString();
                   String address = info.substring(info.length() - 17);
               Intent comintent = new Intent (bluetooth.this,Communication.class);
                comintent.putExtra(EXTRA_ADDRESS,address);
                startActivity(comintent);
          }
      };


}