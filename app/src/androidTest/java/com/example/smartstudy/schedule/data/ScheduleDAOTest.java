package com.example.smartstudy.schedule.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.smartstudy.core.database.DatabaseHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ScheduleDAOTest {
    private File file;
    private SQLiteDatabase db;
    private DatabaseHelper helper;
    private ScheduleDAO dao;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        file = File.createTempFile("schedule-test-", ".db", context.getCacheDir());
        db = SQLiteDatabase.openOrCreateDatabase(file, null);
        helper = new DatabaseHelper(context) {
            @Override
            public SQLiteDatabase getWritableDatabase() {
                return db;
            }

            @Override
            public SQLiteDatabase getReadableDatabase() {
                return db;
            }
        };
        helper.onConfigure(db);
        helper.onCreate(db);
        db.execSQL("INSERT INTO subjects(id,name,color_hex,created_at_ms,updated_at_ms) "
                + "VALUES(1,'Java','#6C7EE1',1,1)");
        dao = new ScheduleDAO(helper);
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
        if (file != null) {
            SQLiteDatabase.deleteDatabase(file);
        }
    }

    private Schedule schedule(long id, int start, int end) {
        return new Schedule(id, 1, 1, start, end, "2026-10-19", "2026-12-31",
                "Asia/Ho_Chi_Minh", " A203 ", " Laptop ", 15);
    }

    @Test
    public void saveEditReopenAndDeletePreserveSubject() {
        long id = dao.save(schedule(0, 510, 630));
        assertEquals("A203", dao.getById(id).getRoom());
        assertEquals(Integer.valueOf(15), dao.getById(id).getRemindBeforeMinutes());
        assertEquals(id, dao.save(schedule(id, 540, 660)));
        db.close();
        db = SQLiteDatabase.openOrCreateDatabase(file, null);
        helper.onConfigure(db);
        assertEquals(540, dao.getById(id).getStartMinute());
        assertEquals("Java", dao.getAll().get(0).getSubjectName());
        assertTrue(dao.delete(id));
        assertTrue(dao.getAll().isEmpty());
        try (android.database.Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM subjects", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getInt(0));
        }
    }

    @Test
    public void conflictingSaveRollsBackAndAdjacentSaveWorks() {
        long id = dao.save(schedule(0, 510, 630));
        try {
            dao.save(schedule(0, 600, 660));
            fail("Phải báo trùng lịch");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Java"));
        }
        assertEquals(1, dao.getAll().size());
        assertEquals(510, dao.getById(id).getStartMinute());
        dao.save(schedule(0, 630, 690));
        assertEquals(2, dao.getAll().size());
    }

    @Test
    public void calendarSnapshotReadsMovedRoomAndDefaultTimeZone() {
        long id = dao.save(schedule(0, 510, 630));
        db.execSQL("INSERT INTO schedule_exceptions(schedule_id,original_date,action,new_date,"
                + "new_start_minute,new_end_minute,new_room,created_at_ms,updated_at_ms) "
                + "VALUES(?,'2026-10-19','rescheduled','2026-10-20',600,660,'B102',1,1)",
                new Object[]{id});
        ScheduleDAO.CalendarData data = dao.getCalendarData();
        assertEquals("Asia/Ho_Chi_Minh", data.timeZoneId);
        java.util.List<ScheduleOccurrence> events = ScheduleCalendar.buildWeek(
                java.time.LocalDate.of(2026, 10, 19), java.time.ZoneId.of(data.timeZoneId),
                data.schedules, data.exceptions);
        assertEquals(1, events.size());
        assertEquals("2026-10-20", events.get(0).getDate().toString());
        assertEquals("B102", events.get(0).getRoom());
    }

    @Test
    public void archivedSubjectsAndExceptionsAreHandledSafely() {
        long id = dao.save(schedule(0, 510, 630));
        db.execSQL("INSERT INTO schedule_exceptions(schedule_id,original_date,action,created_at_ms,updated_at_ms) "
                + "VALUES(?,'2026-10-19','cancelled',1,1)", new Object[]{id});
        try {
            dao.save(schedule(id, 540, 660));
            fail("Chưa hỗ trợ sửa cả chuỗi có ngoại lệ");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("đổi/hủy"));
        }
        db.execSQL("UPDATE subjects SET is_archived=1 WHERE id=1");
        assertTrue(dao.getAll().isEmpty());
        try {
            dao.save(schedule(0, 700, 800));
            fail("Không tạo lịch cho môn lưu trữ");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Môn"));
        }
        assertTrue(dao.delete(id));
        try (android.database.Cursor c = db.rawQuery("SELECT COUNT(*) FROM schedule_exceptions", null)) {
            assertTrue(c.moveToFirst());
            assertEquals(0, c.getInt(0));
        }
    }
}
