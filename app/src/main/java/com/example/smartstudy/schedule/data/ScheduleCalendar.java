package com.example.smartstudy.schedule.data;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Chuyển lịch lặp và ngoại lệ thành các buổi thực tế trong tuần. Không phụ thuộc Android. */
public final class ScheduleCalendar {
    private ScheduleCalendar() {
    }

    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static List<ScheduleOccurrence> buildWeek(LocalDate selected, ZoneId displayZone,
                                                     List<Schedule> schedules,
                                                     Map<Long, List<ScheduleException>> exceptions) {
        LocalDate first = weekStart(selected);
        LocalDate end = first.plusWeeks(1);
        Instant windowStart = first.atStartOfDay(displayZone).toInstant();
        Instant windowEnd = end.atStartOfDay(displayZone).toInstant();
        List<ScheduleOccurrence> result = new ArrayList<>();
        for (Schedule schedule : schedules) {
            ZoneId sourceZone = ZoneId.of(schedule.getTimeZoneId());
            List<ScheduleException> overrides = exceptions.getOrDefault(
                    schedule.getId(), Collections.emptyList());
            LocalDate from = windowStart.atZone(sourceZone).toLocalDate().minusDays(1);
            LocalDate to = windowEnd.atZone(sourceZone).toLocalDate();
            LocalDate validFrom = LocalDate.parse(schedule.getValidFrom());
            LocalDate validTo = LocalDate.parse(schedule.getValidTo());
            if (from.isBefore(validFrom)) {
                from = validFrom;
            }
            if (to.isAfter(validTo)) {
                to = validTo;
            }
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (date.getDayOfWeek().getValue() != schedule.getDayOfWeek()) {
                    continue;
                }
                boolean replaced = false;
                for (ScheduleException override : overrides) {
                    if (date.toString().equals(override.originalDate)) {
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    add(result, schedule, date, schedule.getStartMinute(), schedule.getEndMinute(),
                            schedule.getRoom(), false, date, sourceZone, displayZone, first, end);
                }
            }
            for (ScheduleException override : overrides) {
                if (override.newDate == null) {
                    continue;
                }
                LocalDate original = LocalDate.parse(override.originalDate);
                // Không hiển thị ngoại lệ có ngày gốc không thuộc chuỗi lịch.
                if (original.isBefore(validFrom) || original.isAfter(validTo)
                        || original.getDayOfWeek().getValue() != schedule.getDayOfWeek()) {
                    continue;
                }
                String room = override.newRoom == null ? schedule.getRoom() : override.newRoom;
                add(result, schedule, LocalDate.parse(override.newDate), override.startMinute,
                        override.endMinute, room, true, original, sourceZone, displayZone, first, end);
            }
        }
        result.sort(Comparator.comparing(ScheduleOccurrence::getDate)
                .thenComparingInt(ScheduleOccurrence::getStartMinute)
                .thenComparingInt(ScheduleOccurrence::getEndMinute)
                .thenComparingLong(item -> item.getSchedule().getId()));
        return result;
    }

    private static void add(List<ScheduleOccurrence> result, Schedule schedule, LocalDate sourceDate,
                            int start, int end, String room, boolean moved, LocalDate originalDate,
                            ZoneId sourceZone,
                            ZoneId displayZone, LocalDate first, LocalDate weekEnd) {
        Instant starts = sourceDate.atStartOfDay().plusMinutes(start).atZone(sourceZone).toInstant();
        Instant ends = sourceDate.atStartOfDay().plusMinutes(end).atZone(sourceZone).toInstant();
        for (LocalDate day = first; day.isBefore(weekEnd); day = day.plusDays(1)) {
            Instant dayStart = day.atStartOfDay(displayZone).toInstant();
            Instant dayEnd = day.plusDays(1).atStartOfDay(displayZone).toInstant();
            if (!starts.isBefore(dayEnd) || !ends.isAfter(dayStart)) {
                continue;
            }
            ZonedDateTime localStart = starts.atZone(displayZone);
            ZonedDateTime localEnd = ends.atZone(displayZone);
            int startMinute = starts.isBefore(dayStart) ? 0
                    : localStart.getHour() * 60 + localStart.getMinute();
            int endMinute = !ends.isBefore(dayEnd) ? 1440
                    : localEnd.getHour() * 60 + localEnd.getMinute();
            result.add(new ScheduleOccurrence(schedule, day, startMinute, endMinute, room, moved,
                    schedule.getId() + "/" + originalDate));
        }
    }

    public static List<ScheduleOccurrence> onDate(List<ScheduleOccurrence> week, LocalDate date) {
        List<ScheduleOccurrence> result = new ArrayList<>();
        for (ScheduleOccurrence item : week) {
            if (item.getDate().equals(date)) {
                result.add(item);
            }
        }
        return result;
    }
}
