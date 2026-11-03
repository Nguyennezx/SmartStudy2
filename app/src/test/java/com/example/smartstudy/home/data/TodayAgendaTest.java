package com.example.smartstudy.home.data;

import com.example.smartstudy.schedule.data.Schedule;
import com.example.smartstudy.schedule.data.ScheduleCalendar;
import com.example.smartstudy.schedule.data.ScheduleException;
import com.example.smartstudy.schedule.data.ScheduleOccurrence;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class TodayAgendaTest {
    private final ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
    private final LocalDate date = LocalDate.of(2026, 11, 3);
    private ScheduleOccurrence lesson(long id, int start, int end) {
        Schedule schedule = new Schedule(id, 1, 2, start, end, date.toString(), date.toString(),
                zone.getId(), "A203", "", null);
        return new ScheduleOccurrence(schedule, date, start, end, "A203", false);
    }
    private Instant at(int minute) { return date.atStartOfDay(zone).plusMinutes(minute).toInstant(); }

    @Test public void nextClassIsNotRepeatedAndInputsAreNotMutated() {
        ScheduleOccurrence morning = lesson(1, 510, 630), afternoon = lesson(2, 780, 900);
        java.util.List<ScheduleOccurrence> input = Arrays.asList(afternoon, morning);
        TodayAgenda result = TodayAgenda.at(input, at(500), zone);
        assertSame(morning, result.featured);
        assertEquals(Collections.singletonList(afternoon), result.remaining);
        assertEquals(2, input.size());
        assertSame(afternoon, input.get(0));
    }
    @Test public void ongoingClassRemainsUntilItsEndAndAdjacentClassTakesOver() {
        ScheduleOccurrence first = lesson(1, 510, 630), next = lesson(2, 630, 720);
        assertSame(first, TodayAgenda.at(Arrays.asList(first, next), at(510), zone).featured);
        assertSame(first, TodayAgenda.at(Arrays.asList(first, next), at(629), zone).featured);
        assertSame(next, TodayAgenda.at(Arrays.asList(first, next), at(630), zone).featured);
        assertNull(TodayAgenda.at(Arrays.asList(first, next), at(720), zone).featured);
    }
    @Test public void yesterdayDoesNotLeakAfterLocalMidnight() {
        ScheduleOccurrence late = lesson(1, 1380, 1440);
        assertSame(late, TodayAgenda.at(Collections.singletonList(late), at(1439), zone).featured);
        assertNull(TodayAgenda.at(Collections.singletonList(late), at(1440), zone).featured);
        assertTrue(TodayAgenda.at(Collections.emptyList(), at(500), zone).remaining.isEmpty());
    }
    @Test public void cancelledAndMovedLessonsUseCalendarResults() {
        Schedule schedule = lesson(1, 510, 630).getSchedule();
        ScheduleException cancelled = new ScheduleException(1, date.toString(), null, null, null);
        assertNull(TodayAgenda.at(ScheduleCalendar.buildWeek(date, zone,
                Collections.singletonList(schedule), Collections.singletonMap(1L,
                        Collections.singletonList(cancelled))), at(500), zone).featured);
        ScheduleException moved = new ScheduleException(1, date.toString(), date.toString(), 780, 900, "B201");
        TodayAgenda result = TodayAgenda.at(ScheduleCalendar.buildWeek(date, zone,
                Collections.singletonList(schedule), Collections.singletonMap(1L,
                        Collections.singletonList(moved))), at(700), zone);
        assertNotNull(result.featured);
        assertEquals(780, result.featured.getStartMinute());
        assertEquals("B201", result.featured.getRoom());
    }
}
