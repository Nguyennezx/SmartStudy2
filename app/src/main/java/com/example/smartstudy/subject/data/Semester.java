package com.example.smartstudy.subject.data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class Semester {
    private final long id;
    private final String name, startDate, endDate;
    private final boolean archived;
    public Semester(long id, String name, String startDate, String endDate, boolean archived) {
        this.id = id;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.archived = archived;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public boolean isArchived() {
        return archived;
    }

    public String getDateRange() {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return LocalDate.parse(startDate).format(format) + " – " + LocalDate.parse(endDate).format(format);
    }

    @Override
    public String toString() {
        return name + " (" + getDateRange() + ")";
    }

    /** SQLite chỉ kiểm tra hình thức ngày; app kiểm tra ngày có thật và khoảng ngày. */
    public static void validate(String name, String start, String end) {
        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Vui lòng nhập tên học kỳ.");
        try {
            LocalDate first = LocalDate.parse(start == null ? "" : start);
            LocalDate last = LocalDate.parse(end == null ? "" : end);
            if (first.getYear() < 1 || first.getYear() > 9999 || last.getYear() < 1 || last.getYear() > 9999)
                throw new IllegalArgumentException("Năm phải nằm trong khoảng 1 đến 9999.");
            if (last.isBefore(first))
                throw new IllegalArgumentException("Ngày kết thúc không được trước ngày bắt đầu.");
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("Vui lòng chọn ngày hợp lệ.");
        }
    }
}
