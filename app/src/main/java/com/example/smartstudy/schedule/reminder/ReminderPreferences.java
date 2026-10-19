package com.example.smartstudy.schedule.reminder;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** SharedPreferences theo chương 5: chỉ giữ trạng thái chạy, SQLite vẫn là nguồn lịch chính. */
final class ReminderPreferences {
    private final SharedPreferences preferences;

    ReminderPreferences(Context context) {
        preferences = context.getSharedPreferences("schedule_reminders_runtime", Context.MODE_PRIVATE);
    }

    Set<String> knownIds() {
        return new HashSet<>(preferences.getStringSet("known_ids", new HashSet<>()));
    }

    void setKnownIds(Set<String> ids) {
        if (!preferences.edit().putStringSet("known_ids", new HashSet<>(ids)).commit()) {
            throw new IllegalStateException("Không lưu được danh sách nhắc.");
        }
    }

    Set<String> delivered(long id) {
        return new HashSet<>(preferences.getStringSet("delivered:" + id, new HashSet<>()));
    }

    void markDelivered(ReminderPlan plan) {
        Set<String> keys = delivered(plan.schedule.getId());
        keys.add(plan.key());
        // Giới hạn sổ đã gửi của mỗi chuỗi, giữ các buổi có thời điểm bắt đầu gần nhất.
        List<String> sorted = new ArrayList<>(keys);
        sorted.sort((left, right) -> Long.compare(startMillis(left), startMillis(right)));
        while (sorted.size() > 256) {
            sorted.remove(0);
        }
        if (!preferences.edit().putStringSet("delivered:" + plan.schedule.getId(),
                new HashSet<>(sorted)).commit()) {
            throw new IllegalStateException("Không lưu được trạng thái đã nhắc.");
        }
    }

    private long startMillis(String key) {
        return Long.parseLong(key.split("/")[3]);
    }

    void remove(long id) {
        preferences.edit().remove("delivered:" + id).commit();
    }

    boolean hasAskedPermission() {
        return preferences.getBoolean("asked_notifications", false);
    }

    void markAskedPermission() {
        preferences.edit().putBoolean("asked_notifications", true).apply();
    }
}
