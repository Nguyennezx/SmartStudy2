package com.example.smartstudy.schedule.data;

import java.time.LocalDate;

/** Một buổi học thực tế trên ngày đang xem, khác với chuỗi lịch lặp trong SQLite. */
public class ScheduleOccurrence {
    private final Schedule schedule;
    private final LocalDate date;
    private final int startMinute;
    private final int endMinute;
    private final String room;
    private final boolean moved;
    private final String occurrenceKey;

    public ScheduleOccurrence(Schedule schedule, LocalDate date, int startMinute,
                              int endMinute, String room, boolean moved) {
        this(schedule, date, startMinute, endMinute, room, moved, schedule.getId() + "/" + date);
    }

    public ScheduleOccurrence(Schedule schedule, LocalDate date, int startMinute,
                              int endMinute, String room, boolean moved, String occurrenceKey) {
        this.schedule = schedule;
        this.date = date;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.room = room;
        this.moved = moved;
        this.occurrenceKey = occurrenceKey;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public String getRoom() {
        return room;
    }

    public boolean isMoved() {
        return moved;
    }

    public String getOccurrenceKey() {
        return occurrenceKey;
    }
}
