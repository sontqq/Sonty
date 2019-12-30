package com.sontme.esp.sonty;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class receiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.hasExtra("29293")) {
            try {
                if (BackgroundService.headoverlay_mHeadView == null) {
                    BackgroundService.initHeadOverlay(context);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (intent.getAction() == Intent.ACTION_BOOT_COMPLETED) {
            Intent i = new Intent(context, BackgroundService.class);
            context.startForegroundService(i);
        }
        if (intent.getAction() == "exit") {
            SontHelper.vibrate(context);
            context.stopService(intent);
            context.stopService(new Intent(context, BackgroundService.class));
            context.stopService(new Intent(context, MainActivity.class));
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(2);
        }
        if (intent.getAction() == "start") {
            Toast.makeText(context, "STARTED!", Toast.LENGTH_SHORT).show();
        }
        if (intent.getAction() == "pause") {
            Toast.makeText(context, "PAUSE!", Toast.LENGTH_SHORT).show();
        }
        if (intent.getAction() == "start_bl") {
            Toast.makeText(context, "START!", Toast.LENGTH_SHORT).show();

        }
        if (intent.getAction() == "stop_bl") {
            Toast.makeText(context, "STOP!!", Toast.LENGTH_SHORT).show();
            BackgroundService b = new BackgroundService();
            b.stopBLscanFromService(context);
        }
    }
}
