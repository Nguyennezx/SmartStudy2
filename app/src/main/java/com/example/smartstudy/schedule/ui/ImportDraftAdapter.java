package com.example.smartstudy.schedule.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;
import com.example.smartstudy.R;
import com.example.smartstudy.schedule.data.ImportLessonDraft;
import com.example.smartstudy.schedule.data.ScheduleRules;
import java.util.List;

public final class ImportDraftAdapter extends BaseAdapter {
    private final List<ImportLessonDraft> rows;
    private final Runnable changed;

    public ImportDraftAdapter(List<ImportLessonDraft> rows, Runnable changed) {
        this.rows = rows;
        this.changed = changed;
    }

    @Override public int getCount() { return rows.size(); }
    @Override public ImportLessonDraft getItem(int position) { return rows.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View view, ViewGroup parent) {
        if (view == null) view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_import_draft, parent, false);
        ImportLessonDraft row = getItem(position);
        ((TextView) view.findViewById(R.id.import_row_title)).setText(row.subject.isEmpty() ? "Chưa đọc được tên môn" : row.subject);
        String day = row.day == 0 ? "Chưa rõ thứ" : row.day == 7 ? "Chủ nhật" : "Thứ " + (row.day + 1);
        String hours = row.start < 0 || row.end < 0 ? "Cần bổ sung giờ" : ScheduleRules.formatMinute(row.start) + " – " + ScheduleRules.formatMinute(row.end);
        ((TextView) view.findViewById(R.id.import_row_detail)).setText(day + " · " + hours + "\nPhòng: " + (row.room.isEmpty() ? "Chưa có" : row.room)
                + (row.teacher.isEmpty() ? "" : "\nGiảng viên: " + row.teacher));
        ((TextView) view.findViewById(R.id.import_row_status)).setText(row.saved ? "Đã lưu" : row.skipped ? "Đã bỏ chọn" : row.problem);
        CheckBox selected = view.findViewById(R.id.import_row_check);
        selected.setOnCheckedChangeListener(null);
        selected.setChecked(!row.skipped && !row.saved);
        selected.setEnabled(!row.saved);
        selected.setOnCheckedChangeListener((button, checked) -> { row.skipped = !checked; changed.run(); notifyDataSetChanged(); });
        view.setAlpha(row.saved || row.skipped ? 0.55f : 1f);
        return view;
    }
}
