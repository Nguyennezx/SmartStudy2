package com.example.smartstudy.schedule.reminder;

import com.example.smartstudy.schedule.data.Schedule;
import com.example.smartstudy.schedule.data.ScheduleException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;

/** Tính lần nhắc tiếp theo bằng Java thuần, không dùng timer đếm liên tục. */
public final class ReminderPlanner {
    private ReminderPlanner() {
    }

    public static ReminderPlan next(Schedule schedule, List<ScheduleException> exceptions,
                                    Instant now, Set<String> delivered) {
        if (schedule.getRemindBeforeMinutes() == null) {
            return null;
        }
        ZoneId zone = ZoneId.of(schedule.getTimeZoneId());
        LocalDate validFrom = LocalDate.parse(schedule.getValidFrom());
        LocalDate validTo = LocalDate.parse(schedule.getValidTo());
        LocalDate first = now.atZone(zone).toLocalDate().minusDays(1);
        if (first.isBefore(validFrom)) {
            first = validFrom;
        }
        first = first.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(schedule.getDayOfWeek())));
        ReminderPlan best = null;
        for (LocalDate day = first; !day.isAfter(validTo); day = day.plusWeeks(1)) {
            boolean replaced = false;
            for (ScheduleException exception : exceptions) {
                if (exception.originalDate.equals(day.toString())) {
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                ReminderPlan plan = make(schedule, day, day, schedule.getStartMinute(),
                        schedule.getEndMinute(), schedule.getRoom(), zone);
                if (eligible(plan, now, delivered)) {
                    best = plan;
                    break;
                }
            }
        }
        for (ScheduleException exception : exceptions) {
            if (exception.newDate == null) {
                continue;
            }
            LocalDate original = LocalDate.parse(exception.originalDate);
            if (original.isBefore(validFrom) || original.isAfter(validTo)
                    || original.getDayOfWeek().getValue() != schedule.getDayOfWeek()) {
                continue;
            }
            String room = exception.newRoom == null ? schedule.getRoom() : exception.newRoom;
            ReminderPlan plan = make(schedule, original, LocalDate.parse(exception.newDate),
                    exception.startMinute, exception.endMinute, room, zone);
            if (eligible(plan, now, delivered) && (best == null
                    || plan.triggerMillis < best.triggerMillis)) {
                best = plan;
            }
        }
        return best;
    }

    private static ReminderPlan make(Schedule schedule, LocalDate original, LocalDate actual,
                                     int start, int end, String room, ZoneId zone) {
        long startMillis = actual.atStartOfDay().plusMinutes(start).atZone(zone).toInstant().toEpochMilli();
        long endMillis = actual.atStartOfDay().plusMinutes(end).atZone(zone).toInstant().toEpochMilli();
        return new ReminderPlan(schedule, original, startMillis, endMillis, room);
    }

    private static boolean eligible(ReminderPlan plan, Instant now, Set<String> delivered) {
        return now.toEpochMilli() < plan.expiresMillis() && !delivered.contains(plan.key());
    }
}
