// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.stardroid.activities;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.Manifest.permission_group.CAMERA;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.stardroid.ApplicationConstants;
import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;
import com.google.android.stardroid.activities.dialogs.HelpDialogFragment;
import com.google.android.stardroid.activities.dialogs.MultipleSearchResultsDialogFragment;
import com.google.android.stardroid.activities.dialogs.NoSearchResultsDialogFragment;
import com.google.android.stardroid.activities.dialogs.NoSensorsDialogFragment;
import com.google.android.stardroid.activities.dialogs.TimeTravelDialogFragment;
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger;
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger.NightModeable;
import com.google.android.stardroid.activities.util.ActivityLightLevelManager;
import com.google.android.stardroid.activities.util.FullscreenControlsManager;
import com.google.android.stardroid.activities.util.GooglePlayServicesChecker;
import com.google.android.stardroid.base.Communication;
import com.google.android.stardroid.base.Lists;
import com.google.android.stardroid.base.bluetooth;
import com.google.android.stardroid.control.AstronomerModel;
import com.google.android.stardroid.control.AstronomerModel.Pointing;
import com.google.android.stardroid.control.ControllerGroup;
import com.google.android.stardroid.control.MagneticDeclinationCalculatorSwitcher;
import com.google.android.stardroid.inject.HasComponent;
import com.google.android.stardroid.layers.LayerManager;
import com.google.android.stardroid.math.CoordinateManipulationsKt;
import com.google.android.stardroid.math.MathUtils;
import com.google.android.stardroid.math.Vector3;
import com.google.android.stardroid.renderer.RendererController;
import com.google.android.stardroid.renderer.SkyRenderer;
import com.google.android.stardroid.search.SearchResult;
import com.google.android.stardroid.touch.DragRotateZoomGestureDetector;
import com.google.android.stardroid.touch.GestureInterpreter;
import com.google.android.stardroid.touch.MapMover;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.AnalyticsInterface;
import com.google.android.stardroid.util.MiscUtil;
import com.google.android.stardroid.util.SensorAccuracyMonitor;
import com.google.android.stardroid.views.ButtonLayerView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

/**
 * The main map-rendering Activity.
 */
public class DynamicStarMapActivity extends InjectableActivity
        implements OnSharedPreferenceChangeListener, HasComponent<DynamicStarMapComponent> {
  private static final int TIME_DISPLAY_DELAY_MILLIS = 1000;
  private FullscreenControlsManager fullscreenControlsManager;

  public static final int REQUEST_CODE = 100;
  private String[] neededPermissions = new String[]{CAMERA, WRITE_EXTERNAL_STORAGE};

  private SurfaceHolder surfaceHolder;
  private Camera camera;

  private SurfaceView surfaceView;
  private Menu mMenu;


  private enum ScanState {NONE, LESCAN, DISCOVERY, DISCOVERY_FINISHED}

  private ScanState scanState = ScanState.NONE;
  private static final long LESCAN_PERIOD = 10000; // similar to bluetoothAdapter.startDiscovery


  BluetoothSocket btSocket = null;

  private boolean isBtConnected = false;

  private ProgressDialog progress;
  String address;
  Button button1;

  @Override
  public DynamicStarMapComponent getComponent() {
    return daggerComponent;
  }


  /**
   * Passed to the renderer to get per-frame updates from the model.
   *
   * @author John Taylor
   */
  private static final class RendererModelUpdateClosure implements Runnable {
    private RendererController rendererController;
    private AstronomerModel model;
    private boolean viewDirectionMode;

    public RendererModelUpdateClosure(AstronomerModel model,
                                      RendererController rendererController, SharedPreferences sharedPreferences) {
      this.model = model;
      this.rendererController = rendererController;
      // TODO(jontayler): figure out why we need to do this here.
      updateViewDirectionMode(model, sharedPreferences);
    }

    @Override
    public void run() {
      Pointing pointing = model.getPointing();
      float directionX = pointing.getLineOfSightX();
      float directionY = pointing.getLineOfSightY();
      float directionZ = pointing.getLineOfSightZ();

      float upX = pointing.getPerpendicularX();
      float upY = pointing.getPerpendicularY();
      float upZ = pointing.getPerpendicularZ();

      rendererController.queueSetViewOrientation(directionX, directionY, directionZ, upX, upY, upZ);

      Vector3 up = model.getPhoneUpDirection();
      rendererController.queueTextAngle(MathUtils.atan2(up.x, up.y));
      rendererController.queueViewerUpDirection(model.getZenith().copyForJ());

      float fieldOfView = model.getFieldOfView();
      rendererController.queueFieldOfView(fieldOfView);
    }
  }

  private static void updateViewDirectionMode(AstronomerModel model, SharedPreferences sharedPreferences) {
    String viewDirectionMode =
            sharedPreferences.getString(ApplicationConstants.VIEW_MODE_PREFKEY, "STANDARD");
    switch (viewDirectionMode) {
      case "ROTATE90":
        model.setViewDirectionMode(AstronomerModel.ViewDirectionMode.ROTATE90);
        break;
      case "TELESCOPE":
        model.setViewDirectionMode(AstronomerModel.ViewDirectionMode.TELESCOPE);
        break;
      default:
        model.setViewDirectionMode(AstronomerModel.ViewDirectionMode.STANDARD);
    }
  }

  // Activity for result Ids
  public static final int GOOGLE_PLAY_SERVICES_REQUEST_CODE = 1;
  public static final int GOOGLE_PLAY_SERVICES_REQUEST_LOCATION_PERMISSION_CODE = 2;
  // End Activity for result Ids

  private static final float ROTATION_SPEED = 10;
  private static final String TAG = MiscUtil.getTag(DynamicStarMapActivity.class);

  private ImageButton cancelSearchButton;
  @Inject
  ControllerGroup controller;
  private GestureDetector gestureDetector;
  @Inject
  AstronomerModel model;
  private RendererController rendererController;
  private boolean nightMode = false;
  private boolean searchMode = false;
  private Vector3 searchTarget = CoordinateManipulationsKt.getGeocentricCoords(0, 0);

  @Inject
  SharedPreferences sharedPreferences;
  private GLSurfaceView skyView;
  private PowerManager.WakeLock wakeLock;
  private String searchTargetName;
  @Inject
  LayerManager layerManager;
  // TODO(widdows): Figure out if we should break out the
  // time dialog and time player into separate activities.
  private View timePlayerUI;
  private DynamicStarMapComponent daggerComponent;
  @Inject
  @Named("timetravel")
  Provider<MediaPlayer> timeTravelNoiseProvider;
  @Inject
  @Named("timetravelback")
  Provider<MediaPlayer> timeTravelBackNoiseProvider;
  private MediaPlayer timeTravelNoise;
  private MediaPlayer timeTravelBackNoise;
  @Inject
  Handler handler;
  @Inject
  Analytics analytics;
  @Inject
  GooglePlayServicesChecker playServicesChecker;
  @Inject
  FragmentManager fragmentManager;
  @Inject
  EulaDialogFragment eulaDialogFragmentNoButtons;
  @Inject
  TimeTravelDialogFragment timeTravelDialogFragment;
  @Inject
  HelpDialogFragment helpDialogFragment;
  @Inject
  NoSearchResultsDialogFragment noSearchResultsDialogFragment;
  @Inject
  MultipleSearchResultsDialogFragment multipleSearchResultsDialogFragment;
  @Inject
  NoSensorsDialogFragment noSensorsDialogFragment;
  @Inject
  SensorAccuracyMonitor sensorAccuracyMonitor;
  // A list of runnables to post on the handler when we resume.
  private List<Runnable> onResumeRunnables = new ArrayList<>();

  // We need to maintain references to these objects to keep them from
  // getting gc'd.
  @SuppressWarnings("unused")
  @Inject
  MagneticDeclinationCalculatorSwitcher magneticSwitcher;

  private DragRotateZoomGestureDetector dragZoomRotateDetector;
  @Inject
  Animation flashAnimation;
  private ActivityLightLevelManager activityLightLevelManager;
  private long sessionStartTime;
//*******************************************************

  BluetoothAdapter myBluetoothAdapter;
  Intent btEnablingIntent;
  int requestCodeForeEnable;

  BluetoothDevice[] btArray;
  // SendReceive sendReceive;
  static final int STATE_LISTENING = 1;
  static final int STATE_CONNECT??NG = 2;
  static final int STATE_CONNECTED = 3;
  static final int STATE_CONNECTION_FALLED = 4;
  static final int STATE_MESSAGE_RECEIVED = 5;
  ListView pairedlist;
  private Set<BluetoothDevice> pairedDevice;
  private static final String APP_NAME = "STARMAP";
  private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


  TextView msg_box, status;

  @Override
  public void onCreate(Bundle icicle) {
    Log.d(TAG, "onCreate at " + System.currentTimeMillis());
    super.onCreate(icicle);
    myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();


    btEnablingIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    requestCodeForeEnable = 1;


    daggerComponent = DaggerDynamicStarMapComponent.builder()
            .applicationComponent(getApplicationComponent())
            .dynamicStarMapModule(new DynamicStarMapModule(this)).build();
    daggerComponent.inject(this);

    sharedPreferences.registerOnSharedPreferenceChangeListener(this);

    // Set up full screen mode, hide the system UI etc.
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_FULLSCREEN);


    // TODO(jontayler): upgrade to
    // getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
    // when we reach API level 16.
    // http://developer.android.com/training/system-ui/immersive.html for the right way
    // to do it at API level 19.
    //getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

    // Eventually we should check at the point of use, but this will do for now.  If the
    // user revokes the permission later then odd things may happen.
    playServicesChecker.maybeCheckForGooglePlayServices();

    initializeModelViewController();
    checkForSensorsAndMaybeWarn();

    // Search related
    setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

    ActivityLightLevelChanger activityLightLevelChanger = new ActivityLightLevelChanger(this,
            new NightModeable() {
              @Override
              public void setNightMode(boolean nightMode1) {
                DynamicStarMapActivity.this.rendererController.queueNightVisionMode(nightMode1);
              }
            });
    activityLightLevelManager = new ActivityLightLevelManager(activityLightLevelChanger,
            sharedPreferences);

    PowerManager pm = ContextCompat.getSystemService(this, PowerManager.class);
    if (pm != null) {
      wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
    }

    // Were we started as the result of a search?
    Intent intent = getIntent();
    Log.d(TAG, "Intent received: " + intent);
    if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
      Log.d(TAG, "Started as a result of a search");
      doSearchWithIntent(intent);
    }
    Log.d(TAG, "-onCreate at " + System.currentTimeMillis());
  }


  private boolean checkPermissionn() {
    int currentAPIVersion = Build.VERSION.SDK_INT;
    if (currentAPIVersion >= android.os.Build.VERSION_CODES.M) {
      ArrayList<String> permissionsNotGranted = new ArrayList<>();
      for (String permission : neededPermissions) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
          permissionsNotGranted.add(permission);
        }
      }
      if (permissionsNotGranted.size() > 0) {
        boolean shouldShowAlert = false;
        for (String permission : permissionsNotGranted) {
          shouldShowAlert = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
        }
        if (shouldShowAlert) {
          showPermissionAlert(permissionsNotGranted.toArray(new String[permissionsNotGranted.size()]));
        } else {
          requestPermissions(permissionsNotGranted.toArray(new String[permissionsNotGranted.size()]));
        }
        return false;
      }
    }
    return true;
  }

  private void showPermissionAlert(final String[] permissions) {
    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
    alertBuilder.setCancelable(true);
    alertBuilder.setTitle("message");
    alertBuilder.setMessage("message");
    alertBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        requestPermissions(permissions);
      }
    });
    AlertDialog alert = alertBuilder.create();
    alert.show();
  }

  private void requestPermissions(String[] permissions) {
    ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    switch (requestCode) {
      case REQUEST_CODE:
        for (int result : grantResults) {
          if (result == PackageManager.PERMISSION_DENIED) {
            // Not all permissions granted. Show message to the user.
            return;
          }
        }

        // All permissions are granted. So, do the appropriate work now.
        break;
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }


  private void checkForSensorsAndMaybeWarn() {
    SensorManager sensorManager = ContextCompat.getSystemService(this, SensorManager.class);
    if (sensorManager != null && sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
            && sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) {
      Log.i(TAG, "Minimum sensors present");
      // We want to reset to auto mode on every restart, as users seem to get
      // stuck in manual mode and can't find their way out.
      // TODO(johntaylor): this is a bit of an abuse of the prefs system, but
      // the button we use is wired into the preferences system.  Should probably
      // change this to a use a different mechanism.
      sharedPreferences.edit().putBoolean(ApplicationConstants.AUTO_MODE_PREF_KEY, true).apply();
      setAutoMode(true);
      return;
    }
    // Missing at least one sensor.  Warn the user.
    handler.post(new Runnable() {
      @Override
      public void run() {
        if (!sharedPreferences
                .getBoolean(ApplicationConstants.NO_WARN_ABOUT_MISSING_SENSORS, false)) {
          Log.d(TAG, "showing no sensor dialog");
          noSensorsDialogFragment.show(fragmentManager, "No sensors dialog");
          // First time, force manual mode.
          sharedPreferences.edit().putBoolean(ApplicationConstants.AUTO_MODE_PREF_KEY, false)
                  .apply();
          setAutoMode(false);
        } else {
          Log.d(TAG, "showing no sensor toast");
          Toast.makeText(
                  DynamicStarMapActivity.this, R.string.no_sensor_warning, Toast.LENGTH_LONG).show();
          // Don't force manual mode second time through - leave it up to the user.
        }
      }
    });
  }

  @Override
  protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    // Trigger the initial hide() shortly after the activity has been
    // created, to briefly hint to the user that UI controls
    // are available.
    if (fullscreenControlsManager != null) {
      fullscreenControlsManager.flashTheControls();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main, menu);


    return true;
  }

  @Override
  public void onDestroy() {
    Log.d(TAG, "DynamicStarMap onDestroy");
    super.onDestroy();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case (KeyEvent.KEYCODE_DPAD_LEFT):
        Log.d(TAG, "Key left");
        controller.rotate(-10.0f);
        break;
      case (KeyEvent.KEYCODE_DPAD_RIGHT):
        Log.d(TAG, "Key right");
        controller.rotate(10.0f);
        break;
      case (KeyEvent.KEYCODE_BACK):
        // If we're in search mode when the user presses 'back' the natural
        // thing is to back out of search.
        Log.d(TAG, "In search mode " + searchMode);
        if (searchMode) {
          cancelSearch();
          break;
        }
      default:
        Log.d(TAG, "Key: " + event);
        return super.onKeyDown(keyCode, event);
    }
    return true;
  }

  private static final String BLANK_FRAGMENT_TAG = "FRAGMENT_TAG";

  @SuppressLint("MissingPermission")
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    fullscreenControlsManager.delayHideTheControls();
    Bundle menuEventBundle = new Bundle();
    switch (item.getItemId()) {
      case R.id.menu_item_search:
        Log.d(TAG, "Search");
        menuEventBundle.putString(Analytics.MENU_ITEM_EVENT_VALUE, Analytics.SEARCH_REQUESTED_LABEL);
        onSearchRequested();
        break;
      case R.id.menu_item_settings:
        Log.d(TAG, "Settings");
        menuEventBundle.putString(Analytics.MENU_ITEM_EVENT_VALUE, Analytics.SETTINGS_OPENED_LABEL);
        startActivity(new Intent(this, EditSettingsActivity.class));
        break;
      case R.id.menu_item_help:
        Log.d(TAG, "Help");
        menuEventBundle.putString(Analytics.MENU_ITEM_EVENT_VALUE, Analytics.HELP_OPENED_LABEL);
        helpDialogFragment.show(fragmentManager, "Help Dialog");
        break;
      case R.id.menu_item_dim:
        Log.d(TAG, "Toggling nightmode");
        nightMode = !nightMode;
        sharedPreferences.edit().putString(ActivityLightLevelManager.LIGHT_MODE_KEY,
                nightMode ? "NIGHT" : "DAY").commit();
        menuEventBundle.putString(Analytics.MENU_ITEM_EVENT_VALUE, Analytics.TOGGLED_NIGHT_MODE_LABEL);
        break;
      case R.id.menu_item_time:
        Log.d(TAG, "Starting Time Dialog from menu");
        menuEventBundle.putString(Analytics.MENU_ITEM_EVENT_VALUE, Analytics.TIME_TRAVEL_OPENED_LABEL);
        if (!timePlayerUI.isShown()) {
          Log.d(TAG, "Resetting time in time travel dialog.");
          controller.goTimeTravel(new Date());
        } else {
          Log.d(TAG, "Resuming current time travel dialog.");
        }
        timeTravelDialogFragment.show(fragmentManager, "Time Travel");
        break;
      case R.id.menu_item_gallery:
        Log.d(TAG, "Loading gallery");
        menuEventBundle.putString(Analytics.MENU_ITEM_EVENT_VALUE, Analytics.GALLERY_OPENED_LABEL);
        startActivity(new Intent(this, ImageGalleryActivity.class));
        break;
      case R.id.menu_item_tos:
        Log.d(TAG, "Loading ToS");
        menuEventBundle.putString(Analytics.MENU_ITEM_EVENT_VALUE, Analytics.TOS_OPENED_LABEL);
        eulaDialogFragmentNoButtons.show(fragmentManager, "Eula Dialog No Buttons");
        break;
      case R.id.menu_item_calibrate:
        Log.d(TAG, "Loading Calibration");
        menuEventBundle.putString(Analytics.MENU_ITEM_EVENT_VALUE, Analytics.CALIBRATION_OPENED_LABEL);
        Intent intent = new Intent(this, CompassCalibrationActivity.class);
        intent.putExtra(CompassCalibrationActivity.HIDE_CHECKBOX, true);
        startActivity(intent);
        break;
      case R.id.menu_item_diagnostics:
        Log.d(TAG, "Loading Diagnostics");
        menuEventBundle.putString(Analytics.MENU_ITEM_EVENT_VALUE, Analytics.DIAGNOSTICS_OPENED_LABEL);
        startActivity(new Intent(this, DiagnosticActivity.class));
        break;
      case R.id.ble_setup:
        success_blue();
        break;
      case R.id.ble:
        //   ServerClass serverClass = new ServerClass();
        //   serverClass.start();
        // Intent intent3 = new Intent(this, bluetooth.class);
        //   startActivity(intent3);
        // show_dialog();
        listDevice();
        break;
      default:
        Log.e(TAG, "Unwired-up menu item");
        return false;
    }
    analytics.trackEvent(Analytics.MENU_ITEM_EVENT, menuEventBundle);
    return true;


  }

  void success_blue() {
    if (myBluetoothAdapter == null) {
      Toast.makeText(getApplicationContext(), "Bluetooth does not support", Toast.LENGTH_LONG).show();


    } else {

      if (!myBluetoothAdapter.isEnabled()) {

        startActivityForResult(btEnablingIntent, requestCodeForeEnable);

      }

    }
  }

  @SuppressLint("MissingPermission")
  void show_dialog() {
    ListView listvieww = new ListView(this);
    Set<BluetoothDevice> bt = myBluetoothAdapter.getBondedDevices();
    myBluetoothAdapter.cancelDiscovery();
    String[] strings = new String[bt.size()];
    btArray = new BluetoothDevice[bt.size()];
    int index = 0;

    if (bt.size() > 0) {
      for (BluetoothDevice device : bt) {
        btArray[index] = device;
        strings[index] = device.getName();
        Log.e("connect", "device" + device.getName());
        index++;
      }

    }
//*******************************
    ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(DynamicStarMapActivity.this, android.R.layout.select_dialog_singlechoice, strings);
    listvieww.setAdapter(arrayAdapter);
    AlertDialog.Builder builder = new AlertDialog.Builder(this);

    builder.setTitle("Select Device");

    builder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {

      }
    });


    builder.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        //  String strName = arrayAdapter.getItem(which);
        //  builder.setMessage(strName);
        builder.setView(listvieww);
        address = btArray[which].getAddress().substring(btArray[which].getAddress().length() - 17);



      /*  try {
          //cihaz??n id'si
         ClientClass clientClass=new ClientClass(btArray[which]);

          Log.e("connect"," cihaz id:"+ btArray[which]);
          //clientClass.start();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }*/


        Toast.makeText(DynamicStarMapActivity.this, "Device: " + address, Toast.LENGTH_SHORT).show();


      }
    });

    AlertDialog alert = builder.create();

    alert.show();
  }

  private void listDevice() {

    pairedDevice = myBluetoothAdapter.getBondedDevices();


    ArrayList list = new ArrayList();
    if (pairedDevice.size() > 0) {
      for (BluetoothDevice bt : pairedDevice) {
        list.add(bt.getName() + "\n" + bt.getAddress());


      }


    } else {
      Toast.makeText(this, "E??le??mi?? cihaz yok", Toast.LENGTH_SHORT).show();
    }

    final ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, list);
    pairedlist.setAdapter(adapter);
    pairedlist.setOnItemClickListener(cihazSec);


  }

  public AdapterView.OnItemClickListener cihazSec = new AdapterView.OnItemClickListener() {

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {


      String info = ((TextView) view).getText().toString();
      address = info.substring(info.length() - 17);
      //Yeni bir Activity baslatman icin bir intent tan??ml??yoruz
      Toast.makeText(DynamicStarMapActivity.this, "adress" + address, Toast.LENGTH_SHORT).show();
      new DynamicStarMapActivity.BTbaglan().execute();
    }
  };

  private void Disconnect() {
    if (btSocket != null) {
      try {
        btSocket.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
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
      progress = ProgressDialog.show(DynamicStarMapActivity.this, "Ba??lan??yor..", "L??tfen Bekleyin");
    }

    @Override
    protected Void doInBackground(Void... voids) {
      try {
        if (btSocket == null || !isBtConnected) {
          myBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
          BluetoothDevice cihaz = myBluetoothAdapter.getRemoteDevice(address);


          if (ActivityCompat.checkSelfPermission(DynamicStarMapActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.

          }
          btSocket = cihaz.createRfcommSocketToServiceRecord(MY_UUID);
          BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
          btSocket.connect();



        }
      }
      catch (IOException e){
        ConnectSuccess =false;

      }



      return null;
    }

    @Override
    protected void onPostExecute(Void unused) {
      super.onPostExecute(unused);
      if(!ConnectSuccess){
        Toast.makeText(DynamicStarMapActivity.this, "Ba??lant?? hatas??", Toast.LENGTH_SHORT).show();

      }else{
        Toast.makeText(DynamicStarMapActivity.this, "Ba??lant?? ba??ar??l??", Toast.LENGTH_SHORT).show();
        isBtConnected = true;
      }
      progress.dismiss();
    }
  }


















/*
  Handler handler1 = new Handler(msg -> {
    switch (msg.what) {
      case STATE_LISTENING:
        Toast.makeText(getApplicationContext(), "Listening", Toast.LENGTH_LONG).show();
        break;
      case STATE_CONNECT??NG:
        Toast.makeText(getApplicationContext(), "connecting", Toast.LENGTH_LONG).show();
        break;
      case STATE_CONNECTED:
        Toast.makeText(getApplicationContext(), "connected", Toast.LENGTH_LONG).show();
        break;
      case STATE_CONNECTION_FALLED:
        Toast.makeText(getApplicationContext(), "connection failed", Toast.LENGTH_LONG).show();
        break;
      case STATE_MESSAGE_RECEIVED:
       byte[] readBuff = (byte[]) msg.obj;
       String tempMsg = new String(readBuff,0,msg.arg1);
        Toast.makeText(getApplicationContext(), "Listen", Toast.LENGTH_LONG).show();

        //mesaj
        break;
    }

    return false;
  });

  private class ServerClass extends Thread
  {
    private BluetoothServerSocket serverSocket;

    @SuppressLint("MissingPermission")
    public ServerClass(){
      BluetoothServerSocket  tmp = null;
      try {
        tmp =myBluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME,MY_UUID);
      } catch (IOException e) {
        e.printStackTrace();
      }
      serverSocket = tmp;
    }

    public void run()
    {
      BluetoothSocket socket=null;

      while (true)
      {
        try {
          Message message=Message.obtain();
          message.what=STATE_CONNECTING;
          handler1.sendMessage(message);

          socket=serverSocket.accept();
        } catch (IOException e) {
          e.printStackTrace();
          Message message=Message.obtain();
          message.what=STATE_CONNECTION_FALLED;
          handler1.sendMessage(message);
        }

        if(socket != null)
        {
          Message message=Message.obtain();
          message.what=STATE_CONNECTED;
          handler1.sendMessage(message);

          sendReceive=new SendReceive(socket);
          sendReceive.start();
          try {
            serverSocket.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          break;
        }
      }
    }
    public void cancel() {
      try {
        serverSocket.close();
      } catch (IOException e) {
        Log.e(TAG, "Could not close the connect socket", e);
      }
    }
  }

  //Al??c??
  private class ClientClass extends Thread {

    private BluetoothDevice device;
    private BluetoothSocket socket;

    public ClientClass(BluetoothDevice device1) throws IOException {
      device = device1;
      BluetoothSocket tmp = null;

     try {
        tmp =device.createRfcommSocketToServiceRecord(MY_UUID);

      } catch (IOException e) {
        e.printStackTrace();
      }

socket = tmp;
    }

    public void run() {
      myBluetoothAdapter.cancelDiscovery();
      try {

        Message message=Message.obtain();
        message.what=STATE_CONNECTED;
        handler1.sendMessage(message);
socket.connect();
        sendReceive=new SendReceive(socket);
        sendReceive.start();


}catch (IOException e){
  e.printStackTrace();
        try {
          socket.close();
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
        Message message = Message.obtain();
        message.what =STATE_CONNECTION_FALLED;
        handler1.sendMessage(message);
}


    }
    public void cancel() {
      try {
        socket.close();
      } catch (IOException e) {
        Log.e(TAG, "Could not close the client socket", e);
      }
    }



}


  private class SendReceive extends Thread
  {
    private final BluetoothSocket bluetoothSocket;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private byte[] buffer;

    public SendReceive (BluetoothSocket socket)
    {
      bluetoothSocket=socket;
      InputStream tempIn = null;
      OutputStream tempOut = null;

      try {
        tempIn=bluetoothSocket.getInputStream();
      } catch (IOException e) {

        e.printStackTrace();
      }
      try {
        tempOut=bluetoothSocket.getOutputStream();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }


      inputStream = tempIn;
      outputStream = tempOut;
    }

    public void run()
    {
       buffer=new byte[1024];
      int bytes;

      while (true)
      {
        try {
          bytes = inputStream.read(buffer);
          handler1.obtainMessage(STATE_MESSAGE_RECEIVED,bytes,-1,buffer).sendToTarget();
        } catch (IOException e) {
          e.printStackTrace();
          break;
        }
      }
    }

    public void write(byte[] bytes)
    {
      try {
        outputStream.write(bytes);
        handler.obtainMessage(
                STATE_MESSAGE_RECEIVED, -1, -1, buffer).sendToTarget();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    public void cancel() {
      try {
        bluetoothSocket.close();
      } catch (IOException e) {
        Log.e(TAG, "Could not close the connect socket", e);
      }
    }
  }

 */






  @Override
  public void onStart() {
    super.onStart();
    sessionStartTime = System.currentTimeMillis();
  }

  private enum SessionBucketLength {
    LESS_THAN_TEN_SECS(10), TEN_SECS_TO_THIRTY_SECS(30),
    THIRTY_SECS_TO_ONE_MIN(60), ONE_MIN_TO_FIVE_MINS(300),
    MORE_THAN_FIVE_MINS(Integer.MAX_VALUE);
    private int seconds;
    SessionBucketLength(int seconds) {
      this.seconds = seconds;
    }
  }

  private SessionBucketLength getSessionLengthBucket(int sessionLengthSeconds) {
    for (SessionBucketLength bucket : SessionBucketLength.values()) {
      if (sessionLengthSeconds < bucket.seconds) {
        return bucket;
      }
    }
    Log.e(TAG, "Programming error - should not get here");
    return SessionBucketLength.MORE_THAN_FIVE_MINS;
  }

  @Override
  public void onStop() {
    super.onStop();
    // Define a session as being the time between the main activity being in
    // the foreground and pushed back.  Note that this will mean that sessions
    // do get interrupted by (e.g.) loading preference or help screens.
    int sessionLengthSeconds = (int) ((
        System.currentTimeMillis() - sessionStartTime) / 1000);
    SessionBucketLength bucket = getSessionLengthBucket(sessionLengthSeconds);
    Bundle b = new Bundle();
    // Let's see how well Analytics buckets things and log the raw number
    b.putInt(Analytics.SESSION_LENGTH_TIME_VALUE, sessionLengthSeconds);
    analytics.trackEvent(Analytics.SESSION_LENGTH_EVENT, b);
  }

  @Override
  public void onResume() {
    Log.d(TAG, "onResume at " + System.currentTimeMillis());
    super.onResume();
    Log.i(TAG, "Resuming");
    timeTravelNoise = timeTravelNoiseProvider.get();
    timeTravelBackNoise = timeTravelBackNoiseProvider.get();

    wakeLock.acquire();
    Log.i(TAG, "Starting view");
    skyView.onResume();
    Log.i(TAG, "Starting controller");
    controller.start();
    activityLightLevelManager.onResume();
    if (controller.isAutoMode()) {
      sensorAccuracyMonitor.start();
    }
    for (Runnable runnable : onResumeRunnables) {
      handler.post(runnable);
    }
    Log.d(TAG, "-onResume at " + System.currentTimeMillis());
  }

  public void setTimeTravelMode(Date newTime) {
    SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy.MM.dd G  HH:mm:ss z");
    Toast.makeText(this,
                   String.format(getString(R.string.time_travel_start_message_alt),
                                 dateFormatter.format(newTime)),
                   Toast.LENGTH_LONG).show();
    if (sharedPreferences.getBoolean(ApplicationConstants.SOUND_EFFECTS, true)) {
      try {
        timeTravelNoise.start();
      } catch (IllegalStateException | NullPointerException e) {
        Log.e(TAG, "Exception trying to play time travel sound", e);
        // It's not the end of the world - carry on.
      }
    }

    Log.d(TAG, "Showing TimePlayer UI.");
    timePlayerUI.setVisibility(View.VISIBLE);
    timePlayerUI.requestFocus();
    flashTheScreen();
    controller.goTimeTravel(newTime);
  }

  public void setNormalTimeModel() {
    if (sharedPreferences.getBoolean(ApplicationConstants.SOUND_EFFECTS, true)) {
      try {
        timeTravelBackNoise.start();
      } catch (IllegalStateException | NullPointerException e) {
        Log.e(TAG, "Exception trying to play return time travel sound", e);
        // It's not the end of the world - carry on.
      }
    }
    flashTheScreen();
    controller.useRealTime();
    Toast.makeText(this,
        R.string.time_travel_close_message,
                   Toast.LENGTH_SHORT).show();
    Log.d(TAG, "Leaving Time Travel mode.");
    timePlayerUI.setVisibility(View.GONE);
  }

  private void flashTheScreen() {
    final View view = findViewById(R.id.view_mask);
    // We don't need to set it invisible again - the end of the
    // animation will see to that.
    // TODO(johntaylor): check if setting it to GONE will bring
    // performance benefits.
    view.setVisibility(View.VISIBLE);
    view.startAnimation(flashAnimation);
  }

  @Override
  public void onPause() {
    Log.d(TAG, "DynamicStarMap onPause");
    super.onPause();
    sensorAccuracyMonitor.stop();
    if (timeTravelNoise != null) {
      timeTravelNoise.release();
      timeTravelNoise = null;
    }
    if (timeTravelBackNoise != null) {
      timeTravelBackNoise.release();
      timeTravelBackNoise = null;
    }
    for (Runnable runnable : onResumeRunnables) {
      handler.removeCallbacks(runnable);
    }
    activityLightLevelManager.onPause();
    controller.stop();
    skyView.onPause();
    wakeLock.release();
    // Debug.stopMethodTracing();
    Log.d(TAG, "DynamicStarMap -onPause");
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    Log.d(TAG, "Preferences changed: key=" + key);
    if (key == null) {
      return;
    }
    switch (key) {
      case ApplicationConstants.AUTO_MODE_PREF_KEY:
        boolean autoMode = sharedPreferences.getBoolean(key, true);
        Log.d(TAG, "Automode is set to " + autoMode);
        if (!autoMode) {
          Log.d(TAG, "Switching to manual control");
          Toast.makeText(DynamicStarMapActivity.this, R.string.set_manual, Toast.LENGTH_SHORT).show();
        } else {
          Log.d(TAG, "Switching to sensor control");
          Toast.makeText(DynamicStarMapActivity.this, R.string.set_auto, Toast.LENGTH_SHORT).show();
        }
        setAutoMode(autoMode);
        break;
      case ApplicationConstants.VIEW_MODE_PREFKEY:
        updateViewDirectionMode(model, sharedPreferences);
      default:
        return;
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    // Log.d(TAG, "Touch event " + event);
    // Either of the following detectors can absorb the event, but one
    // must not hide it from the other
    boolean eventAbsorbed = false;
    if (gestureDetector.onTouchEvent(event)) {
      eventAbsorbed = true;
    }
    if (dragZoomRotateDetector.onTouchEvent(event)) {
      eventAbsorbed = true;
    }
    return eventAbsorbed;
  }

  @Override
  public boolean onTrackballEvent(MotionEvent event) {
    // Log.d(TAG, "Trackball motion " + event);
    controller.rotate(event.getX() * ROTATION_SPEED);
    return true;
  }

  private void doSearchWithIntent(Intent searchIntent) {
    // If we're already in search mode, cancel it.
    if (searchMode) {
      cancelSearch();
    }
    Log.d(TAG, "Performing Search");
    final String queryString = searchIntent.getStringExtra(SearchManager.QUERY);
    searchMode = true;
    Log.d(TAG, "Query string " + queryString);
    List<SearchResult> results = layerManager.searchByObjectName(queryString);
    Bundle b = new Bundle();
    b.putString(AnalyticsInterface.SEARCH_TERM, queryString);
    b.putBoolean(AnalyticsInterface.SEARCH_SUCCESS, results.size() > 0);
    analytics.trackEvent(AnalyticsInterface.SEARCH_EVENT, b);
    if (results.isEmpty()) {
      Log.d(TAG, "No results returned");
      noSearchResultsDialogFragment.show(fragmentManager, "No Search Results");
    } else if (results.size() > 1) {
      Log.d(TAG, "Multiple results returned");
      showUserChooseResultDialog(results);
    } else {
      Log.d(TAG, "One result returned.");
      final SearchResult result = results.get(0);
      activateSearchTarget(result.coords(), result.getCapitalizedName());
    }
  }

  private void showUserChooseResultDialog(List<SearchResult> results) {
    multipleSearchResultsDialogFragment.clearResults();
    for (SearchResult result : results) {
      multipleSearchResultsDialogFragment.add(result);
    }
    multipleSearchResultsDialogFragment.show(fragmentManager, "Multiple Search Results");
  }

  private void initializeModelViewController() {
    Log.i(TAG, "Initializing Model, View and Controller @ " + System.currentTimeMillis());
    setContentView(R.layout.skyrenderer);
    skyView = (GLSurfaceView) findViewById(R.id.skyrenderer_view);



    //skyView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);


    // We don't want a depth buffer.
   skyView.setEGLConfigChooser(false);
    SkyRenderer renderer = new SkyRenderer(getResources());
    skyView.setRenderer(renderer);

    rendererController = new RendererController(renderer, skyView);
    // The renderer will now call back every frame to get model updates.
    rendererController.addUpdateClosure(
        new RendererModelUpdateClosure(model, rendererController, sharedPreferences));

    Log.i(TAG, "Setting layers @ " + System.currentTimeMillis());
    layerManager.registerWithRenderer(rendererController);
    Log.i(TAG, "Set up controllers @ " + System.currentTimeMillis());
    controller.setModel(model);
    wireUpScreenControls(); // TODO(johntaylor) move these?
    wireUpTimePlayer();  // TODO(widdows) move these?
  }













  private void setAutoMode(boolean auto) {
    Bundle b = new Bundle();
    b.putString(Analytics.MENU_ITEM_EVENT_VALUE, Analytics.TOGGLED_MANUAL_MODE_LABEL);
    controller.setAutoMode(auto);
    if (auto) {
      sensorAccuracyMonitor.start();
    } else {
      sensorAccuracyMonitor.stop();
    }
  }

  private void wireUpScreenControls() {
    cancelSearchButton = (ImageButton) findViewById(R.id.cancel_search_button);
    // TODO(johntaylor): move to set this in the XML once we don't support 1.5
    cancelSearchButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        cancelSearch();
      }
    });

    pairedlist = findViewById(R.id.listview2);

    ButtonLayerView providerButtons = (ButtonLayerView) findViewById(R.id.layer_buttons_control);

    int numChildren = providerButtons.getChildCount();
    List<View> buttonViews = new ArrayList<>();
    for (int i = 0; i < numChildren; ++i) {
      ImageButton button = (ImageButton) providerButtons.getChildAt(i);
      buttonViews.add(button);
    }
    buttonViews.add(findViewById(R.id.manual_auto_toggle));
    ButtonLayerView manualButtonLayer = (ButtonLayerView) findViewById(
        R.id.layer_manual_auto_toggle);

    fullscreenControlsManager = new FullscreenControlsManager(
        this,
        findViewById(R.id.main_sky_view),
        Lists.<View>asList(manualButtonLayer, providerButtons),
        buttonViews);

    MapMover mapMover = new MapMover(model, controller, this);

    gestureDetector = new GestureDetector(this, new GestureInterpreter(
        fullscreenControlsManager, mapMover));
    dragZoomRotateDetector = new DragRotateZoomGestureDetector(mapMover);
  }

  private void cancelSearch() {
    View searchControlBar = findViewById(R.id.search_control_bar);
    searchControlBar.setVisibility(View.INVISIBLE);
    rendererController.queueDisableSearchOverlay();
    searchMode = false;
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.d(TAG, "New Intent received " + intent);
    if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
      doSearchWithIntent(intent);
    }
  }

  @Override
  protected void onRestoreInstanceState(Bundle icicle) {
    Log.d(TAG, "DynamicStarMap onRestoreInstanceState");
    super.onRestoreInstanceState(icicle);
    if (icicle == null) return;
    searchMode = icicle.getBoolean(ApplicationConstants.BUNDLE_SEARCH_MODE);
    float x = icicle.getFloat(ApplicationConstants.BUNDLE_X_TARGET);
    float y = icicle.getFloat(ApplicationConstants.BUNDLE_Y_TARGET);
    float z = icicle.getFloat(ApplicationConstants.BUNDLE_Z_TARGET);
    searchTarget = new Vector3(x, y, z);
    searchTargetName = icicle.getString(ApplicationConstants.BUNDLE_TARGET_NAME);
    if (searchMode) {



      Log.d(TAG, "Searching for target " + searchTargetName + " at target=" + searchTarget);
      rendererController.queueEnableSearchOverlay(searchTarget, searchTargetName);
      cancelSearchButton.setVisibility(View.VISIBLE);
    }
    nightMode = icicle.getBoolean(ApplicationConstants.BUNDLE_NIGHT_MODE, false);
  }

  @Override
  protected void onSaveInstanceState(Bundle icicle) {
    Log.d(TAG, "DynamicStarMap onSaveInstanceState");
    icicle.putBoolean(ApplicationConstants.BUNDLE_SEARCH_MODE, searchMode);
    icicle.putFloat(ApplicationConstants.BUNDLE_X_TARGET, searchTarget.x);
    icicle.putFloat(ApplicationConstants.BUNDLE_Y_TARGET, searchTarget.y);
    icicle.putFloat(ApplicationConstants.BUNDLE_Z_TARGET, searchTarget.z);
    icicle.putString(ApplicationConstants.BUNDLE_TARGET_NAME, searchTargetName);
    icicle.putBoolean(ApplicationConstants.BUNDLE_NIGHT_MODE, nightMode);
    super.onSaveInstanceState(icicle);
  }

  public void activateSearchTarget(Vector3 target, final String searchTerm) {
    Log.d(TAG, "Item " + searchTerm + " selected");
    // Store these for later.
    searchTarget = target;
    searchTargetName = searchTerm;



    Log.d(TAG, "Searching for target=" + target.x);

     sendSignal(String.valueOf(target.x),String.valueOf(target.y));
    //************************
    String string= String.valueOf(target.x);
    Log.e(TAG,"cevap" + string.getBytes());
  //  sendReceive.write(string.getBytes());
    //**************************




    rendererController.queueViewerUpDirection(model.getZenith().copyForJ());
    rendererController.queueEnableSearchOverlay(target.copyForJ(), searchTerm);
    boolean autoMode = sharedPreferences.getBoolean(ApplicationConstants.AUTO_MODE_PREF_KEY, true);
    if (!autoMode) {
      controller.teleport(target);
    }

    TextView searchPromptText = (TextView) findViewById(R.id.search_status_label);
    searchPromptText.setText(
        String.format("%s %s", getString(R.string.search_target_looking_message), searchTerm));
    View searchControlBar = findViewById(R.id.search_control_bar);
    searchControlBar.setVisibility(View.VISIBLE);
  }

  /**
   * Creates and wire up all time player controls.
   */

  private void sendSignal ( String number,String number2 ) {
    if ( btSocket != null ) {
      try {
        btSocket.getOutputStream().write(number.toString().getBytes());
        btSocket.getOutputStream().write(number2.toString().getBytes());
        Toast.makeText(this, "Ba??ar??l?? data ", Toast.LENGTH_SHORT).show();
      } catch (IOException e) {
        Toast.makeText(this, "Ba??ar??s??z data ", Toast.LENGTH_SHORT).show();
      }
    }
  }
  private void wireUpTimePlayer() {
    Log.d(TAG, "Initializing TimePlayer UI.");
    timePlayerUI = findViewById(R.id.time_player_view);
    ImageButton timePlayerCancelButton = (ImageButton) findViewById(R.id.time_player_close);
    ImageButton timePlayerBackwardsButton = (ImageButton) findViewById(
        R.id.time_player_play_backwards);
    ImageButton timePlayerStopButton = (ImageButton) findViewById(R.id.time_player_play_stop);
    ImageButton timePlayerForwardsButton = (ImageButton) findViewById(
        R.id.time_player_play_forwards);
    final TextView timeTravelSpeedLabel = (TextView) findViewById(R.id.time_travel_speed_label);

    timePlayerCancelButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Log.d(TAG, "Heard time player close click.");
        setNormalTimeModel();
      }
    });
    timePlayerBackwardsButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Log.d(TAG, "Heard time player play backwards click.");
        controller.decelerateTimeTravel();
        timeTravelSpeedLabel.setText(controller.getCurrentSpeedTag());
      }
    });
    timePlayerStopButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Log.d(TAG, "Heard time player play stop click.");
        controller.pauseTime();
        timeTravelSpeedLabel.setText(controller.getCurrentSpeedTag());
      }
    });
    timePlayerForwardsButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Log.d(TAG, "Heard time player play forwards click.");
        controller.accelerateTimeTravel();
        timeTravelSpeedLabel.setText(controller.getCurrentSpeedTag());
      }
    });

    Runnable displayUpdater = new Runnable() {
      private TextView timeTravelTimeReadout = (TextView) findViewById(
          R.id.time_travel_time_readout);
      private TextView timeTravelStatusLabel = (TextView) findViewById(
          R.id.time_travel_status_label);
      private TextView timeTravelSpeedLabel = (TextView) findViewById(
          R.id.time_travel_speed_label);
      private final SimpleDateFormat dateFormatter = new SimpleDateFormat(
          "yyyy.MM.dd G  HH:mm:ss z");
      private Date date = new Date();
      @Override
      public void run() {
        long time = model.getTimeMillis();
        date.setTime(time);
        timeTravelTimeReadout.setText(dateFormatter.format(date));
        if (time > System.currentTimeMillis()) {
          timeTravelStatusLabel.setText(R.string.time_travel_label_future);
        } else {
          timeTravelStatusLabel.setText(R.string.time_travel_label_past);
        }
        timeTravelSpeedLabel.setText(controller.getCurrentSpeedTag());
        handler.postDelayed(this, TIME_DISPLAY_DELAY_MILLIS);
      }
    };
    onResumeRunnables.add(displayUpdater);
  }

  public AstronomerModel getModel() {
    return model;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
  /*  if (requestCode == GOOGLE_PLAY_SERVICES_REQUEST_CODE) {
      playServicesChecker.runAfterDialog();
      return;
    }
    Log.w(TAG, "Unhandled activity result");
*/
    if(resultCode == requestCodeForeEnable){
      if(resultCode == RESULT_OK){
        Toast.makeText(this,"Bluetooth is enable",Toast.LENGTH_LONG).show();


      }else if(resultCode == RESULT_CANCELED){
        Toast.makeText(this,"Bluetooth Enabling Cancelled",Toast.LENGTH_LONG).show();

      }
    }




  }

 /* @Override
  public void onRequestPermissionsResult(int requestCode,
                                         String[] permissions,
                                         int[] grantResults) {
    if (requestCode == GOOGLE_PLAY_SERVICES_REQUEST_LOCATION_PERMISSION_CODE) {
      playServicesChecker.runAfterPermissionsCheck(requestCode, permissions, grantResults);
      return;
    }
    Log.w(TAG, "Unhandled request permissions result");
  }*/










}
