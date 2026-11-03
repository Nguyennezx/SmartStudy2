package com.example.smartstudy.schedule.data;

import static org.junit.Assert.*;
import org.json.JSONObject;
import org.junit.Test;

/** Requires an Android runner (org.json/Bundle); not counted as JVM tests. */
public class ImportLessonDraftTest {
    private JSONObject source() throws Exception {
        return new JSONObject("{\"observed_scope\":\"undated_week\",\"date_evidence\":\"inferred_year\","
                + "\"lessons\":[{\"subject_text\":\"Môn A\",\"weekday\":\"WED\",\"start_time\":\"08:00\","
                + "\"end_time\":\"10:00\",\"time_basis\":\"explicit\",\"recurrence_kind\":\"single\","
                + "\"date\":\"2026-10-21\",\"source_text\":\"Môn A 8-10h\"}]}");
    }

    @Test public void missingYearDoesNotCreateSemesterDefaultsOrSelectSubject() throws Exception {
        ImportLessonDraft row = ImportLessonDraft.parse(source().toString()).get(0);
        assertTrue(row.supported);
        assertNull(row.from);
        assertNull(row.to);
        assertEquals(0, row.toForm().getLong("subject_id"));
        assertEquals(3, row.toForm().getInt("day"));
    }

    @Test public void unconvertedPeriodsKeepMissingHours() throws Exception {
        JSONObject doc = source();
        doc.getJSONArray("lessons").getJSONObject(0).put("time_basis", "periods");
        ImportLessonDraft row = ImportLessonDraft.parse(doc.toString()).get(0);
        assertEquals(-1, row.start);
        assertEquals(-1, row.end);
    }

    @Test public void unknownWeekRulesAndMakeupAreBlockedForTerm() throws Exception {
        JSONObject doc = source().put("observed_scope", "term");
        JSONObject row = doc.getJSONArray("lessons").getJSONObject(0);
        row.put("recurrence_kind", "weekly").put("week_rule", new JSONObject("{\"kind\":\"odd_weeks\",\"weeks\":[1,3]}"));
        assertFalse(ImportLessonDraft.parse(doc.toString()).get(0).supported);
        row.put("week_rule", new JSONObject("{\"kind\":\"every_week\",\"weeks\":[]}")).put("is_makeup", true);
        assertFalse(ImportLessonDraft.parse(doc.toString()).get(0).supported);
        row.put("is_makeup", false);
        assertTrue(ImportLessonDraft.parse(doc.toString()).get(0).supported);
    }
}
