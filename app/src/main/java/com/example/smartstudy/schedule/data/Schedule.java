package com.example.smartstudy.schedule.data;

/** Một buổi học lặp hàng tuần; thứ Hai = 1, Chủ nhật = 7. */
public class Schedule {
    private final long id;
    private final long subjectId;
    private final int dayOfWeek;
    private final int startMinute;
    private final int endMinute;
    private final String validFrom;
    private final String validTo;
    private final String timeZoneId;
    private final String room;
    private final String note;
    private final Integer remindBeforeMinutes;
    private String subjectName;
    private String colorHex;

    public Schedule(long id, long subjectId, int dayOfWeek, int startMinute, int endMinute,
                    String validFrom, String validTo, String timeZoneId, String room,
                    String note, Integer remindBeforeMinutes) {
        this.id = id;
        this.subjectId = subjectId;
        this.dayOfWeek = dayOfWeek;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.timeZoneId = timeZoneId;
        this.room = room;
        this.note = note;
        this.remindBeforeMinutes = remindBeforeMinutes;
    }

    public long getId() {
        return id;
    }

    public long getSubjectId() {
        return subjectId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public String getValidFrom() {
        return validFrom;
    }

    public String getValidTo() {
        return validTo;
    }

    public String getTimeZoneId() {
        return timeZoneId;
    }

    public String getRoom() {
        return room;
    }

    public String getNote() {
        return note;
    }

    public Integer getRemindBeforeMinutes() {
        return remindBeforeMinutes;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setSubjectDisplay(String name, String color) {
        subjectName = name;
        colorHex = color;
    }
}
