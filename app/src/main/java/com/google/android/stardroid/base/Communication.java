package com.google.android.stardroid.base;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;

import android.os.AsyncTask;
import android.os.Bundle;

import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.InjectableActivity;

import java.io.IOException;
import java.util.UUID;

public class Communication extends InjectableActivity {
    private static final String TAG = "Comn";

    String address = null;
    private ProgressDialog progress;
    BluetoothAdapter myBluetooth = null;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    private StringBuilder DataStringIN = new StringBuilder();
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    Button data;


    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_communication);


         data = findViewById(R.id.data_button);

        Intent newint = getIntent();
        address = newint.getStringExtra(bluetooth.EXTRA_ADDRESS);

        new Communication.BTbaglan().execute();   // execute = gerçekleştir

         data.setOnClickListener(new View.OnClickListener(){

             @Override
             public void onClick(View v) {
                 sendData("1");
             }
         });
    }

    private void sendData(String s) {
        if ( btSocket != null ) {
            try {
                btSocket.getOutputStream().write(s.toString().getBytes());
            } catch (IOException e) {
                Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void Disconnect() {
        if (btSocket != null) {
            try {
                btSocket.close();
            } catch (IOException e) {
                // msg("Error");
            }
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Disconnect();
    }


    private class BTbaglan extends AsyncTask<Void, Void, Void> {
        private boolean ConnectSuccess = true;

        @Override
        protected void onPreExecute() {
            progress = ProgressDialog.show(Communication.this, "Baglanıyor...", "Lütfen Bekleyin");
        }

        // https://gelecegiyazanlar.turkcell.com.tr/konu/android/egitim/android-301/asynctask
        @Override
        protected Void doInBackground(Void... devices) {
            try {
                if (btSocket == null || !isBtConnected) {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();
                    BluetoothDevice cihaz = myBluetooth.getRemoteDevice(address);

                    btSocket = cihaz.createInsecureRfcommSocketToServiceRecord(myUUID);

                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();

                    btSocket.connect();


                }
            } catch (IOException e) {
                ConnectSuccess = false;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            if (!ConnectSuccess) {
                // msg("Baglantı Hatası, Lütfen Tekrar Deneyin");
                Toast.makeText(getApplicationContext(), "Is it a SPP Bluetooth? Bağlantı Hatası Tekrar Deneyin", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                //   msg("Baglantı Basarılı");
                Toast.makeText(getApplicationContext(), "Bağlantı Başarılı", Toast.LENGTH_SHORT).show();

                isBtConnected = true;
            }
            progress.dismiss();
        }

    }




}