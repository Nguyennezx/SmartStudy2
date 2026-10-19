package com.example.smartstudy.schedule.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Chia làn cho thẻ chồng giờ, kể cả thẻ ngắn cần chiều cao tối thiểu để đọc/bấm. */
public final class CalendarCardLayout {
    private CalendarCardLayout() {
    }

    public static class Placement {
        public final ScheduleOccurrence occurrence;
        public final int lane;
        public int laneCount;

        Placement(ScheduleOccurrence occurrence, int lane) {
            this.occurrence = occurrence;
            this.lane = lane;
        }
    }

    public static List<Placement> place(List<ScheduleOccurrence> events, int minimumMinutes) {
        List<ScheduleOccurrence> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparingInt(ScheduleOccurrence::getStartMinute)
                .thenComparingInt(ScheduleOccurrence::getEndMinute));
        List<Placement> result = new ArrayList<>();
        List<Placement> group = new ArrayList<>();
        List<Integer> laneEnds = new ArrayList<>();
        int groupEnd = -1;
        for (ScheduleOccurrence event : sorted) {
            if (event.getStartMinute() >= groupEnd) {
                finish(group, laneEnds.size());
                group.clear();
                laneEnds.clear();
                groupEnd = -1;
            }
            int visualEnd = Math.max(event.getEndMinute(), event.getStartMinute() + minimumMinutes);
            int lane = 0;
            while (lane < laneEnds.size() && laneEnds.get(lane) > event.getStartMinute()) {
                lane++;
            }
            if (lane == laneEnds.size()) {
                laneEnds.add(visualEnd);
            } else {
                laneEnds.set(lane, visualEnd);
            }
            Placement placement = new Placement(event, lane);
            group.add(placement);
            result.add(placement);
            groupEnd = Math.max(groupEnd, visualEnd);
        }
        finish(group, laneEnds.size());
        return result;
    }

    private static void finish(List<Placement> group, int laneCount) {
        for (Placement placement : group) {
            placement.laneCount = laneCount;
        }
    }
}
