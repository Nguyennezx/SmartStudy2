package com.example.smartstudy.home.data;

import com.example.smartstudy.schedule.data.ScheduleOccurrence;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Chọn buổi đang học/tiếp theo, không lặp thẻ nổi bật trong danh sách. */
public final class TodayAgenda {
    public final ScheduleOccurrence featured;
    public final List<ScheduleOccurrence> remaining;
    private TodayAgenda(ScheduleOccurrence featured, List<ScheduleOccurrence> remaining) {
        this.featured = featured; this.remaining = remaining;
    }
    public static TodayAgenda at(List<ScheduleOccurrence> lessons, Instant now, ZoneId zone) {
        LocalDate today = now.atZone(zone).toLocalDate();
        List<ScheduleOccurrence> upcoming = new ArrayList<>();
        for (ScheduleOccurrence lesson : lessons) {
            if (lesson.getDate().equals(today) && end(lesson, zone).isAfter(now)) upcoming.add(lesson);
        }
        upcoming.sort(Comparator.comparingInt(ScheduleOccurrence::getStartMinute)
                .thenComparingInt(ScheduleOccurrence::getEndMinute)
                .thenComparingLong(item -> item.getSchedule().getId()));
        ScheduleOccurrence featured = upcoming.isEmpty() ? null : upcoming.remove(0);
        return new TodayAgenda(featured, upcoming);
    }
    public static Instant start(ScheduleOccurrence lesson, ZoneId zone) {
        return lesson.getDate().atStartOfDay().plusMinutes(lesson.getStartMinute()).atZone(zone).toInstant();
    }
    public static Instant end(ScheduleOccurrence lesson, ZoneId zone) {
        return lesson.getDate().atStartOfDay().plusMinutes(lesson.getEndMinute()).atZone(zone).toInstant();
    }
}
