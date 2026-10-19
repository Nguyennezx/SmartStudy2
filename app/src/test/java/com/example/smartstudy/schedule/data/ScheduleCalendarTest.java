package com.example.smartstudy.schedule.data;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class ScheduleCalendarTest {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private Schedule schedule(long id, int day, int start, int end, String from, String to, String zone) {
        Schedule schedule = new Schedule(id, 1, day, start, end, from, to, zone, "A203", "", null);
        schedule.setSubjectDisplay("Java", "#6C7EE1");
        return schedule;
    }

    private Schedule monday() {
        return schedule(1, 1, 510, 630, "2026-10-19", "2026-12-31", VIETNAM.getId());
    }

    private List<ScheduleOccurrence> week(LocalDate date, Schedule schedule) {
        return ScheduleCalendar.buildWeek(date, VIETNAM, Arrays.asList(schedule), Collections.emptyMap());
    }

    @Test
    public void onlyActualDatesAppearAndSelectedDayCanBeEmpty() {
        List<ScheduleOccurrence> events = week(LocalDate.of(2026, 10, 21), monday());
        assertEquals(1, events.size());
        assertEquals(LocalDate.of(2026, 10, 19), events.get(0).getDate());
        assertEquals(510, events.get(0).getStartMinute());
        assertEquals(630, events.get(0).getEndMinute());
        assertTrue(ScheduleCalendar.onDate(events, LocalDate.of(2026, 10, 21)).isEmpty());
        assertTrue(week(LocalDate.of(2027, 1, 11), monday()).isEmpty());
    }

    @Test
    public void weekNavigationCrossesMonthAndYearAndIncludesSunday() {
        LocalDate date = LocalDate.of(2027, 1, 3);
        assertEquals(LocalDate.of(2026, 12, 28), ScheduleCalendar.weekStart(date));
        Schedule sunday = schedule(1, 7, 510, 630, "2027-01-03", "2027-01-03", VIETNAM.getId());
        List<ScheduleOccurrence> events = week(date, sunday);
        assertEquals(1, events.size());
        assertEquals(date, events.get(0).getDate());
        assertTrue(week(date.plusWeeks(1), sunday).isEmpty());
    }

    @Test
    public void cancellationAndMovedRoomOverrideReplaceTheOriginal() {
        Map<Long, List<ScheduleException>> cancelled = Collections.singletonMap(1L,
                Arrays.asList(new ScheduleException(1, "2026-10-19", null, null, null)));
        assertTrue(ScheduleCalendar.buildWeek(LocalDate.of(2026, 10, 19), VIETNAM,
                Arrays.asList(monday()), cancelled).isEmpty());
        Map<Long, List<ScheduleException>> moved = Collections.singletonMap(1L,
                Arrays.asList(new ScheduleException(1, "2026-10-19", "2026-10-20", 600, 660, "B102")));
        List<ScheduleOccurrence> events = ScheduleCalendar.buildWeek(LocalDate.of(2026, 10, 19),
                VIETNAM, Arrays.asList(monday()), moved);
        assertEquals(1, events.size());
        assertEquals(LocalDate.of(2026, 10, 20), events.get(0).getDate());
        assertEquals("B102", events.get(0).getRoom());
        assertTrue(events.get(0).isMoved());
        assertEquals(1, events.get(0).getSchedule().getId());
    }

    @Test
    public void movedClassCanAppearOutsideOriginalDateRangeAndClearRoom() {
        Schedule original = schedule(1, 1, 510, 630, "2026-10-19", "2026-10-19", VIETNAM.getId());
        Map<Long, List<ScheduleException>> moved = Collections.singletonMap(1L,
                Arrays.asList(new ScheduleException(1, "2026-10-19", "2026-10-27", 600, 660, "")));
        List<ScheduleOccurrence> events = ScheduleCalendar.buildWeek(LocalDate.of(2026, 10, 26),
                VIETNAM, Arrays.asList(original), moved);
        assertEquals(1, events.size());
        assertEquals(LocalDate.of(2026, 10, 27), events.get(0).getDate());
        assertEquals("", events.get(0).getRoom());
        assertEquals("1/2026-10-19", events.get(0).getOccurrenceKey());
    }

    @Test
    public void invalidOriginalDateDoesNotCreateAHiddenExtraClass() {
        Schedule original = schedule(1, 1, 510, 630, "2026-10-19", "2026-10-19", VIETNAM.getId());
        Map<Long, List<ScheduleException>> moved = Collections.singletonMap(1L,
                Arrays.asList(new ScheduleException(1, "2026-10-20", "2026-10-21", 600, 660)));
        List<ScheduleOccurrence> events = ScheduleCalendar.buildWeek(LocalDate.of(2026, 10, 19),
                VIETNAM, Arrays.asList(original), moved);
        assertEquals(1, events.size());
        assertFalse(events.get(0).isMoved());
    }

    @Test
    public void timeZonesAtWeekBoundaryDoNotDropAnOccurrence() {
        Schedule utcSunday = schedule(1, 7, 1410, 1440, "2026-10-18", "2026-10-18", "UTC");
        List<ScheduleOccurrence> events = week(LocalDate.of(2026, 10, 19), utcSunday);
        assertEquals(1, events.size());
        assertEquals(LocalDate.of(2026, 10, 19), events.get(0).getDate());
        assertEquals(390, events.get(0).getStartMinute());
        assertEquals(420, events.get(0).getEndMinute());
    }

    @Test
    public void classAcrossDisplayMidnightIsSplitWithoutDoubleCountingTheClass() {
        Schedule utcMonday = schedule(1, 1, 990, 1110, "2026-10-19", "2026-10-19", "UTC");
        List<ScheduleOccurrence> events = week(LocalDate.of(2026, 10, 19), utcMonday);
        assertEquals(2, events.size());
        assertEquals(1410, events.get(0).getStartMinute());
        assertEquals(1440, events.get(0).getEndMinute());
        assertEquals(0, events.get(1).getStartMinute());
        assertEquals(90, events.get(1).getEndMinute());
        Set<String> unique = new HashSet<>();
        for (ScheduleOccurrence event : events) {
            unique.add(event.getOccurrenceKey());
        }
        assertEquals(1, unique.size());
    }

    @Test
    public void overlapCardsUseSeparateLanesAndAdjacentClassesCanShareOne() {
        LocalDate date = LocalDate.of(2026, 10, 19);
        List<ScheduleOccurrence> events = Arrays.asList(
                new ScheduleOccurrence(monday(), date, 480, 600, "", false),
                new ScheduleOccurrence(monday(), date, 540, 660, "", false),
                new ScheduleOccurrence(monday(), date, 600, 720, "", false));
        List<CalendarCardLayout.Placement> placed = CalendarCardLayout.place(events, 53);
        assertEquals(0, placed.get(0).lane);
        assertEquals(1, placed.get(1).lane);
        assertEquals(0, placed.get(2).lane);
        assertEquals(2, placed.get(0).laneCount);
        List<CalendarCardLayout.Placement> adjacent = CalendarCardLayout.place(
                Arrays.asList(events.get(0), events.get(2)), 53);
        assertEquals(1, adjacent.get(0).laneCount);
        assertEquals(1, adjacent.get(1).laneCount);
    }

    @Test
    public void shortCardsDoNotCoverEachOtherWhenMinimumHeightIsApplied() {
        LocalDate date = LocalDate.of(2026, 10, 19);
        List<CalendarCardLayout.Placement> placed = CalendarCardLayout.place(Arrays.asList(
                new ScheduleOccurrence(monday(), date, 480, 490, "", false),
                new ScheduleOccurrence(monday(), date, 495, 505, "", false)), 53);
        assertEquals(2, placed.get(0).laneCount);
        assertNotEquals(placed.get(0).lane, placed.get(1).lane);
    }
}
