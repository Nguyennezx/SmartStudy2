package com.example.smartstudy.core.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "smart_study.db";
    public static final int DATABASE_VERSION = 1;
    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);

        // Bật kiểm tra khóa ngoại.
        db.setForeignKeyConstraintsEnabled(true);
    }


    @Override
    public void onCreate(SQLiteDatabase db) {
        // Tạo bảng, index và view theo thứ tự.
        for (String sql : DatabaseSchema.CREATE_STATEMENTS) {
            db.execSQL(sql);
        }

        // Tạo hồ sơ và cấu hình mặc định.
        long now = System.currentTimeMillis();
        insertDefaultRow(db, "user_profile", now);
        insertDefaultRow(db, "app_settings", now);
    }

    private void insertDefaultRow(
            SQLiteDatabase db, String table, long now) {

        ContentValues values = new ContentValues();
        values.put("id", 1);
        values.put("created_at_ms", now);
        values.put("updated_at_ms", now);

        db.insertOrThrow(table, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        // Viết migration giữ dữ liệu trước khi tăng version.
        throw new SQLiteException(
                "Missing migration from " + oldVersion + " to " + newVersion);
    }
}

