package com.example.smartstudy.schedule.data;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Quy tắc ngày/giờ tách khỏi giao diện để kiểm thử được bằng Java. */
public final class ScheduleRules {
    private ScheduleRules() {
    }

    public static void validate(Schedule schedule) {
        if (schedule.getSubjectId() <= 0) {
            throw new IllegalArgumentException("Vui lòng chọn môn học.");
        }
        if (schedule.getDayOfWeek() < 1 || schedule.getDayOfWeek() > 7) {
            throw new IllegalArgumentException("Thứ trong tuần không hợp lệ.");
        }
        if (schedule.getStartMinute() < 0 || schedule.getStartMinute() > 1439
                || schedule.getEndMinute() < 1 || schedule.getEndMinute() > 1440
                || schedule.getEndMinute() <= schedule.getStartMinute()) {
            throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu trong cùng ngày.");
        }
        try {
            LocalDate first = LocalDate.parse(schedule.getValidFrom());
            LocalDate last = LocalDate.parse(schedule.getValidTo());
            ZoneId.of(schedule.getTimeZoneId());
            if (first.getYear() < 1 || last.getYear() > 9999 || last.isBefore(first)) {
                throw new IllegalArgumentException("Khoảng ngày áp dụng không hợp lệ.");
            }
            if (firstOccurrence(schedule).isAfter(last)) {
                throw new IllegalArgumentException("Khoảng ngày đã chọn không có buổi học vào thứ này.");
            }
        } catch (DateTimeException | NullPointerException error) {
            throw new IllegalArgumentException("Ngày hoặc múi giờ không hợp lệ.");
        }
        Integer reminder = schedule.getRemindBeforeMinutes();
        if (reminder != null && (reminder < 0 || reminder > 10080)) {
            throw new IllegalArgumentException("Nhắc trước phải nằm trong khoảng 0 đến 10080 phút.");
        }
    }

    public static LocalDate firstOccurrence(Schedule schedule) {
        return LocalDate.parse(schedule.getValidFrom()).with(
                TemporalAdjusters.nextOrSame(DayOfWeek.of(schedule.getDayOfWeek())));
    }

    public static String formatMinute(int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }

    public static class Conflict {
        public final Schedule schedule;
        public final LocalDate date;
        public final int startMinute;
        public final int endMinute;

        Conflict(Schedule schedule, LocalDate date, int start, int end) {
            this.schedule = schedule;
            this.date = date;
            this.startMinute = start;
            this.endMinute = end;
        }
    }

    private static Instant instant(LocalDate date, int minute, ZoneId zone) {
        return date.atStartOfDay().plusMinutes(minute).atZone(zone).toInstant();
    }

    private static boolean overlaps(Instant start, Instant end, LocalDate otherDate,
                                    int otherStart, int otherEnd, ZoneId otherZone) {
        return start.isBefore(instant(otherDate, otherEnd, otherZone))
                && end.isAfter(instant(otherDate, otherStart, otherZone));
    }

    /** Kiểm tra cả ngoại lệ của chuỗi được khôi phục, không tính lại buổi đã hủy/dời. */
    public static Conflict findRestoreConflict(Schedule restoring, List<Schedule> schedules,
                                               Map<Long, List<ScheduleException>> exceptions) {
        List<ScheduleException> overrides = exceptions.getOrDefault(
                restoring.getId(), Collections.emptyList());
        if (overrides.isEmpty()) {
            return findConflict(restoring, schedules, exceptions);
        }
        validate(restoring);
        LocalDate first = LocalDate.parse(restoring.getValidFrom());
        LocalDate last = LocalDate.parse(restoring.getValidTo());
        for (LocalDate day = firstOccurrence(restoring); !day.isAfter(last); day = day.plusWeeks(1)) {
            boolean replaced = false;
            for (ScheduleException override : overrides) {
                if (override.originalDate.equals(day.toString())) {
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                Conflict conflict = findConflict(singleOccurrence(restoring, day,
                        restoring.getStartMinute(), restoring.getEndMinute()), schedules, exceptions);
                if (conflict != null) {
                    return conflict;
                }
            }
        }
        for (ScheduleException override : overrides) {
            LocalDate original = LocalDate.parse(override.originalDate);
            if (override.newDate == null || original.isBefore(first) || original.isAfter(last)
                    || original.getDayOfWeek().getValue() != restoring.getDayOfWeek()) {
                continue;
            }
            Conflict conflict = findConflict(singleOccurrence(restoring, LocalDate.parse(override.newDate),
                    override.startMinute, override.endMinute), schedules, exceptions);
            if (conflict != null) {
                return conflict;
            }
        }
        return null;
    }

    private static Schedule singleOccurrence(Schedule source, LocalDate day, int start, int end) {
        return new Schedule(source.getId(), source.getSubjectId(), day.getDayOfWeek().getValue(),
                start, end, day.toString(), day.toString(), source.getTimeZoneId(),
                source.getRoom(), source.getNote(), source.getRemindBeforeMinutes());
    }

    /** So sánh những lần xuất hiện thật, có tính múi giờ và ngoại lệ đang có. */
    public static Conflict findConflict(Schedule candidate, List<Schedule> schedules,
                                        Map<Long, List<ScheduleException>> exceptions) {
        validate(candidate);
        ZoneId zone = ZoneId.of(candidate.getTimeZoneId());
        LocalDate last = LocalDate.parse(candidate.getValidTo());
        for (LocalDate date = firstOccurrence(candidate); !date.isAfter(last); date = date.plusWeeks(1)) {
            Instant start = instant(date, candidate.getStartMinute(), zone);
            Instant end = instant(date, candidate.getEndMinute(), zone);
            for (Schedule other : schedules) {
                if (other.getId() == candidate.getId() && candidate.getId() > 0) {
                    continue;
                }
                ZoneId otherZone = ZoneId.of(other.getTimeZoneId());
                List<ScheduleException> overrides = exceptions.getOrDefault(
                        other.getId(), Collections.emptyList());
                // Tìm ngày lịch bên kia dựa trên thời điểm tuyệt đối, không so giờ máy trực tiếp.
                LocalDate from = start.atZone(otherZone).toLocalDate().minusDays(1);
                LocalDate to = end.atZone(otherZone).toLocalDate();
                for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                    if (day.getDayOfWeek().getValue() != other.getDayOfWeek()
                            || day.isBefore(LocalDate.parse(other.getValidFrom()))
                            || day.isAfter(LocalDate.parse(other.getValidTo()))) {
                        continue;
                    }
                    boolean overridden = false;
                    for (ScheduleException override : overrides) {
                        if (override.originalDate.equals(day.toString())) {
                            overridden = true;
                            break;
                        }
                    }
                    if (!overridden && overlaps(start, end, day, other.getStartMinute(),
                            other.getEndMinute(), otherZone)) {
                        return new Conflict(other, day, other.getStartMinute(), other.getEndMinute());
                    }
                }
                // Buổi dời lịch có thể nằm ngoài khoảng ngày của chuỗi ban đầu.
                for (ScheduleException override : overrides) {
                    if (override.newDate == null) {
                        continue;
                    }
                    LocalDate original = LocalDate.parse(override.originalDate);
                    // Giống lịch ngày/tuần: chỉ tính ngoại lệ thuộc một buổi gốc có thật.
                    if (original.getDayOfWeek().getValue() != other.getDayOfWeek()
                            || original.isBefore(LocalDate.parse(other.getValidFrom()))
                            || original.isAfter(LocalDate.parse(other.getValidTo()))) {
                        continue;
                    }
                    if (overlaps(start, end,
                            LocalDate.parse(override.newDate), override.startMinute,
                            override.endMinute, otherZone)) {
                        return new Conflict(other, LocalDate.parse(override.newDate),
                                override.startMinute, override.endMinute);
                    }
                }
            }
        }
        return null;
    }
}
