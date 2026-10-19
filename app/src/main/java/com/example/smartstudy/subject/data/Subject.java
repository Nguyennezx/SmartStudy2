package com.example.smartstudy.subject.data;

/** Môn học dùng chung cho lịch học, bài tập và phiên học. */
public class Subject {
    private final long id;
    private final String name;
    private final String teacherName;
    private final String colorHex;
    private final boolean archived;
    private final Long semesterId;
    private final String semesterName;

    public Subject(long id, String name, String teacherName, String colorHex, boolean archived) {
        this(id, name, teacherName, colorHex, archived, null, null);
    }

    public Subject(long id, String name, String teacherName, String colorHex, boolean archived,
            Long semesterId, String semesterName) {
        this.id = id;
        this.name = name;
        this.teacherName = teacherName;
        this.colorHex = colorHex;
        this.archived = archived;
        this.semesterId = semesterId;
        this.semesterName = semesterName;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public String getColorHex() {
        return colorHex;
    }

    public boolean isArchived() {
        return archived;
    }

    public Long getSemesterId() {
        return semesterId;
    }

    public String getSemesterName() {
        return semesterName;
    }
}
