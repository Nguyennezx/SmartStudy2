package com.example.smartstudy.schedule.reminder;

import com.example.smartstudy.schedule.data.Schedule;
import com.example.smartstudy.schedule.data.ScheduleException;

import org.junit.Test;

import java.time.Instant;
import java.util.Collections;

import static org.junit.Assert.*;

public class ReminderPlannerTest {
    private Schedule schedule(Integer reminder) {
        return new Schedule(1, 2, 1, 510, 630, "2026-10-19", "2026-11-02",
                "Asia/Ho_Chi_Minh", "A203", "", reminder);
    }

    private ReminderPlan next(Integer reminder, String now) {
        return ReminderPlanner.next(schedule(reminder), Collections.emptyList(),
                Instant.parse(now), Collections.emptySet());
    }

    @Test
    public void leadTimeUsesScheduleZone() {
        ReminderPlan plan = next(15, "2026-10-18T00:00:00Z");
        assertEquals(Instant.parse("2026-10-19T01:30:00Z").toEpochMilli(), plan.startMillis);
        assertEquals(Instant.parse("2026-10-19T01:15:00Z").toEpochMilli(), plan.triggerMillis);
    }

    @Test
    public void disabledAndAtStartAreDifferent() {
        assertNull(next(null, "2026-10-18T00:00:00Z"));
        ReminderPlan plan = next(0, "2026-10-18T00:00:00Z");
        assertEquals(plan.startMillis, plan.triggerMillis);
    }

    @Test
    public void deliveredOccurrenceIsSkippedEvenAfterClockRollback() {
        ReminderPlan first = next(5, "2026-10-18T00:00:00Z");
        ReminderPlan following = ReminderPlanner.next(schedule(5), Collections.emptyList(),
                Instant.parse("2026-10-17T00:00:00Z"), Collections.singleton(first.key()));
        assertEquals("2026-10-26", following.originalDate.toString());
    }

    @Test
    public void cancellationSkipsOriginal() {
        ReminderPlan plan = ReminderPlanner.next(schedule(5), Collections.singletonList(
                new ScheduleException(1, "2026-10-19", null, null, null)),
                Instant.parse("2026-10-18T00:00:00Z"), Collections.emptySet());
        assertEquals("2026-10-26", plan.originalDate.toString());
    }

    @Test
    public void movedOccurrenceCanBeOutsideRangeAndClearRoom() {
        ReminderPlan plan = ReminderPlanner.next(schedule(5), Collections.singletonList(
                new ScheduleException(1, "2026-10-19", "2026-11-05", 600, 660, "")),
                Instant.parse("2026-11-04T00:00:00Z"), Collections.emptySet());
        assertEquals("2026-10-19", plan.originalDate.toString());
        assertEquals("", plan.room);
        assertEquals(Instant.parse("2026-11-05T03:00:00Z").toEpochMilli(), plan.startMillis);
    }

    @Test
    public void lateDeliveryExpiresAfterTenMinutes() {
        assertEquals("2026-10-19", next(5, "2026-10-19T01:39:59Z").originalDate.toString());
        assertEquals("2026-10-26", next(5, "2026-10-19T01:40:00Z").originalDate.toString());
        assertNull(next(5, "2026-11-02T01:40:00Z"));
    }

    @Test
    public void shortLessonExpiresAtItsEnd() {
        Schedule shortLesson = new Schedule(1, 2, 1, 510, 515, "2026-10-19", "2026-10-19",
                "Asia/Ho_Chi_Minh", "", "", 0);
        assertNull(ReminderPlanner.next(shortLesson, Collections.emptyList(),
                Instant.parse("2026-10-19T01:35:00Z"), Collections.emptySet()));
    }

    @Test
    public void changedLeadTimeHasDifferentDeliveryKey() {
        assertNotEquals(next(5, "2026-10-18T00:00:00Z").key(),
                next(15, "2026-10-18T00:00:00Z").key());
    }

    @Test
    public void longLeadTimeStillFindsNextWeek() {
        ReminderPlan plan = next(10080, "2026-10-19T02:00:00Z");
        assertEquals("2026-10-26", plan.originalDate.toString());
        assertEquals(Instant.parse("2026-10-19T01:30:00Z").toEpochMilli(), plan.triggerMillis);
    }
}
