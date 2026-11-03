package com.example.smartstudy.home.data;

import com.example.smartstudy.schedule.data.ScheduleOccurrence;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** Dữ liệu chỉ đọc; Home không sở hữu bài tập hoặc phiên học. */
public final class HomeSnapshot {
    public final LocalDate date;
    public final ZoneId zone;
    public final List<ScheduleOccurrence> lessons;
    public final List<Task> tasks;
    public final Map<Long, String> teachers;
    public final long focusSeconds;
    public HomeSnapshot(LocalDate date, ZoneId zone, List<ScheduleOccurrence> lessons,
                        List<Task> tasks, Map<Long, String> teachers, long focusSeconds) {
        this.date = date; this.zone = zone; this.lessons = lessons;
        this.tasks = tasks; this.teachers = teachers; this.focusSeconds = focusSeconds;
    }
    public static final class Task {
        public final long id, dueMillis;
        public final String title, subject, description;
        public Task(long id, String title, String subject, String description, long dueMillis) {
            this.id = id; this.title = title; this.subject = subject;
            this.description = description; this.dueMillis = dueMillis;
        }
    }
}
