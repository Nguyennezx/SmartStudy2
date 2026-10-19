package com.example.smartstudy.subject.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class SemesterTest {
    @Test public void validRangeAndLeapDayAreAccepted() {
        Semester.validate(" Học kỳ 1 ", "2026-09-01", "2026-12-31");
        Semester.validate("Một ngày", "2028-02-29", "2028-02-29");
        assertEquals("01/09/2026 – 31/12/2026",
                new Semester(1, "HK1", "2026-09-01", "2026-12-31", false).getDateRange());
    }

    @Test public void badDatesBlankNameAndReversedRangeAreRejected() {
        reject(" ", "2026-09-01", "2026-12-31");
        reject(null, "2026-09-01", "2026-12-31");
        reject("HK1", "2026-02-29", "2026-12-31");
        reject("HK1", "2026-04-31", "2026-12-31");
        reject("HK1", "2026-12-31", "2026-09-01");
        reject("HK1", null, "2026-09-01");
        reject("HK1", "01/09/2026", "2026-12-31");
        reject("HK1", "0000-01-01", "2026-12-31");
    }

    private void reject(String name, String start, String end) {
        try {
            Semester.validate(name, start, end);
            fail("Phải từ chối dữ liệu không hợp lệ");
        } catch (IllegalArgumentException expected) {
        }
    }
}
