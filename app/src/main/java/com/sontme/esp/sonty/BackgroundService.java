package com.sontme.esp.sonty;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.github.anrwatchdog.ANRError;
import com.github.anrwatchdog.ANRWatchDog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BackgroundService extends Service {

    public static String INSERT_URL = "https://sont.sytes.net/wifilocator/wifi_insert.php";
    public WifiManager wifiManager;
    public LocationManager locationManager;
    public LocationListener locationListener;
    public static List<ScanResult> scanresult;

    public static Set<String> unique = new LinkedHashSet<>();

    //region Uniqueness check
    /*
    public static HashSet<String> unique2 = new HashSet<>();
    public static LinkedHashSet<String> unique3 = new LinkedHashSet<>();
    public static TreeSet<String> unique4 = new TreeSet<>();
    */
    //endregion

    public static int count = 0;
    public static String service_icon_lastColor = "red";
    public static String started_time;

    public static int recorded; // new
    public static int updated; // regi_old, regi_str
    public static int not_touched; // not_recorded
    public static int retry_count; // http retry;

    /*
    public static WindowManager headoverlay_mWindowManager;
    public static View headoverlay_mHeadView;

    static Button headoverlay_lay_btn1;
    static Button headoverlay_lay_btn2;
    static TextView headoverlay_lay_txt1;
    static TextView headoverlay_lay_txt2;
    static ListView bllistview;
    static ArrayList<String> listItems = new ArrayList<String>();
    static ArrayAdapter<String> adapter;
    //boolean reallyexit = false;
    //public static Map<Location, BluetoothDevice> bl_devices = new HashMap<Location, BluetoothDevice>();
    */

    static SontHelper.TimeElapsedUtil teu = new SontHelper.TimeElapsedUtil();
    static SontHelper.TimeElapsedUtil elapsed_since_start = new SontHelper.TimeElapsedUtil();
    static int started_at_battery;
    static String gnss_count;


    public static Location CURRENT_LOCATION;


    public Handler inactivity_handler;
    public Runnable inactivity_runnable;

    public static SontHelper.BluetoothThings bl = new SontHelper.BluetoothThings();

    @Override
    public void onCreate() {
        try {
            super.onCreate();

            elapsed_since_start.setStartTime(System.currentTimeMillis());
            exitOnTooLowBattery();
            started_at_battery = SontHelper.getBatteryLevel(getApplicationContext());
            SontHelper.createNotifGroup(getApplicationContext(), "sonty", "sonty");
            SontHelper.createNotificationChannel(getApplicationContext(), "sonty", "sonty");

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            Date resultdate = new Date(System.currentTimeMillis());
            started_time = sdf.format(resultdate);

            showOngoing();

            Thread vibroThread = new Thread() {
                @Override
                public void run() {
                    SontHelper.Vibratoor.init(getApplicationContext());
                    SontHelper.Vibratoor.makePattern().beat(500).rest(80).beat(100).playPattern(1);
                }
            };
            vibroThread.start();
            //region HOME CHECK
            /*
            if (SontHelper.getSSID(getApplicationContext()).contains("UPCAEDB2C3")) {
                SontHelper.blinkLed(getApplicationContext(), 5);
            }
            */
            //endregion
            registerReceiver(wifiReceiver, new IntentFilter(
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
            ));

            wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    try {
                        resetInactivityHandler();
                        CURRENT_LOCATION = location;
                        Log.d("WIFI_LOCATION", "Location Changed: " + location.toString());
                        count++;
                        String notificationText;
                        String title = "Sonty Service";
                        /* !!! IMPORTANT !! LOGIC !!! */
                        if (wifiManager.getScanResults() != null) { // scanResult
                            if (wifiManager.getScanResults().isEmpty() != true) {

                                String addr = SontHelper.locationToStringAddress(getApplicationContext(), location);

                                if (addr != null) {
                                    notificationText = "Count: " + count + " @ " + addr;
                                } else {
                                    notificationText = "Count: " + count + " @ Unknown";
                                }

                                for (ScanResult result : wifiManager.getScanResults()) {
                                    unique.add(result.BSSID);

                                    String enc = "notavailable";
                                    if (!result.capabilities.contains("WEP") ||
                                            !result.capabilities.contains("WPA")) {
                                        enc = "NONE";
                                    } else if (result.capabilities.contains("WEP")) {
                                        enc = "WEP";
                                    } else if (result.capabilities.contains("WPA")) {
                                        enc = "WPA";
                                    } else if (result.capabilities.contains("WPA2")) {
                                        enc = "WPA2";
                                    }

                                    if ((result.BSSID != null) && (result.BSSID.length() >= 1)) {
                                        String reqBody = "?id=0&ssid=" +
                                                result.SSID + "&add=service" + "&bssid=" +
                                                result.BSSID + "&source=" + "sonty" + "_v" + "1" + "&enc=" +
                                                enc + "&rssi=" +
                                                convertDBM(result.level) + "&long=" +
                                                location.getLongitude() + "&lat=" +
                                                location.getLatitude() + "&channel=" +
                                                result.frequency;
                                        Thread saveHttpThread = new Thread() {
                                            @Override
                                            public void run() {
                                                try {
                                                    SAVE_HTTP save = new SAVE_HTTP(INSERT_URL + reqBody, getApplicationContext());
                                                    save.execute();
                                                } catch (Exception e) {
                                                    Log.d("HTTP_", "ERROR: " + e.getMessage());
                                                    //headoverlay_lay_txt1.setText("HTTP ERROR");
                                                    e.printStackTrace();
                                                }
                                            }

                                        };
                                        saveHttpThread.start();

                                        //Log.d("ELAPSED_TIME_SINCE_WIFI_SCAN", "TIME: " + teu.getElapsed());
                                    }
                                }
                                scanresult.clear();
                            } else {
                                notificationText = "NO WIFI NEARBY";
                            }
                        } else {
                            notificationText = "WIFI SCANRESULT ERROR: NULL";
                        }

                        updateCurrent(title, notificationText);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                    Log.d("LOCATION_PROVIDER", "CHANGED: " + provider + "_" + status + "_" + extras.toString());
                    Toast.makeText(getApplicationContext(), "Provider changed: " + provider + " Status: " + status, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onProviderEnabled(String provider) {
                    Toast.makeText(getApplicationContext(), "Provider enabled", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onProviderDisabled(String provider) {
                    //locationManager.removeUpdates(locationListener);
                    Toast.makeText(getApplicationContext(), "Provider disabled", Toast.LENGTH_SHORT).show();
                }
            };

            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "PERMISSION ERROR", Toast.LENGTH_LONG).show();
            }
            locationManager.requestSingleUpdate(
                    LocationManager.GPS_PROVIDER,
                    locationListener,
                    null
            );
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3,
                    5,
                    locationListener
            );
            locationManager.addGpsStatusListener(new GpsStatus.Listener() {
                @Override
                public void onGpsStatusChanged(int event) {
                    if (event == GpsStatus.GPS_EVENT_STOPPED) {
                        updateCurrent("Sonty Service", "[GPSS] Signal Lost");
                        //Toast.makeText(getApplicationContext(),"GPS SIGNAL LOST",Toast.LENGTH_LONG).show();
                    }
                    if (event == GpsStatus.GPS_EVENT_STARTED) {
                        //Waiting for
                        updateCurrent("Sonty Service", "[GPS] Connecting..");
                    }
                    if (event == GpsStatus.GPS_EVENT_FIRST_FIX) {
                        updateCurrent("Sonty Service", "[GPS] Connected");
                    }
                }
            });
            locationManager.registerGnssStatusCallback(new GnssStatus.Callback() {
                @Override
                public void onFirstFix(int ttffMillis) {
                    super.onFirstFix(ttffMillis);
                }

                @Override
                public void onSatelliteStatusChanged(GnssStatus status) {
                    gnss_count = String.valueOf(status.getSatelliteCount());
                    for (int i = 0; i < status.getSatelliteCount(); i++) {
                        //int type = status.getConstellationType(i);
                        //satellite_types.put(i, convert(type));
                        //Log.d("GNSS_", "TYPE: " + i + " -> " + convert(type));
                    }
                    super.onSatelliteStatusChanged(status);
                }

                public String convert(int i) {
                    switch (i) {
                        case 0:
                            return "Unknown";
                        case 1:
                            return "GPS";
                        case 2:
                            return "SBAS";
                        case 3:
                            return "GLONASS";
                        case 4:
                            return "QZSS";
                        case 5:
                            return "BEIDOU";
                        case 6:
                            return "GALILEO";
                        case 7:
                            return "IRNSS";
                        default:
                            return "Unknown";
                    }
                }
            });

            ANRWatchDog watchdog = new ANRWatchDog();
            watchdog.setName("service_main_thread");
            watchdog.start();
            watchdog.setANRListener(new ANRWatchDog.ANRListener() {
                @Override
                public void onAppNotResponding(ANRError error) {
                    Thread anrThread = new Thread() {
                        @Override
                        public void run() {
                            SontHelper.sendEmail(
                                    "[SONTY][ANR] ERROR",
                                    "SERVICE THREAD NOT RESPONDING\n\n" +
                                            error.toString() + "\n\n" +
                                            error.getCause().getStackTrace()[0].toString() + "\n\n" +
                                            error.getStackTrace()[0].toString(),
                                    "sont16@gmail.com",
                                    "egedzsolt@protonmail.com"
                            );
                        }
                    };
                    anrThread.start();
                }
            });

            initHeadOverlay(getApplicationContext());

            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
                    Log.d("!!!!!!!!_UNHANDLED EXCEPTION CAUGHT_!!!!!!!!_1", "Error: " + paramThread.toString());
                    Log.d("!!!!!!!!_UNHANDLED EXCEPTION CAUGHT_!!!!!!!!_2", "Error: " + paramThrowable.toString());
                    Toast.makeText(getApplicationContext(), "UNHANDLED EX: " + paramThread.getName() + " _ " + paramThrowable.getMessage(), Toast.LENGTH_LONG).show();
                    Thread emailThread = new Thread() {
                        @Override
                        public void run() {
                            SontHelper.sendEmail(
                                    "[SONTY] UNHANDLED ERROR",
                                    "Error: " + paramThread.getName() + " _ " +
                                            paramThrowable.getMessage() + "\n" +
                                            paramThread.toString() + "\n" +
                                            paramThrowable.toString() + "\n Line number: " +
                                            paramThrowable.getStackTrace()[0].getLineNumber() + "\n" +
                                            paramThread.getStackTrace()[0].getLineNumber(),
                                    "sont16@gmail.com",
                                    "egedzsolt@protonmail.com"
                            );
                        }
                    };
                    emailThread.start();
                }
            });

            //region BATTERY POWER CHECKER TIMER
            android.os.Handler handler = new android.os.Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    exitOnTooLowBattery();

                    handler.postDelayed(this, 10000);
                }
            }, 10000);
            //endregion

            inactivity_handler = new Handler();
            inactivity_runnable = new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "INACTIVE", Toast.LENGTH_SHORT).show();
                    SontHelper.sendEmail(
                            "[SONTY] SERVICE INACTIVE",
                            "SERVICE is INACTIVE since 1 HOUR!",
                            "sont16@gmail.com",
                            "egedzsolt@protonmail.com"
                    );
                }
            };

            startInactivityHandler();

            /*adapter = new ArrayAdapter<String>(getApplicationContext(),
                    android.R.layout.simple_list_item_1,
                    listItems);

            bllistview.setAdapter(adapter);

            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.CENTER;
            params.x = 0;
            params.y = 700;
*/
            /*
            bllistview.setOnTouchListener(new View.OnTouchListener() {
                private int lastAction;
                private int initialX;
                private int initialY;
                private float initialTouchX;
                private float initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();

                            lastAction = event.getAction();
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (lastAction == MotionEvent.ACTION_DOWN) {
                                //OPEN MAIN ACTIVITY
                                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                getApplicationContext().startActivity(intent);
                                //stopSelf();
                            }
                            lastAction = event.getAction();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);

                            headoverlay_mWindowManager.updateViewLayout(headoverlay_mHeadView, params);
                            lastAction = event.getAction();
                            return true;
                    }
                    return false;
                }
            });
            */
            //bl = new SontHelper.BluetoothThings();
            SontHelper.BluetoothThings.BluetoothDeviceListener listener = new SontHelper.BluetoothThings.BluetoothDeviceListener() {
                @Override
                public void found(BluetoothDevice device) {
                    try {
                        SontHelper.showAlertDialog(getApplicationContext(),
                                "Bluetooth Classic device found",
                                device.getName() + " -> " + device.getAddress(),
                                22
                        );
                        if (CURRENT_LOCATION != null)
                            SontHelper.fileIO.writeExperimental(getApplicationContext(),
                                    "bluetoothlist.txt",
                                    "\n\n" + SontHelper.getCurrentTimeHumanReadable() +
                                            "\n" + CURRENT_LOCATION.toString() + "\n" +
                                            device.getName() + "\n" +
                                            device.getAddress(),
                                    true
                            );

                        //if (bl_devices.containsValue(device) != true) {
                        //   if (bl_devices.size() >= 1) {
                        // headoverlay_lay_txt2.setText(headoverlay_lay_txt2.getText() + "\n");
                        //}
                        //   bl_devices.put(CURRENT_LOCATION, device);
                        //   if (headoverlay_lay_txt2 != null) {
                        //if (adapter.getCount() > 10) {
                        //adapter.clear();
                        //adapter.notifyDataSetChanged();
                        //}
                        //adapter.add(device.getName() + " -> " + device.getAddress());
                        //headoverlay_lay_txt2.setVisibility(View.VISIBLE);
                        //if (headoverlay_lay_txt2.getText().length() > 250)
                        //   headoverlay_lay_txt2.setText("");
                                /*headoverlay_lay_txt2.setText(
                                        headoverlay_lay_txt2.getText() + SontHelper.getCurrentOnlyTimeHumanReadable() +
                                                " " + device.getName() + " -> " +
                                                device.getAddress() + "\n"
                                );*/
                        //}
                        //}
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void found(BluetoothDevice device, int rssi) {
                    try {
                        SontHelper.showAlertDialog(getApplicationContext(),
                                "Bluetooth LE device found",
                                device.getName() + "\n" + device.getAddress(),
                                22
                        );
                        if (CURRENT_LOCATION != null)
                            SontHelper.fileIO.writeExperimental(getApplicationContext(),
                                    "bluetoothlist.txt",
                                    "\n\n" + SontHelper.getCurrentTimeHumanReadable() + "\n" +
                                            CURRENT_LOCATION.toString() + "\n" +
                                            device.getName() + "_LE -> " +
                                            device.getAddress(),
                                    true
                            );
                        //if (bl_devices.containsValue(device) != true) {
                        //  if (bl_devices.size() >= 1) {
                        //    headoverlay_lay_txt2.setText(
                        //          headoverlay_lay_txt2.getText() + "\n");
                        // }
                        //bl_devices.put(CURRENT_LOCATION, device);
                        //if (headoverlay_lay_txt2 != null) {
                        //  if (adapter.getCount() > 10) {
                        //    adapter.clear();
                        //  adapter.notifyDataSetChanged();
                        //}
                        //adapter.add(device.getName() + " -> " + device.getAddress());
                        //headoverlay_lay_txt2.setVisibility(View.VISIBLE);
                        //if (headoverlay_lay_txt2.getText().length() > 250)
                        //   headoverlay_lay_txt2.setText("");
                                /*headoverlay_lay_txt2.setText(headoverlay_lay_txt2.getText() +
                                        SontHelper.getCurrentOnlyTimeHumanReadable() + " @LE_" + device.getName() +
                                        " -> " + device.getAddress() +
                                        " -> RSSI: " + rssi + "\n"
                                );*/
                        //}
                        //}
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            //bl.addListener(listener);

            //startBLscanFromService(getApplicationContext());
            //startBLLEscanFromService(getApplicationContext());


            Log.d("SERVICE_INITIALIZATION", "Elapsed time: " + elapsed_since_start.getElapsed());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void startBLLEscanFromService(Context ctx) {
        SontHelper.BluetoothThings.startBLLEscan(ctx);
    }

    public static void startBLscanFromService(Context ctx) {
        SontHelper.BluetoothThings.startBLscan(ctx);
    }

    public static void stopBLLEscanFromService(Context ctx) {

    }

    public static void stopBLscanFromService(Context ctx) {
        SontHelper.BluetoothThings.stopBLscan(ctx);
    }

    public void resetInactivityHandler() {
        stopInactivityHandler();
        startInactivityHandler();
    }

    public void stopInactivityHandler() {
        inactivity_handler.removeCallbacks(inactivity_runnable);
    }

    public void startInactivityHandler() {
        inactivity_handler.postDelayed(inactivity_runnable, 3600000); // 1h minutes
    }

    public static void initHeadOverlay(Context c) {/*
        try {
            if (headoverlay_mHeadView != null) {
                headoverlay_mHeadView.setVisibility(View.GONE);
                headoverlay_mHeadView = null;
            }
            headoverlay_mHeadView = LayoutInflater.from(c).inflate(R.layout.head_layout, null);
            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.CENTER;
            params.x = 0;
            params.y = 700;
            headoverlay_mWindowManager = (WindowManager) c.getSystemService(WINDOW_SERVICE);
            headoverlay_mWindowManager.addView(headoverlay_mHeadView, params);

            headoverlay_lay_txt1 = headoverlay_mHeadView.findViewById(R.id.laytext);
            headoverlay_lay_txt2 = headoverlay_mHeadView.findViewById(R.id.laytext2);
            headoverlay_lay_btn1 = headoverlay_mHeadView.findViewById(R.id.lay_btn1);
            headoverlay_lay_btn2 = headoverlay_mHeadView.findViewById(R.id.lay_btn2);
            bllistview = headoverlay_mHeadView.findViewById(R.id.bllist);

            headoverlay_lay_btn1.setText("Ping");
            headoverlay_lay_btn2.setText("Hide");
            headoverlay_lay_btn1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    headoverlay_lay_txt1.setText(String.valueOf(System.currentTimeMillis()));
                }
            });
            headoverlay_lay_btn2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    headoverlay_mHeadView.setVisibility(View.GONE);
                    headoverlay_mHeadView = null;
                }
            });

            headoverlay_lay_txt1.setText("SERVICE STARTED");
            headoverlay_lay_txt1.setOnTouchListener(new View.OnTouchListener() {
                private int lastAction;
                private int initialX;
                private int initialY;
                private float initialTouchX;
                private float initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();

                            lastAction = event.getAction();
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (lastAction == MotionEvent.ACTION_DOWN) {
                                //OPEN MAIN ACTIVITY
                                Intent intent = new Intent(c, MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                c.startActivity(intent);
                                //stopSelf();
                            }
                            lastAction = event.getAction();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);

                            headoverlay_mWindowManager.updateViewLayout(headoverlay_mHeadView, params);
                            lastAction = event.getAction();
                            return true;
                    }
                    return false;
                }
            });
            headoverlay_lay_txt2.setVisibility(View.GONE);
            headoverlay_lay_txt2.setOnTouchListener(new View.OnTouchListener() {
                private int lastAction;
                private int initialX;
                private int initialY;
                private float initialTouchX;
                private float initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();

                            lastAction = event.getAction();
                            return true;
                        case MotionEvent.ACTION_UP:
                            if (lastAction == MotionEvent.ACTION_DOWN) {
                                //OPEN MAIN ACTIVITY
                                Intent intent = new Intent(c, MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                c.startActivity(intent);
                                //stopSelf();
                            }
                            lastAction = event.getAction();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);

                            headoverlay_mWindowManager.updateViewLayout(headoverlay_mHeadView, params);
                            lastAction = event.getAction();
                            return true;
                    }
                    return false;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        */
    }

    public void updateCurrent(String title, String text) {
        try {
            if (gnss_count == null) {
                gnss_count = "0";
            }

            int color_drawable = R.drawable.service;
            if (service_icon_lastColor == "red") {
                color_drawable = R.drawable.service_gray;
                service_icon_lastColor = "gray";
            } else if (service_icon_lastColor == "gray") {
                color_drawable = R.drawable.service;
                service_icon_lastColor = "red";
            }
            /*
            Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(),
                    0, notificationIntent, 0);
            */

            Intent notificationIntent = new Intent(getApplicationContext(), receiver.class);
            notificationIntent.putExtra("29293", "29293");
            PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 29293, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

            Intent intent_start = new Intent(getApplicationContext(), receiver.class);
            Intent intent_pause = new Intent(getApplicationContext(), receiver.class);
            Intent intent_exit = new Intent(getApplicationContext(), receiver.class);
            Intent start_bl = new Intent(getApplicationContext(), receiver.class);
            Intent stop_bl = new Intent(getApplicationContext(), receiver.class);

            intent_exit.setAction("exit");
            intent_start.setAction("start");
            intent_pause.setAction("pause");
            start_bl.setAction("start_bl");
            stop_bl.setAction("stop_bl");

            PendingIntent pi_start = PendingIntent.getBroadcast(getApplicationContext(), 1, intent_start, PendingIntent.FLAG_IMMUTABLE);
            PendingIntent pi_pause = PendingIntent.getBroadcast(getApplicationContext(), 1, intent_pause, PendingIntent.FLAG_IMMUTABLE);
            PendingIntent pi_exit = PendingIntent.getBroadcast(getApplicationContext(), 1, intent_exit, PendingIntent.FLAG_IMMUTABLE);
            PendingIntent pi_start_bl = PendingIntent.getBroadcast(getApplicationContext(), 1, start_bl, PendingIntent.FLAG_IMMUTABLE);
            PendingIntent pi_stop_bl = PendingIntent.getBroadcast(getApplicationContext(), 1, stop_bl, PendingIntent.FLAG_IMMUTABLE);

            String ip = NetworkUtils.getIPAddress(true);

            String elapsed = elapsed_since_start.getElapsed();
            String finalText = "Elapsed: " + elapsed + " @ " + started_at_battery + "%";

            if (scanresult == null) {
                scanresult = new ArrayList<>();
            }
            Notification notification = new NotificationCompat.Builder(getApplicationContext(), "sonty")
                    .setContentTitle(title + " | Started: " + started_time)
                    .setContentText(text)
                    /* Known Direct Subclasses
                    NotificationCompat.BigPictureStyle,NotificationCompat.BigTextStyle,NotificationCompat.DecoratedCustomViewStyle,NotificationCompat.InboxStyle,NotificationCompat.MediaStyle,NotificationCompat.MessagingStyle
                    */
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(finalText)
                            .setSummaryText("IP: " + ip)
                            .setBigContentTitle("Satellites: " + gnss_count)
                    )
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setSmallIcon(color_drawable)
                    .setContentIntent(pendingIntent)
                    .setChannelId("sonty")
                    .addAction(R.drawable.service, "BL START", pi_start_bl)
                    .addAction(R.drawable.service, "BL STOP", pi_stop_bl)
                    //.addAction(R.drawable.service, "START", pi_start)
                    //.addAction(R.drawable.service, "PAUSE", pi_pause)
                    .addAction(R.drawable.service, "EXIT", pi_exit)
                    .build();

            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.notify(55, notification);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showOngoing() {
        try {
            Intent notificationIntent = new Intent(getApplicationContext(), receiver.class);
            notificationIntent.putExtra("29293", "29293");
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    getApplicationContext(),
                    29293,
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
            );

            Intent intent = new Intent(getApplicationContext(), receiver.class);
            intent.setAction("exit");
            PendingIntent pi = PendingIntent.getBroadcast(getApplicationContext(), 1, intent, PendingIntent.FLAG_IMMUTABLE);

            Notification notification = new NotificationCompat.Builder(getApplicationContext(), "sonty")
                    .setContentTitle("Sonty Service" + " | Started: " + started_time)
                    .setContentText("Waiting for GPS")
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setSmallIcon(R.drawable.service)
                    .setContentIntent(pendingIntent)
                    .setChannelId("sonty")
                    .addAction(R.drawable.service, "EXIT",
                            pi)
                    .build();

            startForeground(55, notification);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    public void exitOnTooLowBattery() {
        try {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (batLevel <= 25 && SontHelper.isBatteryCharging(getApplicationContext()) == false) {
                Toast.makeText(getApplicationContext(), "Exiting, too low battery!", Toast.LENGTH_LONG).show();
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int convertDBM(int dbm) {
        int quality;
        if (dbm <= -100)
            quality = 0;
        else if (dbm >= -50)
            quality = 100;
        else
            quality = 2 * (dbm + 100);
        return quality;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        restartService();
        super.onTaskRemoved(rootIntent);
    }

    public void restartService() {
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());

        PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT);
        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent);
    }

    BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // make a bit delay to test out power consumption
            try {
                /*TextView headtext = headoverlay_mHeadView.findViewById(R.id.laytext);
                headtext.setText(Html.fromHtml(
                        "New: <b>" + recorded + "</b> | " +
                                "Updated: <b>" + updated + "</b><br>" +
                                "Not updated: <b>" + not_touched + "</b> | " +
                                "Retry: <b>" + retry_count + "</b>",
                        Html.FROM_HTML_MODE_COMPACT
                ));
                headtext.setTextSize(15f);
                headtext.setBackgroundResource(R.color.headOverlayTextBackgroundColor);
*/
                Thread scanThread = new Thread() {
                    @Override
                    public void run() {
                        try {
                            scanresult = wifiManager.getScanResults();
                            Thread.sleep(3000);
                            wifiManager.startScan();
                            teu = new SontHelper.TimeElapsedUtil();
                            teu.setStartTime(System.currentTimeMillis());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };
                scanThread.start();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
}

