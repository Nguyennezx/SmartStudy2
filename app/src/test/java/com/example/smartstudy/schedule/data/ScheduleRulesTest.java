package com.example.smartstudy.schedule.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ScheduleRulesTest {
    private Schedule schedule(long id, int day, int start, int end, String from, String to, String zone) {
        Schedule result = new Schedule(id, 1, day, start, end, from, to, zone, "A203", "", null);
        result.setSubjectDisplay("Java", "#6C7EE1");
        return result;
    }

    private Schedule monday(long id, int start, int end, String from, String to) {
        return schedule(id, 1, start, end, from, to, "Asia/Ho_Chi_Minh");
    }

    private ScheduleRules.Conflict conflict(Schedule candidate, Schedule existing) {
        return ScheduleRules.findConflict(candidate, Arrays.asList(existing), Collections.emptyMap());
    }

    @Test
    public void overlappingHoursConflictButAdjacentHoursAreAllowed() {
        Schedule existing = monday(1, 510, 630, "2026-10-19", "2026-12-31");
        assertNotNull(conflict(monday(0, 600, 660, "2026-10-19", "2026-12-31"), existing));
        assertNull(conflict(monday(0, 630, 690, "2026-10-19", "2026-12-31"), existing));
        assertNull(conflict(monday(0, 450, 510, "2026-10-19", "2026-12-31"), existing));
    }

    @Test
    public void intersectingDateRangesNeedAnActualCommonOccurrence() {
        Schedule existing = monday(1, 510, 630, "2026-10-12", "2026-10-25");
        assertNull(conflict(monday(0, 510, 630, "2026-10-20", "2026-11-02"), existing));
        ScheduleRules.Conflict found = conflict(
                monday(0, 510, 630, "2026-10-19", "2026-10-19"), existing);
        assertNotNull(found);
        assertEquals("2026-10-19", found.date.toString());
    }

    @Test
    public void differentDaysAndAnUnchangedEditDoNotConflict() {
        Schedule existing = monday(7, 510, 630, "2026-10-19", "2026-12-31");
        assertNull(conflict(existing, existing));
        assertNull(conflict(schedule(0, 2, 510, 630, "2026-10-19", "2026-12-31",
                "Asia/Ho_Chi_Minh"), existing));
    }

    @Test
    public void timeZonesCanOverlapOnDifferentLocalDates() {
        Schedule vietnam = schedule(0, 1, 30, 90, "2026-10-19", "2026-10-19", "Asia/Ho_Chi_Minh");
        Schedule utcSunday = schedule(1, 7, 1050, 1110, "2026-10-18", "2026-10-18", "UTC");
        assertNotNull(conflict(vietnam, utcSunday));
    }

    @Test
    public void daylightSavingOffsetsAreCalculatedForEachDate() {
        Schedule newYork = schedule(1, 1, 510, 570, "2026-10-26", "2026-11-02", "America/New_York");
        assertNull(conflict(schedule(0, 1, 810, 870, "2026-10-26", "2026-10-26", "UTC"), newYork));
        assertNotNull(conflict(schedule(0, 1, 810, 870, "2026-11-02", "2026-11-02", "UTC"), newYork));
    }

    @Test
    public void cancelledOccurrenceIsSkippedButLaterWeeksStillConflict() {
        Schedule existing = monday(1, 510, 630, "2026-10-19", "2026-10-26");
        Map<Long, List<ScheduleException>> exceptions = Collections.singletonMap(1L,
                Arrays.asList(new ScheduleException(1, "2026-10-19", null, null, null)));
        assertNull(ScheduleRules.findConflict(monday(0, 510, 630, "2026-10-19", "2026-10-19"),
                Arrays.asList(existing), exceptions));
        ScheduleRules.Conflict found = ScheduleRules.findConflict(
                monday(0, 510, 630, "2026-10-19", "2026-10-26"), Arrays.asList(existing), exceptions);
        assertEquals("2026-10-26", found.date.toString());
    }

    @Test
    public void rescheduledOccurrenceOutsideTheOriginalRangeIsChecked() {
        Schedule existing = monday(1, 510, 630, "2026-10-19", "2026-10-19");
        Map<Long, List<ScheduleException>> exceptions = Collections.singletonMap(1L,
                Arrays.asList(new ScheduleException(1, "2026-10-19", "2026-10-20", 600, 660)));
        assertNull(ScheduleRules.findConflict(monday(0, 510, 630, "2026-10-19", "2026-10-19"),
                Arrays.asList(existing), exceptions));
        Schedule candidate = schedule(0, 2, 600, 660, "2026-10-20", "2026-10-20", "Asia/Ho_Chi_Minh");
        ScheduleRules.Conflict found = ScheduleRules.findConflict(candidate, Arrays.asList(existing), exceptions);
        assertEquals("2026-10-20", found.date.toString());
        assertEquals(600, found.startMinute);
    }

    @Test
    public void invalidDateTimeAndEmptyOccurrenceRangesAreRejected() {
        reject(monday(0, 630, 510, "2026-10-19", "2026-12-31"));
        reject(monday(0, 510, 510, "2026-10-19", "2026-12-31"));
        reject(monday(0, 510, 630, "2026-12-31", "2026-10-19"));
        reject(monday(0, 510, 630, "2026-02-30", "2026-12-31"));
        reject(monday(0, 510, 630, "2026-10-20", "2026-10-25"));
        reject(schedule(0, 8, 510, 630, "2026-10-19", "2026-12-31", "UTC"));
        reject(schedule(0, 1, 510, 630, "2026-10-19", "2026-12-31", "Invalid/Zone"));
        ScheduleRules.validate(monday(0, 1380, 1440, "2026-10-19", "2026-10-19"));
        assertEquals("24:00", ScheduleRules.formatMinute(1440));
    }

    private void reject(Schedule schedule) {
        try {
            ScheduleRules.validate(schedule);
            fail("Dữ liệu sai phải bị từ chối");
        } catch (IllegalArgumentException expected) {
            // Đúng kỳ vọng.
        }
    }
}
