package com.example.smartstudy.schedule.reminder;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.smartstudy.R;
import com.example.smartstudy.schedule.ui.ScheduleEditActivity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ReminderNotifications {
    public static final String CHANNEL = "schedule_lessons";

    private ReminderNotifications() {
    }

    public static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.reminder_channel_description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    public static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false;
        }
        NotificationChannel channel = context.getSystemService(NotificationManager.class)
                .getNotificationChannel(CHANNEL);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    public static boolean canUseExactAlarms(Context context) {
        return Build.VERSION.SDK_INT < 31
                || context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    static boolean post(Context context, ReminderPlan plan, long now) {
        if (!canPost(context)) {
            return false;
        }
        ZoneId zone = ZoneId.of(plan.schedule.getTimeZoneId());
        DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm, dd/MM").withZone(zone);
        String room = plan.room == null || plan.room.trim().isEmpty()
                ? context.getString(R.string.schedule_no_room) : plan.room;
        String body = format.format(Instant.ofEpochMilli(plan.startMillis)) + " · " + room
                + " · " + zone.getId();
        String title = context.getString(now < plan.startMillis
                ? R.string.reminder_upcoming_title : R.string.reminder_started_title,
                plan.schedule.getSubjectName());
        Intent intent = new Intent(context, ScheduleEditActivity.class);
        intent.setData(Uri.parse("smartstudy://schedule-open/" + plan.schedule.getId()));
        intent.putExtra(ScheduleEditActivity.EXTRA_ID, plan.schedule.getId());
        PendingIntent open = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_schedule_notification)
                .setColor(ContextCompat.getColor(context, R.color.PeriwinkleBlue))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        context.getSystemService(NotificationManager.class).notify(tag(plan), 0, builder.build());
        return true;
    }

    private static String tag(ReminderPlan plan) {
        return "schedule:" + plan.schedule.getId() + ":" + plan.key();
    }

    static void cancelForSchedule(Context context, long id) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        String prefix = "schedule:" + id + ":";
        for (StatusBarNotification notification : manager.getActiveNotifications()) {
            if (notification.getTag() != null && notification.getTag().startsWith(prefix)) {
                manager.cancel(notification.getTag(), notification.getId());
            }
        }
    }
}
