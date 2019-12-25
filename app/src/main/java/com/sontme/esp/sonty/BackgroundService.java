package com.sontme.esp.sonty;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.location.GnssStatus;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.StringRequestListener;
import com.github.anrwatchdog.ANRError;
import com.github.anrwatchdog.ANRWatchDog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BackgroundService extends Service {

    public static String INSERT_URL = "https://sont.sytes.net/wifilocator/wifi_insert.php";
    public WifiManager wifiManager;
    public LocationManager locationManager;
    public LocationListener locationListener;
    public static List<ScanResult> scanresult;

    public static Set<String> unique = new LinkedHashSet<>();
    /*
    public static HashSet<String> unique2 = new HashSet<>();
    public static LinkedHashSet<String> unique3 = new LinkedHashSet<>();
    public static TreeSet<String> unique4 = new TreeSet<>();
    */

    public static int count = 0;
    public static String lastcolor = "red";
    public static String started_time;
    public static long capacity;

    public static int recorded; // new
    public static int updated; // regi_old, regi_str
    public static int not_touched; // not_recorded
    public static int retry_count; // http retry;

    static WindowManager mWindowManager;
    public static View mHeadView;
    static Button lay_btn1;
    static Button lay_btn2;
    static TextView lay_txt1;

    static SontHelper.TimeElapsedUtil teu;
    static String gns;
    Map<Integer, String> satellite_types = new HashMap<Integer, String>();
    boolean reallyexit = false;

    @Override
    public void onCreate() {
        try {
            super.onCreate();

            SontHelper.TimeElapsedUtil t = new SontHelper.TimeElapsedUtil();
            t.setStartTime(System.currentTimeMillis());

            exitOnTooLowBattery();

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

            if (SontHelper.getSSID(getApplicationContext()).contains("UPCAEDB2C3"))
                SontHelper.blinkLed(getApplicationContext(), 5);

            registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

            wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    try {
                        Log.d("WIFI_LOCATION", "Location Changed: " + location.toString());
                        count++;
                        String notificationText;
                        String title = "Sonty Service";
                        /* !!! IMPORTANT !! LOGIC !!! */
                        if (wifiManager.getScanResults() != null) { // scanResult
                            if (wifiManager.getScanResults().isEmpty() != true) {

                                notificationText = "Count: " + count +
                                        " @ " + SontHelper.locationToStringAddress(getApplicationContext(), location);

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
                                                    lay_txt1.setText("HTTP ERROR");
                                                    e.printStackTrace();
                                                }
                                            }

                                        };
                                        saveHttpThread.start();

                                        Log.d("ELAPSED_TIME_SINCE_WIFI_SCAN", "TIME: " + teu.getElapsed());
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

            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "PERMISSION ERROR", Toast.LENGTH_LONG).show();
            }
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, null);
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 3, 5, locationListener);
            locationManager.addGpsStatusListener(new GpsStatus.Listener() {
                @Override
                public void onGpsStatusChanged(int event) {
                    if (event == GpsStatus.GPS_EVENT_STOPPED) {
                        updateCurrent("Sonty Service", "[GPSS] Signal Lost");
                        //Toast.makeText(getApplicationContext(),"GPS SIGNAL LOST",Toast.LENGTH_LONG).show();
                    }
                    if (event == GpsStatus.GPS_EVENT_STARTED || event == GpsStatus.GPS_EVENT_FIRST_FIX) {
                        //Waiting for
                        updateCurrent("Sonty Service", "[GPS] Waiting..");
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
                    gns = String.valueOf(status.getSatelliteCount());
                    for (int i = 0; i < status.getSatelliteCount(); i++) {
                        int type = status.getConstellationType(i);
                        satellite_types.put(i, convert(type));
                        Log.d("GNSS_", "TYPE: " + i + " -> " + convert(type));
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
                                    "[SONTY][ANR] SERVICE STARTED",
                                    "SERVICE THREAD NOT RESPONDING \n" + error.toString(),
                                    "sont16@gmail.com",
                                    "egedzsolt@protonmail.com"
                            );
                        }
                    };
                    anrThread.start();
                }
            });

            if (wifiManager.isWifiEnabled() == false) {
                Toast.makeText(getApplicationContext(), "Please TURN ON WIFI", Toast.LENGTH_SHORT).show();
            }

            wifiManager.startScan();

            initHeadOverlay(getApplicationContext());

            // Catch all Exception globally (class level (BackgroundService))
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
                                    "Error: " + paramThread.getName() + " _ " + paramThrowable.getMessage() + "\n" + paramThread.toString() + "\n" + paramThrowable.toString(),
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
            handler.postDelayed(new

                                        Runnable() {
                                            public void run() {
                                                exitOnTooLowBattery();
                                                handler.postDelayed(this, 10000);
                                            }
                                        }, 10000);
            //endregion

            Thread emailThread = new Thread() {
                @Override
                public void run() {
                    SontHelper.sendEmail(
                            "[SONTY] SERVICE STARTED",
                            "SERVICE STARTED \n IP: " + SontHelper.getLocalIpAddress(),
                            "sont16@gmail.com",
                            "egedzsolt@protonmail.com"
                    );
                }
            };
            emailThread.start();

            Log.d("SERVICE_INITIALIZATION_TOOK: ", "Elapsed time: " + t.getElapsed());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void initHeadOverlay(Context c) {
        mHeadView.setVisibility(View.GONE);
        mHeadView = null;
        mHeadView = LayoutInflater.from(c).inflate(R.layout.head_layout, null);
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        params.x = 0;
        params.y = 700;
        mWindowManager = (WindowManager) c.getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mHeadView, params);

        lay_txt1 = mHeadView.findViewById(R.id.laytext);
        lay_btn1 = mHeadView.findViewById(R.id.lay_btn1);
        lay_btn2 = mHeadView.findViewById(R.id.lay_btn2);

        lay_btn1.setText("Ping");
        lay_btn2.setText("Hide");
        lay_btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lay_txt1.setText(String.valueOf(System.currentTimeMillis()));
            }
        });
        lay_btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHeadView.setVisibility(View.GONE);
            }
        });

        lay_txt1.setText("SERVICE STARTED");

        lay_txt1.setOnTouchListener(new View.OnTouchListener() {
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

                        mWindowManager.updateViewLayout(mHeadView, params);
                        lastAction = event.getAction();
                        return true;
                }
                return false;
            }
        });

    }

    public void updateCurrent(String title, String text) {
        try {
            if (gns == null) {
                gns = "0";
            }

            int color_drawable = R.drawable.service;
            if (lastcolor == "red") {
                color_drawable = R.drawable.service_gray;
                lastcolor = "gray";
            } else if (lastcolor == "gray") {
                color_drawable = R.drawable.service;
                lastcolor = "red";
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

            intent_exit.setAction("exit");
            intent_start.setAction("start");
            intent_pause.setAction("pause");

            PendingIntent pi_start = PendingIntent.getBroadcast(getApplicationContext(), 1, intent_start, PendingIntent.FLAG_IMMUTABLE);
            PendingIntent pi_pause = PendingIntent.getBroadcast(getApplicationContext(), 1, intent_pause, PendingIntent.FLAG_IMMUTABLE);
            PendingIntent pi_exit = PendingIntent.getBroadcast(getApplicationContext(), 1, intent_exit, PendingIntent.FLAG_IMMUTABLE);

            String ip = NetworkUtils.getIPAddress(true);

            String x = "";
            ArrayList<String> ts = new ArrayList<>();
            for (int i = 0; i < Integer.parseInt(gns); i++) {
                ts.add(satellite_types.get(i));
            }
            for (String s : ts) {
                x += s + " -> " + Collections.frequency(ts, s) + "\n";
            }

            if (scanresult == null) {
                scanresult = new ArrayList<>();
            }
            Notification notification = new NotificationCompat.Builder(getApplicationContext(), "sonty")
                    .setContentTitle(title + " | Started: " + started_time)
                    .setContentText(text)
                    // Known Direct Subclasses
                    // NotificationCompat.BigPictureStyle,NotificationCompat.BigTextStyle,NotificationCompat.DecoratedCustomViewStyle,NotificationCompat.InboxStyle,NotificationCompat.MediaStyle,NotificationCompat.MessagingStyle
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(x)
                            .setSummaryText("IP: " + ip)
                            .setBigContentTitle("Satellites: " + gns)
                    )
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setSmallIcon(color_drawable)
                    .setContentIntent(pendingIntent)
                    .setChannelId("sonty")
                    .addAction(R.drawable.service, "START",
                            pi_start)
                    .addAction(R.drawable.service, "PAUSE",
                            pi_pause)
                    .addAction(R.drawable.service, "EXIT",
                            pi_exit)
                    .build();

            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.notify(55, notification);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showOngoing() {
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
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        Log.d("BLUETOOTH_LE", "Scan Started");
    }

    public void exitOnTooLowBattery() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (batLevel <= 25 && SontHelper.isBatteryCharging(getApplicationContext()) == false) {
            Toast.makeText(getApplicationContext(), "Exiting, too low battery!", Toast.LENGTH_LONG).show();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
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
        //super.onDestroy();
        /*
        BackgroundService.vibrate(getApplicationContext());
        Toast.makeText(getApplicationContext(), "SERVICE EXITING", Toast.LENGTH_LONG).show();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(1);
        notificationManager.cancelAll();

        long apprx = TrafficStats.getUidRxBytes(getApplicationInfo().uid);
        long apptx = TrafficStats.getUidTxBytes(getApplicationInfo().uid);
        long app_r_pa = TrafficStats.getUidRxPackets(getApplicationInfo().uid);
        long app_s_pa = TrafficStats.getUidTxPackets(getApplicationInfo().uid);

        SontHelper.saveSharedPref(getApplicationContext(), "sonty_rx", String.valueOf(apprx));
        SontHelper.saveSharedPref(getApplicationContext(), "sonty_tx", String.valueOf(apptx));

        if (mHeadView != null) mWindowManager.removeView(mHeadView);
        */
        //SontHelper.createNotifGroup(getApplicationContext(), "sonty", "sonty");
        //SontHelper.createNotificationChannel(getApplicationContext(), "sonty", "sonty");
        if (reallyexit != true) {
            Intent i = new Intent(getApplicationContext(), BackgroundService.class);
            startForegroundService(i);
        } else {
            super.onDestroy();
        }
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
            TextView headtext = mHeadView.findViewById(R.id.laytext);
            headtext.setText(Html.fromHtml(
                    "New: <b>" + recorded + "</b> | " +
                            "Updated: <b>" + updated + "</b><br>" +
                            "Not updated: <b>" + not_touched + "</b> | " +
                            "Retry: <b>" + retry_count + "</b>",
                    Html.FROM_HTML_MODE_COMPACT
            ));
            headtext.setTextSize(15f);
            headtext.setBackgroundResource(R.color.headOverlayTextBackgroundColor);

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
        }
    };
}

class SAVE_HTTP extends AsyncTask<String, String, String> {

    String full_url;
    Context c;

    public SAVE_HTTP(String full_url, Context c) {
        this.full_url = full_url;
        this.c = c;
    }

    @Override
    protected String doInBackground(String... strings) {
        AndroidNetworking.get(full_url)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        //Log.d("HTTP_RESPONSE_DEBUG_", "'" + response + "'" + "_" + response.length());
                        if (response.contains("new")) {
                            BackgroundService.recorded++;
                            SontHelper.vibrate(c);
                            if (SontHelper.getSSID(c).contains("UPCAEDB2C3")) {
                                SontHelper.blinkLed(c, 50);
                            } else {
                                SontHelper.vibrate(c);
                                Thread vibroThread = new Thread() {
                                    @Override
                                    public void run() {
                                        SontHelper.Vibratoor.init(c);
                                        SontHelper.Vibratoor.makePattern().beat(40).rest(50).beat(40).playPattern(2);
                                    }
                                };
                                vibroThread.start();
                            }
                        } else if (response.contains("regi_old")) {
                            SontHelper.vibrate(c);
                            BackgroundService.updated++;
                        } else if (response.contains("not_recorded")) {
                            BackgroundService.not_touched++;
                        }
                        /*
                        if (response.equalsIgnoreCase("new") == true ||
                                response.equalsIgnoreCase("regi_str") == true ||
                                response.equalsIgnoreCase("regi_old")) {
                            // or .contains?
                            try {
                                if (c != null) {
                                    BackgroundService.vibrate(c);
                                    SontHelper.blinkLed(c, 50);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }*/
                    }

                    @Override
                    public void onError(ANError anError) {
                        BackgroundService.retry_count++;
                        Log.d("HTTP_ERROR_", anError.toString());
                    }
                });

        return null;
    }
}
