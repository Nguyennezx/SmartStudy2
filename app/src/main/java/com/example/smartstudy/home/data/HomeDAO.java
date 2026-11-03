package com.example.smartstudy.home.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.smartstudy.core.database.DatabaseHelper;
import com.example.smartstudy.schedule.data.ScheduleCalendar;
import com.example.smartstudy.schedule.data.ScheduleDAO;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** C6: Cursor, SQL có tham số, chỉ đọc database chung và chạy ngoài UI thread. */
public final class HomeDAO {
    private final DatabaseHelper helper;
    public HomeDAO(Context context) { helper = DatabaseHelper.getInstance(context); }
    public HomeSnapshot load(Instant now) {
        SQLiteDatabase db = helper.getReadableDatabase();
        db.beginTransactionNonExclusive();
        try {
            ScheduleDAO.CalendarData calendar = new ScheduleDAO(helper).getCalendarData();
            ZoneId zone = ZoneId.of(calendar.timeZoneId);
            LocalDate day = now.atZone(zone).toLocalDate();
            long dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli();
            long dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            long horizon = day.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli();
            List<HomeSnapshot.Task> tasks = new ArrayList<>();
            try (Cursor c = db.rawQuery("SELECT a.id,a.title,s.name,a.description,a.due_at_ms "
                    + "FROM assignments a JOIN subjects s ON s.id=a.subject_id "
                    + "WHERE a.status<>'done' AND s.is_archived=0 AND a.due_at_ms IS NOT NULL "
                    + "AND a.due_at_ms<? ORDER BY a.due_at_ms,a.id LIMIT 3",
                    new String[]{String.valueOf(horizon)})) {
                while (c.moveToNext()) tasks.add(new HomeSnapshot.Task(c.getLong(0), c.getString(1),
                        c.getString(2), c.getString(3), c.getLong(4)));
            }
            long seconds;
            // Quy ước chung: focus_seconds đã ghi của phiên bắt đầu trong ngày.
            // Không tự tính thời gian đang chạy, không bỏ lịch sử môn lưu trữ.
            try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(focus_seconds),0) FROM study_sessions "
                    + "WHERE started_at_ms>=? AND started_at_ms<?",
                    new String[]{String.valueOf(dayStart), String.valueOf(dayEnd)})) {
                c.moveToFirst(); seconds = c.getLong(0);
            }
            Map<Long, String> teachers = new HashMap<>();
            try (Cursor c = db.rawQuery("SELECT id,teacher_name FROM subjects WHERE is_archived=0", null)) {
                while (c.moveToNext()) teachers.put(c.getLong(0), c.getString(1));
            }
            HomeSnapshot result = new HomeSnapshot(day, zone, ScheduleCalendar.onDate(
                    ScheduleCalendar.buildWeek(day, zone, calendar.schedules, calendar.exceptions), day),
                    tasks, teachers, seconds);
            db.setTransactionSuccessful();
            return result;
        } finally { db.endTransaction(); }
    }
}
