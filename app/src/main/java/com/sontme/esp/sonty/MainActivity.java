package com.sontme.esp.sonty;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.Priority;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.StringRequestListener;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.gson.Gson;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import com.google.common.annotations.Beta;

public class MainActivity extends AppCompatActivity {

    WebView web1;
    WebView web2;
    WebView web3;
    Gson gson;
    TextView wificount;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        View someView = findViewById(R.id.rootElement);
        View root = someView.getRootView();
        root.setBackgroundColor(Color.parseColor("#00574B"));

        gson = new Gson();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
                try {
                    Thread t = new Thread() {
                        @Override
                        public void run() {
                            String thread = gson.toJson(paramThread);
                            String throwable = gson.toJson(paramThrowable);

                            String finalAll_error = thread + "\n\n" + throwable;

                            SontHelper.ExceptionHandler.appendException(
                                    "UNCAUGHT ERROR\n\n" +
                                            finalAll_error,
                                    getApplicationContext()
                            );

                            SontHelper.ExceptionHandler.sendToRemoteLogServer(
                                    "UNCAUGHT ERROR\n\n" +
                                            finalAll_error
                            );

                            SontHelper.sendEmail("UNHANDLED EXCEPTION",
                                    finalAll_error,
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
        });

        getWifiCount();
        wificount = findViewById(R.id.wificount);
        Button btn = findViewById(R.id.button_refresh);
        Button btn_geterror = findViewById(R.id.button_geterror);
        Button btn_get_logcat = findViewById(R.id.button_getlogcat);
        Button btn_clear_logcat = findViewById(R.id.clear_logcat);

        btn_get_logcat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> logcat_log = SontHelper.ExceptionHandler.getLogcatArray();
                String logcat_errors_str = "";
                int i = 0;
                for (String s : logcat_log) {
                    //if (i >= 150)
                    //  break;
                    logcat_errors_str = logcat_errors_str + i + ": " + s + "\n\n";
                    i++;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("LOGCAT\n" +
                                "#: " + logcat_log.size())
                        .setCancelable(true)
                        .setMessage(logcat_errors_str)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();

            }
        });
        btn_clear_logcat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SontHelper.ExceptionHandler.clearLogcat();
                SontHelper.ExceptionHandler.clearExceptions(getApplicationContext());
                SontHelper.vibrate(getApplicationContext());
            }
        });
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    TextView textview = findViewById(R.id.textview);

                    /*if (BackgroundService.scanresult != null || BackgroundService.unique != null) {
                        textview.setText("Near: " + BackgroundService.scanresult.size() + " Unique: " + BackgroundService.unique.size());
                    }*/

                    Thread netThread = new Thread() {
                        @Override
                        public void run() {
                            SontHelper.vibrate(getApplicationContext());
                            getChart_timer_updated("https://sont.sytes.net/wifilocator/wifis_chart_updated.php");
                            getChart_timer_new("https://sont.sytes.net/wifilocator/wifis_chart_new.php");
                        }
                    };
                    netThread.start();
                    Handler handler = new Handler();
                    Thread cntt = new Thread() {
                        @Override
                        public void run() {
                            TextView wificount = findViewById(R.id.wificount);
                            Document doc;
                            String a = "";
                            try {
                                doc = Jsoup.connect("http://sont.sytes.net/wifilocator/wifi_count.php").get();
                                a = doc.html();

                                String temp_a = a;
                                handler.post(new Runnable() {
                                    public void run() {
                                        wificount.setText(SontHelper.getPlainText(
                                                temp_a
                                        ));
                                    }
                                });
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        }
                    };
                    cntt.start();
                    web1.loadUrl("https://sont.sytes.net/wifilocator/stat_1_week.php");
                    web2.loadUrl("https://sont.sytes.net/wifilocator/stat_1_month.php");
                    web3.loadUrl("https://sont.sytes.net/wifilocator/stat_3_month.php");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        btn_geterror.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> exception_log = SontHelper.ExceptionHandler.readExceptionAllArray(getApplicationContext());
                String exception_errors_str = "";
                int x = 0;
                for (String s : exception_log) {
                    if (x >= 150)
                        break;
                    exception_errors_str = exception_errors_str + x + ": " + s + "\n\n";
                    x++;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("EXCEPTION LOG\n" +
                                "#: " + exception_log.size())
                        .setCancelable(true)
                        .setMessage(exception_errors_str)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();

            }
        });

        SontHelper.createNotifGroup(getApplicationContext(), "sonty", "sonty");
        SontHelper.createNotificationChannel(getApplicationContext(), "sonty", "sonty");

        managePermissions();

        web1 = findViewById(R.id.web1);
        web2 = findViewById(R.id.web2);
        web3 = findViewById(R.id.web3);

        web1.getSettings().setJavaScriptEnabled(true);
        web2.getSettings().setJavaScriptEnabled(true);
        web3.getSettings().setJavaScriptEnabled(true);

        web1.getSettings().setLoadWithOverviewMode(true);
        web2.getSettings().setLoadWithOverviewMode(true);
        web3.getSettings().setLoadWithOverviewMode(true);

        web1.getSettings().setUseWideViewPort(true);
        web2.getSettings().setUseWideViewPort(true);
        web3.getSettings().setUseWideViewPort(true);

        web1.setBackgroundColor(Color.parseColor("#00574B"));
        web2.setBackgroundColor(Color.parseColor("#00574B"));
        web3.setBackgroundColor(Color.parseColor("#00574B"));

        /*AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Refresh Interval?");
        final EditText input = new EditText(this);

        // DIALOGBOX
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText("3");
        builder.setView(input);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BackgroundService.timeInterval = Integer.parseInt(input.getText().toString());
            }
        });
        //builder.show();
        */
        Intent serviceIntent = new Intent(getApplicationContext(), BackgroundService.class);
        //Bundle extras = serviceIntent.getExtras();
        //extras.putString("timeInterval", "3");
        //extras.putString("meterInterval", "5");
        //serviceIntent.putExtra("timeInterval",3);
        //serviceIntent.putExtra("meterInterval",5);
        startForegroundService(serviceIntent);
    }

    public void managePermissions() {
        String[] PERMISSIONS = {
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
                Manifest.permission.CAMERA
        };

        if (!hasPermissions(getApplicationContext(), PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
        }

        PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(POWER_SERVICE);
        String packageName = getPackageName();
        Intent ii = new Intent();
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            ii.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            ii.setData(Uri.parse("package:" + packageName));
            startActivity(ii);
        }
    }

    public void getWifiCount() {
        Handler handler = new Handler();
        Thread cntt = new Thread() {
            @Override
            public void run() {
                TextView wificount = findViewById(R.id.wificount);
                Document doc;
                String a = "";
                try {
                    doc = Jsoup.connect("https://sont.sytes.net/wifilocator/wifi_count.php").get();
                    a = doc.html();

                    String temp_a = a;
                    handler.post(new Runnable() {
                        public void run() {
                            wificount.setText(SontHelper.getPlainText(
                                    temp_a
                            ));
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        };
        cntt.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent i = new Intent(getApplicationContext(), BackgroundService.class);
        startForegroundService(i);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("PERMISSION_CHECK", "MISSING PERMISSION NOW GRANTED: " + permission);
                    return false;
                }
            }
        }
        return true;
    }

    public void getChart_timer_updated(String path) {
        AndroidNetworking.get(path)
                .setTag("chart_auto")
                .setPriority(Priority.LOW)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            LineChart newchart = findViewById(R.id.newchart);
                            List<Entry> entries = new ArrayList<Entry>();

                            Map<Integer, Integer> hours = new HashMap<Integer, Integer>();
                            for (int i = 0; i < 24; i++) {
                                hours.put(i, 0);
                            }
                            Map<Integer, Integer> values = new HashMap<Integer, Integer>();
                            Map<Integer, Integer> combined = new HashMap<Integer, Integer>(hours);

                            String str = response;
                            String[] lines = str.trim().split("\\r?\\n");
                            try {
                                for (String line : lines) {
                                    String[] words = line.trim().split("\\s+");
                                    values.put(Integer.valueOf(words[0]), Integer.valueOf(words[1]));
                                }
                                combined.putAll(values);
                                for (Map.Entry<Integer, Integer> entry : combined.entrySet()) {
                                    int key = entry.getKey();
                                    int value = entry.getValue();
                                    entries.add(new Entry(key, value));
                                }
                            } catch (Exception e) {
                            }

                            LineDataSet dataSet = new LineDataSet(entries, "Stats");

                            dataSet.setDrawFilled(true);
                            dataSet.setDrawHighlightIndicators(true);
                            dataSet.setHighlightLineWidth(3f);
                            dataSet.setDrawValues(true);
                            dataSet.setValueTextSize(13);
                            dataSet.setHighlightEnabled(false);
                            dataSet.setDrawHighlightIndicators(false);
                            dataSet.setLineWidth(1);

                            dataSet.setValueFormatter(new CustomFormatter());
                            LineData lineData = new LineData(dataSet);
                            lineData.setValueFormatter(new CustomFormatter());

                            dataSet.setDrawCircles(false);
                            newchart.setDrawGridBackground(false);
                            newchart.setDrawBorders(false);
                            YAxis leftAxis = newchart.getAxisLeft();
                            leftAxis.setEnabled(false);
                            YAxis rightAxis = newchart.getAxisRight();
                            rightAxis.setEnabled(false);
                            XAxis xAxis = newchart.getXAxis();
                            xAxis.setEnabled(false);
                            Legend legend = newchart.getLegend();
                            legend.setEnabled(false);

                            newchart.getAxisLeft().setTextColor(Color.parseColor("#D81B60"));
                            newchart.getAxisRight().setTextColor(Color.parseColor("#D81B60"));
                            newchart.getXAxis().setTextColor(Color.parseColor("#D81B60"));
                            legend.setTextColor(Color.parseColor("#D81B60"));
                            lineData.setValueTextColor(Color.parseColor("#D81B60"));
                            dataSet.setColor(Color.parseColor("#D81B60"));

                            newchart.setData(lineData);
                            newchart.setDrawBorders(false);
                            newchart.getAxisRight().setDrawGridLines(false);
                            newchart.getAxisLeft().setDrawGridLines(false);

                            newchart.getXAxis().setDrawGridLines(false);
                            newchart.getXAxis().setDrawLabels(false);

                            newchart.getDescription().setEnabled(false);
                            newchart.getLegend().setEnabled(false);
                            newchart.setScaleEnabled(false);
                            newchart.setPinchZoom(false);
                            newchart.invalidate();
                            //newchart.animateX(1000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onError(ANError anError) {
                        LineChart newchart = findViewById(R.id.newchart);
                        newchart.clear();
//                        newchart.clearValues();
                        SontHelper.vibrate(getApplicationContext());
                    }
                });

    }

    public void getChart_timer_new(String path) {

        AndroidNetworking.get(path)
                .setTag("chart_auto")
                .setPriority(Priority.LOW)
                .build()
                .getAsString(new StringRequestListener() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            LineChart newchart = findViewById(R.id.newchart2);
                            List<Entry> entries = new ArrayList<Entry>();

                            Map<Integer, Integer> hours = new HashMap<Integer, Integer>();
                            for (int i = 0; i <= 24; i++) {
                                hours.put(i, 0);
                            }
                            Map<Integer, Integer> values = new HashMap<Integer, Integer>();
                            Map<Integer, Integer> combined = new HashMap<Integer, Integer>(hours);

                            String str = response;

                            String[] lines = str.trim().split("\\r?\\n");
                            try {
                                for (String line : lines) {
                                    String[] words = line.trim().split("\\s+");
                                    values.put(Integer.valueOf(words[0]), Integer.valueOf(words[1]));
                                }
                                combined.putAll(values);
                                for (Map.Entry<Integer, Integer> entry : combined.entrySet()) {
                                    int key = entry.getKey();
                                    int value = entry.getValue();
                                    entries.add(new Entry(key, value));
                                }
                            } catch (Exception e) {
                            }


                            LineDataSet dataSet = new LineDataSet(entries, "Stats");

                            dataSet.setDrawFilled(true);
                            dataSet.setDrawHighlightIndicators(true);
                            dataSet.setHighlightLineWidth(3f);
                            dataSet.setDrawValues(true);
                            dataSet.setLineWidth(1);
                            dataSet.setValueTextSize(13);
                            dataSet.setHighlightEnabled(false);
                            dataSet.setDrawHighlightIndicators(false);

                            dataSet.setDrawCircles(false);
                            newchart.setDrawGridBackground(false);
                            newchart.setDrawBorders(false);
                            YAxis leftAxis = newchart.getAxisLeft();
                            leftAxis.setEnabled(false);
                            YAxis rightAxis = newchart.getAxisRight();
                            rightAxis.setEnabled(false);
                            XAxis xAxis = newchart.getXAxis();
                            xAxis.setEnabled(false);
                            Legend legend = newchart.getLegend();
                            legend.setEnabled(false);

                            newchart.getAxisLeft().setTextColor(Color.parseColor("#D81B60"));
                            newchart.getAxisRight().setTextColor(Color.parseColor("#D81B60"));
                            newchart.getXAxis().setTextColor(Color.parseColor("#D81B60"));
                            legend.setTextColor(Color.parseColor("#D81B60"));
                            dataSet.setValueTextColor(Color.parseColor("#D81B60"));
                            dataSet.setColor(Color.parseColor("#D81B60"));

                            dataSet.setValueFormatter(new CustomFormatter());
                            LineData lineData = new LineData(dataSet);
                            lineData.setValueFormatter(new CustomFormatter());

                            newchart.setData(lineData);
                            newchart.setDrawBorders(false);
                            newchart.getAxisRight().setDrawGridLines(false);
                            newchart.getAxisLeft().setDrawGridLines(false);

                            newchart.getXAxis().setDrawGridLines(false);
                            newchart.getXAxis().setDrawLabels(false);

                            newchart.getDescription().setEnabled(false);
                            newchart.getLegend().setEnabled(false);
                            newchart.setScaleEnabled(false);
                            newchart.setPinchZoom(false);
                            newchart.invalidate();
                            //newchart.animateX(500);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onError(ANError anError) {
                        LineChart newchart = findViewById(R.id.newchart2);
                        newchart.clear();
                        //newchart.clearValues();
                        SontHelper.vibrate(getApplicationContext());
                    }
                });

    }

    public void batteryOptimization() {
        for (Intent intent : POWERMANAGER_INTENTS)
            if (getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                startActivity(intent);
                break;
            }
        for (Intent intent : POWERMANAGER_INTENTS)
            if (getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                // show dialog to ask user action
                break;
            }

    }

    private static final Intent[] POWERMANAGER_INTENTS = {
            new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            new Intent().setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
            new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            new Intent().setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            new Intent().setComponent(new ComponentName("com.htc.pitroad", "com.htc.pitroad.landingpage.activity.LandingPageActivity")),
            new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity")),
            new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            new Intent().setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
    };
}

