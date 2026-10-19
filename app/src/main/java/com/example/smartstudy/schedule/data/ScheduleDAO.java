package com.example.smartstudy.schedule.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.smartstudy.core.database.DatabaseHelper;
import com.example.smartstudy.schedule.reminder.ReminderCoordinator;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** SQL đặt trong DAO theo chương 6; mọi lời gọi thực hiện trên luồng nền. */
public class ScheduleDAO {
    private final DatabaseHelper helper;
    private final Context appContext;
    private static final String SELECT = "SELECT s.*, sub.name AS subject_name, "
            + "sub.color_hex FROM schedules s JOIN subjects sub ON sub.id=s.subject_id ";

    public ScheduleDAO(Context context) {
        helper = DatabaseHelper.getInstance(context);
        appContext = context.getApplicationContext();
    }

    ScheduleDAO(DatabaseHelper helper) {
        this.helper = helper;
        appContext = null;
    }

    public List<Schedule> getAll() {
        return read(SELECT + "WHERE s.is_archived=0 AND sub.is_archived=0 "
                + "ORDER BY s.day_of_week, s.start_minute, s.id", null);
    }

    public Schedule getById(long id) {
        List<Schedule> schedules = read(SELECT + "WHERE s.id=? AND s.is_archived=0",
                new String[]{String.valueOf(id)});
        return schedules.isEmpty() ? null : schedules.get(0);
    }

    public String getDefaultTimeZone() {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT time_zone_id FROM app_settings WHERE id=1", null)) {
            if (!cursor.moveToFirst()) {
                throw new IllegalStateException("Thiếu cài đặt múi giờ.");
            }
            return cursor.getString(0);
        }
    }

    public static class CalendarData {
        public final List<Schedule> schedules;
        public final Map<Long, List<ScheduleException>> exceptions;
        public final String timeZoneId;
        public final boolean remindersEnabled;

        CalendarData(List<Schedule> schedules, Map<Long, List<ScheduleException>> exceptions,
                     String timeZoneId, boolean remindersEnabled) {
            this.schedules = schedules;
            this.exceptions = exceptions;
            this.timeZoneId = timeZoneId;
            this.remindersEnabled = remindersEnabled;
        }
    }

    /** Đọc lịch, ngoại lệ và múi giờ trong cùng một snapshot SQLite. */
    public CalendarData getCalendarData() {
        SQLiteDatabase db = helper.getReadableDatabase();
        db.beginTransactionNonExclusive();
        try {
            boolean enabled;
            try (Cursor cursor = db.rawQuery("SELECT reminders_enabled FROM app_settings WHERE id=1", null)) {
                if (!cursor.moveToFirst()) {
                    throw new IllegalStateException("Thiếu cài đặt nhắc lịch.");
                }
                enabled = cursor.getInt(0) == 1;
            }
            CalendarData data = new CalendarData(getAll(), getExceptions(), getDefaultTimeZone(), enabled);
            db.setTransactionSuccessful();
            return data;
        } finally {
            db.endTransaction();
        }
    }

    private List<Schedule> read(String sql, String[] args) {
        List<Schedule> schedules = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) {
                int reminderIndex = c.getColumnIndexOrThrow("remind_before_minutes");
                Schedule schedule = new Schedule(
                        c.getLong(c.getColumnIndexOrThrow("id")),
                        c.getLong(c.getColumnIndexOrThrow("subject_id")),
                        c.getInt(c.getColumnIndexOrThrow("day_of_week")),
                        c.getInt(c.getColumnIndexOrThrow("start_minute")),
                        c.getInt(c.getColumnIndexOrThrow("end_minute")),
                        c.getString(c.getColumnIndexOrThrow("valid_from")),
                        c.getString(c.getColumnIndexOrThrow("valid_to")),
                        c.getString(c.getColumnIndexOrThrow("time_zone_id")),
                        c.getString(c.getColumnIndexOrThrow("room")),
                        c.getString(c.getColumnIndexOrThrow("note")),
                        c.isNull(reminderIndex) ? null : c.getInt(reminderIndex));
                schedule.setSubjectDisplay(c.getString(c.getColumnIndexOrThrow("subject_name")),
                        c.getString(c.getColumnIndexOrThrow("color_hex")));
                schedules.add(schedule);
            }
        }
        return schedules;
    }

    public long save(Schedule schedule) {
        ScheduleRules.validate(schedule);
        SQLiteDatabase db = helper.getWritableDatabase();
        long id = schedule.getId();
        db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery("SELECT id FROM subjects WHERE id=? AND is_archived=0",
                    new String[]{String.valueOf(schedule.getSubjectId())})) {
                if (!c.moveToFirst()) {
                    throw new IllegalArgumentException("Môn đã lưu trữ hoặc không còn tồn tại. Vui lòng chọn lại.");
                }
            }
            long created = System.currentTimeMillis();
            if (schedule.getId() > 0) {
                Schedule old = getById(schedule.getId());
                if (old == null) {
                    throw new IllegalArgumentException("Lịch học không còn tồn tại.");
                }
                try (Cursor c = db.rawQuery("SELECT 1 FROM schedule_exceptions WHERE schedule_id=? LIMIT 1",
                        new String[]{String.valueOf(schedule.getId())})) {
                    if (c.moveToFirst()) {
                        throw new IllegalArgumentException("Lịch có buổi đổi/hủy riêng. Chưa hỗ trợ sửa cả chuỗi ở bước này.");
                    }
                }
                try (Cursor c = db.rawQuery("SELECT created_at_ms FROM schedules WHERE id=?",
                        new String[]{String.valueOf(schedule.getId())})) {
                    c.moveToFirst();
                    created = c.getLong(0);
                }
            }
            ScheduleRules.Conflict conflict = ScheduleRules.findConflict(schedule, getAll(), getExceptions());
            if (conflict != null) {
                String date = conflict.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                throw new IllegalArgumentException("Trùng môn " + conflict.schedule.getSubjectName()
                        + " ngày " + date + ", " + ScheduleRules.formatMinute(conflict.startMinute)
                        + "–" + ScheduleRules.formatMinute(conflict.endMinute)
                        + " (" + conflict.schedule.getTimeZoneId() + ").");
            }
            ContentValues values = new ContentValues();
            values.put("subject_id", schedule.getSubjectId());
            values.put("day_of_week", schedule.getDayOfWeek());
            values.put("start_minute", schedule.getStartMinute());
            values.put("end_minute", schedule.getEndMinute());
            values.put("valid_from", schedule.getValidFrom());
            values.put("valid_to", schedule.getValidTo());
            values.put("time_zone_id", schedule.getTimeZoneId());
            values.put("room", schedule.getRoom() == null ? null : schedule.getRoom().trim());
            values.put("note", schedule.getNote() == null ? null : schedule.getNote().trim());
            if (schedule.getRemindBeforeMinutes() == null) {
                values.putNull("remind_before_minutes");
            } else {
                values.put("remind_before_minutes", schedule.getRemindBeforeMinutes());
            }
            values.put("updated_at_ms", Math.max(created, System.currentTimeMillis()));
            if (id <= 0) {
                values.put("created_at_ms", created);
                id = db.insertOrThrow("schedules", null, values);
            } else {
                int changed = db.update("schedules", values, "id=?", new String[]{String.valueOf(id)});
                if (changed != 1) {
                    throw new IllegalStateException("Không cập nhật được lịch học.");
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (appContext != null) {
            ReminderCoordinator.scheduleChanged(appContext, id);
        }
        return id;
    }

    private Map<Long, List<ScheduleException>> getExceptions() {
        Map<Long, List<ScheduleException>> result = new HashMap<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT schedule_id, original_date, new_date, new_start_minute, new_end_minute, new_room "
                        + "FROM schedule_exceptions", null)) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                List<ScheduleException> items = result.get(id);
                if (items == null) {
                    items = new ArrayList<>();
                    result.put(id, items);
                }
                items.add(new ScheduleException(id, c.getString(1), c.getString(2),
                        c.isNull(3) ? null : c.getInt(3), c.isNull(4) ? null : c.getInt(4),
                        c.getString(5)));
            }
        }
        return result;
    }

    public boolean delete(long id) {
        boolean deleted = helper.getWritableDatabase().delete("schedules", "id=?",
                new String[]{String.valueOf(id)}) == 1;
        if (deleted && appContext != null) {
            ReminderCoordinator.scheduleChanged(appContext, id);
        }
        return deleted;
    }
}
