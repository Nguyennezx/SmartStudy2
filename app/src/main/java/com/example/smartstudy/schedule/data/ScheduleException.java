package com.example.smartstudy.schedule.data;

/** Chỉ đọc ngoại lệ đã có; giao diện đổi/hủy riêng một buổi sẽ làm sau. */
public class ScheduleException {
    public final long scheduleId;
    public final String originalDate;
    public final String newDate;
    public final Integer startMinute;
    public final Integer endMinute;
    public final String newRoom;

    public ScheduleException(long scheduleId, String originalDate, String newDate,
                             Integer startMinute, Integer endMinute) {
        this(scheduleId, originalDate, newDate, startMinute, endMinute, null);
    }

    public ScheduleException(long scheduleId, String originalDate, String newDate,
                             Integer startMinute, Integer endMinute, String newRoom) {
        this.scheduleId = scheduleId;
        this.originalDate = originalDate;
        this.newDate = newDate;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.newRoom = newRoom;
    }
}
