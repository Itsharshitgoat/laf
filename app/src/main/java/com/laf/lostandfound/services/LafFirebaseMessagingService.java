package com.laf.lostandfound.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.laf.lostandfound.R;
import com.laf.lostandfound.activities.ItemDetailActivity;
import com.laf.lostandfound.activities.MainActivity;
import com.laf.lostandfound.repositories.AuthRepository;
import com.laf.lostandfound.utils.Constants;

import java.util.Map;
import java.util.Random;

/**
 * Handles incoming Firebase Cloud Messaging push notifications for
 * match suggestions, claim alerts, and report updates.
 */
public class LafFirebaseMessagingService extends FirebaseMessagingService {

    public static final String CHANNEL_ID = "laf_notifications_channel";
    public static final String CHANNEL_NAME = "Lost & Found Alerts";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        AuthRepository.getInstance().updateFcmToken(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = null;
        String body = null;
        String itemId = null;

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        Map<String, String> data = remoteMessage.getData();
        if (data != null && !data.isEmpty()) {
            if (data.containsKey("title")) title = data.get("title");
            if (data.containsKey("message")) body = data.get("message");
            if (data.containsKey("body") && body == null) body = data.get("body");
            if (data.containsKey("itemId")) itemId = data.get("itemId");
        }

        if (title == null || title.trim().isEmpty()) {
            title = getString(R.string.app_name);
        }
        if (body == null || body.trim().isEmpty()) {
            body = "You have a new update on Lost & Found.";
        }

        showSystemNotification(title, body, itemId);
    }

    private void showSystemNotification(String title, String body, String itemId) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for matches, claims, and status updates");
            channel.enableLights(true);
            channel.enableVibration(true);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent;
        if (itemId != null && !itemId.trim().isEmpty()) {
            intent = new Intent(this, ItemDetailActivity.class);
            intent.putExtra(Constants.EXTRA_ITEM_ID, itemId.trim());
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                new Random().nextInt(10000),
                intent,
                flags
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, builder.build());
    }
}
