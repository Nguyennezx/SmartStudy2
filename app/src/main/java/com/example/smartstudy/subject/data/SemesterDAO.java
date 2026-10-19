package com.example.smartstudy.subject.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.smartstudy.core.database.DatabaseHelper;
import java.util.ArrayList;
import java.util.List;

/** Dùng cùng Helper với SubjectDAO; gọi trên luồng nền. */
public class SemesterDAO {
    private final DatabaseHelper helper;
    public SemesterDAO(Context context) {
        helper = DatabaseHelper.getInstance(context);
    }

    SemesterDAO(DatabaseHelper helper) {
        this.helper = helper;
    }

    public List<Semester> getAll() {
        List<Semester> semesters = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().query("semesters", null, null, null,
                null, null, "start_date DESC, id DESC")) {
            while (c.moveToNext())
                semesters.add(new Semester(
                    c.getLong(c.getColumnIndexOrThrow("id")),
                    c.getString(c.getColumnIndexOrThrow("name")),
                    c.getString(c.getColumnIndexOrThrow("start_date")),
                    c.getString(c.getColumnIndexOrThrow("end_date")),
                    c.getInt(c.getColumnIndexOrThrow("is_archived")) == 1));
        }

        return semesters;
    }

    public long insert(String name, String start, String end) {
        ContentValues values = values(name, start, end);
        long now = System.currentTimeMillis();
        values.put("created_at_ms", now);
        values.put("updated_at_ms", now);
        return helper.getWritableDatabase().insertOrThrow("semesters", null, values);
    }

    public boolean update(long id, String name, String start, String end) {
        ContentValues values = values(name, start, end);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            long created;
            try (Cursor c = db.query("semesters", new String[] {
                "created_at_ms"
            }, "id=?",
                    new String[] {
                String.valueOf(id)
            }, null, null, null)) {
                if (!c.moveToFirst())
                    return false;
                created = c.getLong(0);
            }

            values.put("updated_at_ms", Math.max(created, System.currentTimeMillis()));
            boolean changed = db.update("semesters", values, "id=?", new String[] {
                String.valueOf(id)
            }) == 1;
            db.setTransactionSuccessful();
            return changed;
        } finally {
            db.endTransaction();
        }
    }

    private ContentValues values(String name, String start, String end) {
        Semester.validate(name, start, end);
        ContentValues values = new ContentValues();
        values.put("name", name.trim());
        values.put("start_date", start);
        values.put("end_date", end);
        return values;
    }
}
