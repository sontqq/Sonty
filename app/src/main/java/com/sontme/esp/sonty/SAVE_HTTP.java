package com.sontme.esp.sonty;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.StringRequestListener;

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
