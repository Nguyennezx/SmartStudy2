package com.example.smartstudy.schedule.data;

import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Dữ liệu tạm để đối chiếu; không tự tạo môn hoặc ghi database. */
public final class ImportLessonDraft {
    public String subject, teacher, room, note, source, problem;
    public int day, start = -1, end = -1;
    public String from, to;
    public boolean supported, skipped, saved;

    private static String text(JSONObject object, String key) {
        return object.isNull(key) ? "" : object.optString(key, "");
    }

    private static int clock(String value, boolean end) {
        if (end && "24:00".equals(value)) return 1440;
        if (!value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) return -1;
        return Integer.parseInt(value.substring(0, 2)) * 60 + Integer.parseInt(value.substring(3));
    }

    public static List<ImportLessonDraft> parse(String json) throws JSONException {
        if (json == null || json.length() > 200000) throw new JSONException("Bản nháp quá lớn.");
        JSONObject envelope = new JSONObject(json);
        JSONObject doc = envelope.optJSONObject("extraction");
        if (doc == null) doc = envelope;
        JSONArray rows = doc.getJSONArray("lessons");
        if (rows.length() > 100) throw new JSONException("Bản nháp tối đa 100 buổi.");
        List<ImportLessonDraft> result = new ArrayList<>();
        String scope = text(doc, "observed_scope");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            ImportLessonDraft draft = new ImportLessonDraft();
            draft.subject = text(row, "subject_text");
            draft.teacher = text(row, "teacher_text");
            draft.room = text(row, "room");
            draft.note = text(row, "note_text");
            draft.source = text(row, "source_text");
            String[] days = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
            for (int j = 0; j < days.length; j++) if (days[j].equals(text(row, "weekday"))) draft.day = j + 1;
            if ("explicit".equals(text(row, "time_basis"))) {
                draft.start = clock(text(row, "start_time"), false);
                draft.end = clock(text(row, "end_time"), true);
            }
            String recurrence = text(row, "recurrence_kind");
            JSONObject rule = row.optJSONObject("week_rule");
            JSONArray weeks = rule == null ? null : rule.optJSONArray("weeks");
            boolean weekly = rule != null && "every_week".equals(text(rule, "kind"))
                    && weeks != null && weeks.length() == 0;
            draft.supported = (("dated_week".equals(scope) || "undated_week".equals(scope))
                    && "single".equals(recurrence))
                    || ("term".equals(scope) && "weekly".equals(recurrence) && weekly
                    && Boolean.FALSE.equals(row.opt("is_makeup")));
            if (!draft.supported) draft.problem = "Quy tắc tuần hoặc lịch bù cần xử lý riêng; chưa đưa vào form.";
            else draft.problem = "Đối chiếu ảnh, chọn môn và bổ sung các trường còn thiếu trước khi lưu.";
            if (!"term".equals(scope) && "full_year_visible".equals(text(doc, "date_evidence"))) {
                try {
                    LocalDate date = LocalDate.parse(text(row, "date"));
                    if (date.getDayOfWeek().getValue() == draft.day) draft.from = draft.to = date.toString();
                } catch (RuntimeException ignored) { /* Giữ trống để người dùng chọn. */ }
            }
            if ("term".equals(scope)) draft.problem += " Chọn ngày đầu và cuối khoảng lặp.";
            result.add(draft);
        }
        return result;
    }

    public Bundle toForm() {
        Bundle data = new Bundle();
        data.putLong("subject_id", 0); // Không tự chọn môn đầu danh sách hoặc tin ID do AI trả.
        data.putString("import_subject_name", subject);
        data.putString("import_teacher_name", teacher);
        data.putInt("day", day);
        data.putInt("start", start);
        data.putInt("end", end);
        data.putString("from", from);
        data.putString("to", to);
        data.putString("room", room);
        data.putString("note", note);
        data.putInt("reminder", -1);
        return data;
    }
}
