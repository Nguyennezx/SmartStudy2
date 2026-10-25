package com.example.smartstudy.schedule.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ScheduleRestoreTest {
    private Schedule lesson(long id, int day, int start, int end, String date, String zone) {
        Schedule schedule = new Schedule(id, id, day, start, end, date, date, zone, "", "", null);
        schedule.setSubjectDisplay("Môn " + id, "#6C7EE1");
        return schedule;
    }

    private Schedule monday(long id, int start, int end) {
        return lesson(id, 1, start, end, "2026-10-19", "Asia/Ho_Chi_Minh");
    }

    @Test
    public void overlappingRestoredLessonIsBlockedButAdjacentIsAllowed() {
        Schedule restoring = monday(1, 510, 630);
        Schedule active = monday(2, 600, 660);
        ScheduleRules.Conflict conflict = ScheduleRules.findRestoreConflict(restoring,
                Arrays.asList(restoring, active), Collections.emptyMap());
        assertNotNull(conflict);
        assertEquals(2, conflict.schedule.getId());
        assertEquals("2026-10-19", conflict.date.toString());
        assertNull(ScheduleRules.findRestoreConflict(restoring,
                Arrays.asList(restoring, monday(2, 630, 690)), Collections.emptyMap()));
    }

    @Test
    public void cancelledRestoredOccurrenceDoesNotBlockRestore() {
        Schedule restoring = monday(1, 510, 630);
        Map<Long, List<ScheduleException>> exceptions = Collections.singletonMap(1L,
                Collections.singletonList(new ScheduleException(1, "2026-10-19", null, null, null)));
        assertNull(ScheduleRules.findRestoreConflict(restoring,
                Arrays.asList(monday(2, 510, 630)), exceptions));
    }

    @Test
    public void movedRestoredOccurrenceChecksItsActualDateOutsideOriginalRange() {
        Schedule restoring = monday(1, 510, 630);
        Map<Long, List<ScheduleException>> exceptions = Collections.singletonMap(1L,
                Collections.singletonList(new ScheduleException(1, "2026-10-19", "2026-10-20", 600, 660)));
        assertNull(ScheduleRules.findRestoreConflict(restoring,
                Arrays.asList(monday(2, 510, 630)), exceptions));
        Schedule active = lesson(2, 2, 600, 660, "2026-10-20", "Asia/Ho_Chi_Minh");
        assertNotNull(ScheduleRules.findRestoreConflict(restoring, Arrays.asList(active), exceptions));
    }

    @Test
    public void activeLessonCancellationIsAlsoRespected() {
        Schedule restoring = monday(1, 510, 630);
        Map<Long, List<ScheduleException>> exceptions = Collections.singletonMap(2L,
                Collections.singletonList(new ScheduleException(2, "2026-10-19", null, null, null)));
        assertNull(ScheduleRules.findRestoreConflict(restoring,
                Arrays.asList(monday(2, 510, 630)), exceptions));
    }

    @Test
    public void restoresCompareAbsoluteTimeAcrossDifferentLocalDates() {
        Schedule restoring = lesson(1, 1, 30, 90, "2026-10-19", "Asia/Ho_Chi_Minh");
        Schedule active = lesson(2, 7, 1050, 1110, "2026-10-18", "UTC");
        assertNotNull(ScheduleRules.findRestoreConflict(restoring,
                Arrays.asList(active), Collections.emptyMap()));
    }

    @Test
    public void invalidMovedOriginalDoesNotInventARestoredOccurrence() {
        Schedule restoring = monday(1, 510, 630);
        Map<Long, List<ScheduleException>> exceptions = Collections.singletonMap(1L,
                Collections.singletonList(new ScheduleException(1, "2026-10-12", "2026-10-20", 600, 660)));
        Schedule active = lesson(2, 2, 600, 660, "2026-10-20", "Asia/Ho_Chi_Minh");
        assertNull(ScheduleRules.findRestoreConflict(restoring, Arrays.asList(active), exceptions));
    }
}
