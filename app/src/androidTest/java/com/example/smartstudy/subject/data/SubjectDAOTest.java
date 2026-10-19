package com.example.smartstudy.subject.data;

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

/** SQLite thật trên file riêng; không ghi database của ứng dụng. */
@RunWith(AndroidJUnit4.class)
public class SubjectDAOTest {
    private File file;
    private SQLiteDatabase db;
    private DatabaseHelper helper;
    private SubjectDAO dao;

    @Before public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        file = File.createTempFile("subjects-test-", ".db", context.getCacheDir());
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
        dao = new SubjectDAO(helper);
    }

    @After public void tearDown() {
        if (db != null)
            db.close();
        if (file != null)
            SQLiteDatabase.deleteDatabase(file);
    }

    @Test public void editArchiveRestoreAndReopenKeepRelatedData() {
        long id = dao.insert("  Lập trình Java  ", "  Nguyễn Văn A  ", "#6C7EE1");
        assertEquals("Lập trình Java", dao.getActiveSubjects().get(0).getName());
        db.execSQL("INSERT INTO semesters(id,name,start_date,end_date,created_at_ms,updated_at_ms) "
                + "VALUES(1,'HK1','2026-09-01','2026-12-31',1,1)");
        db.execSQL("UPDATE subjects SET semester_id=1 WHERE id=?", new Object[] {
            id
        });
        db.execSQL("INSERT INTO assignments(subject_id,title,created_at_ms,updated_at_ms) VALUES(?,'Bài Java',1,1)", new Object[] {
            id
        });
        assertTrue(dao.update(id, "Java nâng cao", "", "#FFC4A4"));
        assertNull(dao.getActiveSubjects().get(0).getTeacherName());
        assertEquals("#FFC4A4", dao.getActiveSubjects().get(0).getColorHex());
        assertTrue(dao.setArchived(id, true));
        assertTrue(dao.getActiveSubjects().isEmpty());
        assertEquals(id, dao.getByArchived(true).get(0).getId());
        assertTrue(dao.setArchived(id, false));
        db.close();
        db = SQLiteDatabase.openOrCreateDatabase(file, null);
        helper.onConfigure(db);
        assertEquals("Java nâng cao", dao.getActiveSubjects().get(0).getName());
        try (android.database.Cursor cursor = db.rawQuery("SELECT semester_id, (SELECT COUNT(*) FROM assignments WHERE subject_id=?) FROM subjects WHERE id=?",
                new String[] {
            String.valueOf(id), String.valueOf(id)
        })) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getInt(0));
            assertEquals(1, cursor.getInt(1));
        }

        assertFalse(dao.update(-1, "Môn không tồn tại", "", "#6C7EE1"));
    }

    @Test public void semesterAssignmentFiltersAndRenameKeepSubjectIdentity() {
        SemesterDAO semesters = new SemesterDAO(helper);
        long first = semesters.insert("Học kỳ 1", "2026-09-01", "2026-12-31");
        long second = semesters.insert("Học kỳ 2", "2027-01-01", "2027-05-31");
        long id = dao.insert("Java", "", "#6C7EE1", first);
        dao.insert("SQL", "", "#FFC4A4");
        assertEquals(1, dao.getByArchived(false, first).size());
        assertEquals(1, dao.getByArchived(false, SubjectDAO.UNASSIGNED_SEMESTER).size());
        assertEquals(2, dao.getActiveSubjects().size());
        assertTrue(semesters.update(first, "Học kỳ một", "2026-09-02", "2026-12-31"));
        assertEquals("Học kỳ một", dao.getByArchived(false, first).get(0).getSemesterName());
        assertTrue(dao.update(id, "Java", "", "#6C7EE1", second));
        assertTrue(dao.getByArchived(false, first).isEmpty());
        assertEquals(id, dao.getByArchived(false, second).get(0).getId());
        assertEquals(Long.valueOf(second), dao.getByArchived(false, second).get(0).getSemesterId());
        assertTrue(dao.update(id, "Java", "", "#6C7EE1", null));
        assertEquals(2, dao.getByArchived(false, SubjectDAO.UNASSIGNED_SEMESTER).size());
        assertTrue(dao.setArchived(id, true));
        assertEquals(id, dao.getByArchived(true, 0).get(0).getId());
        db.close();
        db = SQLiteDatabase.openOrCreateDatabase(file, null);
        helper.onConfigure(db);
        assertEquals(2, semesters.getAll().size());
        assertNull(dao.getByArchived(true, 0).get(0).getSemesterId());
    }

    @Test public void invalidSemesterDoesNotInsertOrModifyData() {
        SemesterDAO semesters = new SemesterDAO(helper);
        long id = semesters.insert("HK1", "2026-09-01", "2026-12-31");
        try {
            semesters.update(id, "Sai ngày", "2026-12-31", "2026-09-01");
            fail("Ngày kết thúc trước bắt đầu phải bị từ chối");
        } catch (IllegalArgumentException expected) {
        }

        assertEquals("HK1", semesters.getAll().get(0).getName());
        try {
            dao.insert("Java", "", "#6C7EE1", 999999L);
            fail("Học kỳ không tồn tại phải bị khóa ngoại từ chối");
        } catch (android.database.sqlite.SQLiteConstraintException expected) {
        }

        assertTrue(dao.getActiveSubjects().isEmpty());
    }

    @Test public void emptyNameAndInvalidColorAreRejected() {
        try {
            dao.insert("   ", "", "#6C7EE1");
            fail("Tên rỗng phải bị từ chối");
        } catch (IllegalArgumentException expected) {
        }

        try {
            dao.insert("Java", "", "blue");
            fail("Màu sai phải bị từ chối");
        } catch (IllegalArgumentException expected) {
        }

        assertTrue(dao.getActiveSubjects().isEmpty());
    }
}
