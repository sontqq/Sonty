package com.sontme.esp.sonty;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
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
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);

            SontHelper.TimeElapsedUtil teu = new SontHelper.TimeElapsedUtil();
            teu.setStartTime(System.currentTimeMillis());

            setContentView(R.layout.activity_main);

            Button btn = findViewById(R.id.button);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        TextView textview = findViewById(R.id.textview);
                        if (BackgroundService.scanresult != null || BackgroundService.unique != null) {
                            textview.setText("Near: " + BackgroundService.scanresult.size() + " Unique: " + BackgroundService.unique.size());
                        }
                        SontHelper.vibrate(getApplicationContext());
                        getChart_timer_updated("https://sont.sytes.net/wifilocator/wifis_chart_updated.php");
                        getChart_timer_new("https://sont.sytes.net/wifilocator/wifis_chart_new.php");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            });

            SontHelper.createNotifGroup(getApplicationContext(), "sonty", "sonty");
            SontHelper.createNotificationChannel(getApplicationContext(), "sonty", "sonty");

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

            Intent serviceIntent = new Intent(getApplicationContext(), BackgroundService.class);
            startForegroundService(serviceIntent);

            //getChart_timer_updated("https://sont.sytes.net/wifilocator/wifis_chart_updated.php");
            //getChart_timer_new("https://sont.sytes.net/wifilocator/wifis_chart_new.php");

            Log.d("ACTIVITY_INITIALIZATION", "Elapsed time: " + teu.getElapsed());
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                            Log.d("Error_", e.getMessage());
                            //e.printStackTrace();
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
}

class CustomFormatter extends ValueFormatter {

    private DecimalFormat mFormat;

    public CustomFormatter() {
        //mFormat = new DecimalFormat();
        mFormat = new DecimalFormat("###,###,##0"); // use one decimal
        //mFormat = new DecimalFormat("#");
    }


    @Override
    public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {
        Log.d("chart_", "value: " + value);
        return mFormat.format(value) + " asd";
        /*if (value > 0) {
            return mFormat.format(value) + "asd";
        } else {
            return "" + "asd";
        }*/
    }
}
