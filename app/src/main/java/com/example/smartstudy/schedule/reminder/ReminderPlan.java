package com.example.smartstudy.schedule.reminder;

import com.example.smartstudy.schedule.data.Schedule;

import java.time.LocalDate;

/** Một lần nhắc cụ thể; khóa đổi khi môn, giờ hoặc số phút nhắc thay đổi. */
public class ReminderPlan {
    public final Schedule schedule;
    public final LocalDate originalDate;
    public final long startMillis;
    public final long endMillis;
    public final long triggerMillis;
    public final String room;

    ReminderPlan(Schedule schedule, LocalDate originalDate, long startMillis,
                 long endMillis, String room) {
        this.schedule = schedule;
        this.originalDate = originalDate;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.triggerMillis = startMillis - schedule.getRemindBeforeMinutes() * 60000L;
        this.room = room;
    }

    public String key() {
        return schedule.getId() + "/" + schedule.getSubjectId() + "/" + originalDate + "/"
                + startMillis + "/" + endMillis + "/" + schedule.getRemindBeforeMinutes();
    }

    /** Không gửi nhắc muộn quá 10 phút sau lúc bắt đầu, hoặc sau khi buổi đã kết thúc. */
    public long expiresMillis() {
        return Math.min(endMillis, startMillis + 10 * 60000L);
    }
}
