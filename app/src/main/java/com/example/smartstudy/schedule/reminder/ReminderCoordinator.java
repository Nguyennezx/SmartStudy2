package com.example.smartstudy.schedule.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.example.smartstudy.schedule.data.Schedule;
import com.example.smartstudy.schedule.data.ScheduleDAO;
import com.example.smartstudy.schedule.data.ScheduleException;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Một hàng đợi dùng chung để đồng bộ alarm, sổ đã gửi và thao tác hủy. */
public final class ReminderCoordinator {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    public static final String EXTRA_ID = "reminder_schedule_id";
    public static final String EXTRA_KEY = "reminder_occurrence_key";

    public interface Listener {
        void onResult(Status status);
    }

    public static class Status {
        public int upcoming;
        public boolean globallyEnabled;
        public boolean canPost;
        public boolean exact;
        public boolean failed;
    }

    private ReminderCoordinator() {
    }

    public static void refresh(Context context) {
        refresh(context, null);
    }

    public static void refresh(Context context, Listener listener) {
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            Status status = safeSync(app);
            if (listener != null) {
                new Handler(Looper.getMainLooper()).post(() -> listener.onResult(status));
            }
        });
    }

    public static void scheduleChanged(Context context, long id) {
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            try {
                cancelAlarm(app, id);
                ReminderNotifications.cancelForSchedule(app, id);
            } catch (Exception error) {
                android.util.Log.e("ScheduleReminder", "Không hủy được nhắc cũ", error);
            }
            safeSync(app);
        });
    }

    static void fromSystem(Context context, Runnable completion) {
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            try {
                safeSync(app);
            } finally {
                completion.run();
            }
        });
    }

    static void onAlarm(Context context, long id, String key, Runnable completion) {
        Context app = context.getApplicationContext();
        executor.execute(() -> {
            try {
                ReminderNotifications.createChannel(app);
                ScheduleDAO.CalendarData data = new ScheduleDAO(app).getCalendarData();
                if (data.remindersEnabled && ReminderNotifications.canPost(app)) {
                    ReminderPreferences preferences = new ReminderPreferences(app);
                    long now = System.currentTimeMillis();
                    for (Schedule schedule : data.schedules) {
                        if (schedule.getId() != id) {
                            continue;
                        }
                        ReminderPlan plan = ReminderPlanner.next(schedule,
                                data.exceptions.getOrDefault(id, Collections.emptyList()),
                                Instant.ofEpochMilli(now), preferences.delivered(id));
                        // Alarm cũ sau sửa/xóa không được gửi nội dung lỗi thời.
                        if (plan != null && plan.key().equals(key) && plan.triggerMillis <= now
                                && now < plan.expiresMillis()
                                && ReminderNotifications.post(app, plan, now)) {
                            preferences.markDelivered(plan);
                        }
                        break;
                    }
                }
                safeSync(app);
            } catch (Exception error) {
                android.util.Log.e("ScheduleReminder", "Không xử lý được lần nhắc", error);
                safeSync(app);
            } finally {
                completion.run();
            }
        });
    }

    private static Status safeSync(Context context) {
        try {
            return sync(context);
        } catch (Exception error) {
            android.util.Log.e("ScheduleReminder", "Không đồng bộ được nhắc lịch", error);
            Status status = new Status();
            status.failed = true;
            return status;
        }
    }

    private static Status sync(Context context) {
        ReminderNotifications.createChannel(context);
        ScheduleDAO.CalendarData data = new ScheduleDAO(context).getCalendarData();
        ReminderPreferences preferences = new ReminderPreferences(context);
        Status status = new Status();
        status.globallyEnabled = data.remindersEnabled;
        status.canPost = ReminderNotifications.canPost(context);
        status.exact = ReminderNotifications.canUseExactAlarms(context);
        Set<String> active = new HashSet<>();
        Instant now = Instant.now();
        for (Schedule schedule : data.schedules) {
            long id = schedule.getId();
            active.add(String.valueOf(id));
            List<ScheduleException> exceptions = data.exceptions.getOrDefault(id, Collections.emptyList());
            ReminderPlan plan = ReminderPlanner.next(schedule, exceptions, now, preferences.delivered(id));
            if (plan != null) {
                status.upcoming++;
            }
            boolean enabled = status.globallyEnabled && status.canPost
                    && schedule.getRemindBeforeMinutes() != null;
            if (enabled && plan != null) {
                setAlarm(context, plan, status.exact);
            } else {
                cancelAlarm(context, id);
            }
            if (!enabled) {
                ReminderNotifications.cancelForSchedule(context, id);
            }
        }
        for (String old : preferences.knownIds()) {
            if (!active.contains(old)) {
                long id = Long.parseLong(old);
                cancelAlarm(context, id);
                ReminderNotifications.cancelForSchedule(context, id);
                preferences.remove(id);
            }
        }
        preferences.setKnownIds(active);
        return status;
    }

    private static Intent alarmIntent(Context context, long id) {
        return new Intent(context, ScheduleAlarmReceiver.class)
                .setAction("com.example.smartstudy.SCHEDULE_REMINDER")
                .setData(Uri.parse("smartstudy://schedule-reminder/" + id));
    }

    private static void setAlarm(Context context, ReminderPlan plan, boolean exact) {
        Intent intent = alarmIntent(context, plan.schedule.getId());
        intent.putExtra(EXTRA_ID, plan.schedule.getId());
        intent.putExtra(EXTRA_KEY, plan.key());
        PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        long trigger = Math.max(plan.triggerMillis, System.currentTimeMillis() + 1000);
        if (exact) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
                return;
            } catch (SecurityException revoked) {
                // Người dùng có thể vừa thu hồi quyền trong khi đang lập alarm.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
    }

    private static void cancelAlarm(Context context, long id) {
        PendingIntent pending = PendingIntent.getBroadcast(context, 0, alarmIntent(context, id),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            context.getSystemService(AlarmManager.class).cancel(pending);
            pending.cancel();
        }
    }

    public static boolean hasAskedNotificationPermission(Context context) {
        return new ReminderPreferences(context).hasAskedPermission();
    }

    public static void markAskedNotificationPermission(Context context) {
        new ReminderPreferences(context).markAskedPermission();
    }
}
