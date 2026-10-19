package com.example.smartstudy.subject.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.smartstudy.core.database.DatabaseHelper;
import com.example.smartstudy.schedule.reminder.ReminderCoordinator;
import java.util.ArrayList;
import java.util.List;

/** Gọi các hàm DAO trên luồng nền; dùng Helper chung của project. */
public class SubjectDAO {
    private final DatabaseHelper helper;
    private final Context appContext;
    public static final long ALL_SEMESTERS = -1;
    public static final long UNASSIGNED_SEMESTER = 0;

    public SubjectDAO(Context context) {
        helper = DatabaseHelper.getInstance(context);
        appContext = context.getApplicationContext();
    }

    SubjectDAO(DatabaseHelper helper) {
        this.helper = helper;
        appContext = null;
    }

    /** Danh sách môn đang dùng để người 3 chọn khi tạo bài tập/phiên học. */
    public List<Subject> getActiveSubjects() {
        return getByArchived(false);
    }

    public List<Subject> getByArchived(boolean archived) {
        return getByArchived(archived, ALL_SEMESTERS);
    }

    /** filter: -1 tất cả, 0 chưa gán học kỳ, số dương là ID học kỳ. */
    public List<Subject> getByArchived(boolean archived, long semesterFilter) {
        if (semesterFilter < ALL_SEMESTERS)
            throw new IllegalArgumentException("Bộ lọc học kỳ không hợp lệ.");
        List<Subject> subjects = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        String sql = "SELECT s.id, s.name, s.teacher_name, s.color_hex, s.is_archived, "
        + "s.semester_id, sem.name AS semester_name FROM subjects s "
        + "LEFT JOIN semesters sem ON sem.id=s.semester_id WHERE s.is_archived=?";
        List<String> args = new ArrayList<>();
        args.add(archived ? "1" : "0");
        if (semesterFilter == UNASSIGNED_SEMESTER)
            sql += " AND s.semester_id IS NULL";
        else if (semesterFilter > 0) {
            sql += " AND s.semester_id=?";
            args.add(String.valueOf(semesterFilter));
        }

        sql += " ORDER BY s.name COLLATE NOCASE, s.id";
        try (Cursor cursor = db.rawQuery(sql, args.toArray(new String[0]))) {
            while (cursor.moveToNext()) {
                subjects.add(new Subject(cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("teacher_name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("color_hex")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_archived")) == 1,
                        cursor.isNull(cursor.getColumnIndexOrThrow("semester_id")) ? null
                        : cursor.getLong(cursor.getColumnIndexOrThrow("semester_id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("semester_name"))));
            }
        }

        return subjects;
    }

    public long insert(String name, String teacher, String color) {
        return insert(name, teacher, color, null);
    }

    public long insert(String name, String teacher, String color, Long semesterId) {
        ContentValues values = formValues(name, teacher, color);
        putSemester(values, semesterId);
        long now = System.currentTimeMillis();
        values.put("created_at_ms", now);
        values.put("updated_at_ms", now);
        return helper.getWritableDatabase().insertOrThrow("subjects", null, values);
    }

    public boolean update(long id, String name, String teacher, String color) {
        return updateValues(id, formValues(name, teacher, color));
    }

    public boolean update(long id, String name, String teacher, String color, Long semesterId) {
        ContentValues values = formValues(name, teacher, color);
        putSemester(values, semesterId);
        return updateValues(id, values);
    }

    private void putSemester(ContentValues values, Long semesterId) {
        if (semesterId == null)
            values.putNull("semester_id");
        else {
            if (semesterId <= 0)
                throw new IllegalArgumentException("Học kỳ không hợp lệ.");
            values.put("semester_id", semesterId);
        }
    }

    public boolean setArchived(long id, boolean archived) {
        ContentValues values = new ContentValues();
        values.put("is_archived", archived ? 1 : 0);
        return updateValues(id, values);
    }

    private boolean updateValues(long id, ContentValues values) {
        SQLiteDatabase db = helper.getWritableDatabase();
        boolean changed;
        // Giữ updated_at hợp lệ ngay cả khi giờ hệ thống bị chỉnh lùi.
        db.beginTransaction();
        try {
            long created;
            try (Cursor cursor = db.query("subjects", new String[] {
                "created_at_ms"
            },
                    "id = ?", new String[] {
                String.valueOf(id)
            }, null, null, null)) {
                if (!cursor.moveToFirst())
                    return false;
                created = cursor.getLong(0);
            }

            values.put("updated_at_ms", Math.max(created, System.currentTimeMillis()));
            changed = db.update("subjects", values, "id = ?",
                    new String[] {
                String.valueOf(id)
            }) == 1;
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (changed && appContext != null) {
            ReminderCoordinator.refresh(appContext);
        }
        return changed;
    }

    private ContentValues formValues(String name, String teacher, String color) {
        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Vui lòng nhập tên môn học.");
        if (color == null || !color.matches("#[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("Màu môn học không hợp lệ.");
        ContentValues values = new ContentValues();
        values.put("name", name.trim());
        if (teacher == null || teacher.trim().isEmpty())
            values.putNull("teacher_name");
        else values.put("teacher_name", teacher.trim());
        values.put("color_hex", color);
        return values;
    }
}
