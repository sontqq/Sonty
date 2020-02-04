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
import com.google.common.base.Throwables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BackgroundService extends Service {

    /*public static String INSERT_URL =
            "https://sont.sytes.net/wifilocator/wifi_insert.php";*/
    Gson gson;
    public WifiManager wifiManager;
    public LocationManager locationManager;
    public LocationListener locationListener;
    public static List<ScanResult> scanresult;

    public static Set<String> unique = new LinkedHashSet<>();

    public static int count = 0;
    public static String service_icon_lastColor = "red";
    public static String started_time;

    public static int recorded;
    public static int updated;
    public static int not_touched;
    public static int retry_count;

    static int started_at_battery;
    static String gnss_count;

    ANRWatchDog watchdog;

    boolean developerDevice;

    public static Location CURRENT_LOCATION;

    public static int meterInterval = 5;
    public static int timeInterval = 3;

    @Override
    public void onCreate() {
        super.onCreate();

        exitOnTooLowBattery(15);
        SontHelper.createNotifGroup(getApplicationContext(), "sonty", "sonty");
        SontHelper.createNotificationChannel(getApplicationContext(), "sonty", "sonty");

        showOngoing("Standby");

        developerDevice = SontHelper.getDeviceName().contains("Samsung SM-A530F");
        //region HOME CHECK
            /*
            if (SontHelper.getSSID(getApplicationContext()).contains("UPCAEDB2C3")) {
                SontHelper.blinkLed(getApplicationContext(), 5);
            }
            */
        //endregion

        if (developerDevice == true) { /* SAJAT */
            started_at_battery = SontHelper.getBatteryLevel(getApplicationContext());
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            Date resultdate = new Date(System.currentTimeMillis());
            started_time = sdf.format(resultdate);

            registerReceiver(wifiReceiver, new IntentFilter(
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
            ));

            watchdog = new ANRWatchDog();
            watchdog.setName("service_main_thread");
            watchdog.setReportAllThreads();
            ANRWatchDog.ANRListener listener = new ANRWatchDog.ANRListener() {
                @Override
                public void onAppNotResponding(ANRError error) {
                    Thread anrThread = new Thread() {
                        @Override
                        public void run() {
                            SontHelper.ExceptionHandler.appendException(
                                    "NOT RESPONDING ERROR\n\n" +
                                            "TIMEOUT",
                                    getApplicationContext()
                            );

                            SontHelper.ExceptionHandler.sendToRemoteLogServer(
                                    "NOT RESPONDING ERROR\n\n" +
                                            "TIMEOUT"
                            );
                        }
                    };
                    anrThread.start();
                }
            };
            watchdog.setANRListener(listener);
            watchdog.setInterruptionListener(new ANRWatchDog.InterruptionListener() {
                @Override
                public void onInterrupted(InterruptedException exception) {
                    Thread anrThread = new Thread() {
                        @Override
                        public void run() {
                            SontHelper.ExceptionHandler.appendException(
                                    "NOT RESPONDING ERROR\n\n" +
                                            "TIMEOUT" + "\n\n" + exception.getCause().getStackTrace()[0].toString(),
                                    getApplicationContext()
                            );

                            SontHelper.ExceptionHandler.sendToRemoteLogServer(
                                    "NOT RESPONDING ERROR\n\n" +
                                            "TIMEOUT" + SontHelper.getDeviceName() + "\n\n" + exception.getCause().getStackTrace()[0].toString()
                            );
                        }
                    };
                    anrThread.start();
                }
            });
            watchdog.start();

            Thread vibroThread = new Thread() {
                @Override
                public void run() {
                    SontHelper.Vibratoor.init(getApplicationContext());
                    SontHelper.Vibratoor.makePattern().beat(500).rest(80).beat(100).rest(80).beat(500).rest(80).beat(80).playPattern(1);
                }
            };
            vibroThread.start();

            wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
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
                                            String response = "";
                                            try {
                                                response = SAVE_HTTP_CUSTOM.GET_PAGE(
                                                        "sont.sytes.net",
                                                        80,
                                                        "/wifilocator/wifi_insert.php" + reqBody,
                                                        false, ""
                                                );

                                            } catch (NullPointerException e) {
                                                /*SontHelper.sendEmail("HTTP EXCEPTION",
                                                        SontHelper.ExceptionHandler.convertExceptionHumanReadable(e),
                                                        "sont16@gmail.com",
                                                        "egedzsolt@protonmail.com"
                                                );*/
                                            }
                                            if (response.contains("new")) {
                                                try {
                                                    BackgroundService.recorded++;
                                                    SontHelper.vibrate(getApplicationContext());
                                                    if (SontHelper.getSSID(getApplicationContext()).contains("UPCAEDB2C3")) {
                                                        SontHelper.blinkLed(getApplicationContext(), 50);
                                                    } else {
                                                        SontHelper.vibrate(getApplicationContext());
                                                    }
                                                } catch (Exception e) {

                                                }
                                            } else if (response.contains("regi_old")) {
                                                SontHelper.vibrate(getApplicationContext());
                                                BackgroundService.updated++;
                                            } else if (response.contains("not_recorded")) {
                                                BackgroundService.not_touched++;
                                            }


                                        }

                                    };
                                    saveHttpThread.start();

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

                    //save_custom = new SAVE_HTTP_CUSTOM();

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
                    locationManager.removeUpdates(locationListener);
                    Toast.makeText(getApplicationContext(), "Provider disabled", Toast.LENGTH_SHORT).show();
                }
            };

            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "PERMISSION ERROR", Toast.LENGTH_LONG).show();
            }

            locationManager.addGpsStatusListener(new GpsStatus.Listener() {
                @Override
                public void onGpsStatusChanged(int event) {
                    if (event == GpsStatus.GPS_EVENT_STOPPED) {
                        updateCurrent("Sonty Service", "[GPSS] Signal Lost");
                    }
                    if (event == GpsStatus.GPS_EVENT_STARTED) {
                        updateCurrent("Sonty Service", "[GPS] Connecting..");
                    }
                    if (event == GpsStatus.GPS_EVENT_FIRST_FIX) {
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
                    /*for (int i = 0; i < status.getSatelliteCount(); i++) {
                        //int type = status.getConstellationType(i);
                        //satellite_types.put(i, convert(type));
                        //Log.d("GNSS_", "TYPE: " + i + " -> " + convert(type));
                    }*/
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

            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    timeInterval, /* 3 */
                    meterInterval, /* 5 */
                    locationListener
            );
        } else {
            //region SINGLE LOCATION REQUEST
            Handler handler = new Handler();
            Runnable runnable = new Runnable() {
                public void run() {

                    SontHelper.ExceptionHandler.sendToRemoteLogServer(SontHelper.getDeviceName() + "_ALIVE");
                    handler.postDelayed(this, 10000);
                }
            };
            //endregion
        }

        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(
                            Thread paramThread, Throwable paramThrowable) {
                        try {
                            Thread t = new Thread() {
                                @Override
                                public void run() {
                                    //String all_error = "";
                                    //for (StackTraceElement s : paramThrowable.getStackTrace()) {
                                    //all_error += s + "\n\n";
                                    //}

                                    Throwable rootCause = paramThrowable;
                                    while (rootCause.getCause() != null && rootCause.getCause() != rootCause)
                                        rootCause = rootCause.getCause();

                                    String className = rootCause.getStackTrace()[0].getClassName();
                                    String methodName = rootCause.getStackTrace()[0].getMethodName();
                                    String fileName = rootCause.getStackTrace()[0].getFileName();
                                    int lineNumber = rootCause.getStackTrace()[0].getLineNumber();

                                    String ret = "Class Name: " + className + "\n" +
                                            "Method Name: " + methodName + "\n" +
                                            "Line Number: " + lineNumber + "\n" +
                                            "File Name: " + fileName;

                                    String stackTrc = Throwables.getStackTraceAsString(paramThrowable);
                                    gson = new GsonBuilder().setPrettyPrinting().create();
                                    String thread = gson.toJson(paramThread);
                                    String throwable = gson.toJson(paramThrowable);

                                    String finalAll_error = thread + "\n\n" + throwable;// + "\n\n" + stackTrc;

                                    SontHelper.ExceptionHandler.appendException(
                                            "UNHANDLED EXCEPTION\n\n" +
                                                    finalAll_error,
                                            getApplicationContext()
                                    );

                                    SontHelper.ExceptionHandler.sendToRemoteLogServer(
                                            "UNHANDLED EXCEPTION\n\n" +
                                                    finalAll_error
                                    );
                                    String summary = SontHelper.ExceptionHandler.convertExceptionHumanReadable((Exception) paramThrowable);

                                    SontHelper.sendEmail(
                                            "UNHANDLED EXCEPTION",
                                            "SUMMARY:\n" + ret + "\n\nSUMMARY_2:\n" + summary + "\n\n" + finalAll_error,
                                            "sont16@gmail.com",
                                            "egedzsolt@protonmail.com"
                                    );

                                }
                            };
                            t.start();

                            uncaughtException(paramThread, paramThrowable);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
        );

        final String[] last = {""};

        //region BATTERY POWER CHECKER TIMER
        android.os.Handler handler = new android.os.Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                exitOnTooLowBattery(15);
                if (developerDevice == true) {
                    Thread t = new Thread() {
                        @Override
                        public void run() {
                            String new_update = "PING_battery_" +
                                    SontHelper.getBatteryLevel(getApplicationContext()) + "_" +
                                    SontHelper.getCurrentWifiName(getApplicationContext()) + "_" +
                                    SontHelper.getLocalIpAddress();
                            if (last[0].equals(new_update) == false) {
                                SontHelper.ExceptionHandler.sendToRemoteLogServer(new_update);
                                last[0] = new_update;
                            }
                        }
                    };
                    t.start();
                }
                handler.postDelayed(this, 10000);
            }
        }, 10000);
        //endregion

        Thread notifyAppStarted = new Thread() {
            @Override
            public void run() {
                SontHelper.ExceptionHandler.sendToRemoteLogServer(
                        "APP STARTED " + SontHelper.getDeviceName()
                );
            }
        };
        notifyAppStarted.start();

        //Log.e("CUSTOM_LOGGING_", "LOGCAT: " + SontHelper.ExceptionHandler.getLogcat());
    }

    public void updateCurrent(String title, String text) {
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
            /* // OPEN MAIN ACTIVITY ON CLICK
            Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(),
                    0, notificationIntent, 0);
            */

        Intent notificationIntent = new Intent(getApplicationContext(), receiver.class);
        notificationIntent.putExtra("29293", "29293");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 29293, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent intent_exit = new Intent(getApplicationContext(), receiver.class);
        intent_exit.setAction("exit");
        PendingIntent pi_exit = PendingIntent.getBroadcast(getApplicationContext(), 1, intent_exit, PendingIntent.FLAG_IMMUTABLE);

        String ip = NetworkUtils.getIPAddress(true);
        String finalText = "Started at BATTERY: " + started_at_battery + "%";

        if (scanresult == null) {
            scanresult = new ArrayList<>();
        }
        Notification notification = new NotificationCompat.Builder(getApplicationContext(), "sonty")
                .setContentTitle(title + " | Started: " + started_time)
                .setContentText(text)
                /* Known Direct Subclasses
                NotificationCompat.BigPictureStyle,NotificationCompat.BigTextStyle,NotificationCompat.DecoratedCustomViewStyle,NotificationCompat.InboxStyle,NotificationCompat.MediaStyle,NotificationCompat.MessagingStyle
                */
                /*.setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(finalText)
                        .setSummaryText("IP: " + ip)
                        .setBigContentTitle("Satellites: " + gnss_count)
                )*/
                .setCategory(Notification.CATEGORY_SERVICE)
                .setSmallIcon(color_drawable)
                .setContentIntent(pendingIntent)
                .setChannelId("sonty")
                .addAction(R.drawable.service, "EXIT", pi_exit)
                .build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(55, notification);
    }

    public void showOngoing(String text) {
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
                .setContentTitle("Sonty Service")
                .setContentText(text)
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
    }

    public void exitOnTooLowBattery(int limit) {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (batLevel <= limit && SontHelper.isBatteryCharging(getApplicationContext()) == false) {
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
        if (intent != null && intent.getExtras() != null) {
            String time = intent.getExtras().getString("timeInterval");
            String meter = intent.getExtras().getString("meterInterval");
            Log.d("SERVICE_TESTING", time + "_" + meter);
        }

        /*int time = 0;
        int meter = 0;
        try {
            time = Integer.parseInt(intent.getStringExtra("timeInterval"));
            meter = Integer.parseInt(intent.getStringExtra("meterInterval"));
        } catch (Exception e) {
            e.printStackTrace();
        }*/
        //Log.d("SERVICE_TESTING", "DATAS onstartCommand(): " + time + "_" + meter);
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
            Thread scanThread = new Thread() {
                @Override
                public void run() {
                    try {
                        scanresult = wifiManager.getScanResults();
                        Thread.sleep(3000);
                        wifiManager.startScan();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            scanThread.start();

        }
    };
}

