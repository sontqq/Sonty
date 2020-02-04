package com.sontme.esp.sonty;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class BetaService extends Service {

    public void onCreate() {
    }

    public void showOngoing() {
        Intent notificationIntent = new Intent(getApplicationContext(), receiver.class);
        notificationIntent.putExtra("29294", "29294");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                getApplicationContext(),
                29294,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        Intent intent = new Intent(getApplicationContext(), receiver.class);
        intent.setAction("test");
        PendingIntent pi = PendingIntent.getBroadcast(getApplicationContext(),
                2, intent, PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(getApplicationContext(), "sonty")
                .setContentTitle("Sonty BETA Service")
                .setContentText("Hello?")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setSmallIcon(R.drawable.service)
                .setContentIntent(pendingIntent)
                .setChannelId("sonty")
                .addAction(R.drawable.service, "TEST",
                        pi)
                .build();

        startForeground(56, notification);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
