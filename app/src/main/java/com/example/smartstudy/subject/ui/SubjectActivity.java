package com.example.smartstudy.subject.ui;

import android.os.Bundle;
import android.content.Intent;
import android.widget.AdapterView;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.smartstudy.R;
import com.example.smartstudy.subject.data.Subject;
import com.example.smartstudy.subject.data.SubjectDAO;
import com.example.smartstudy.subject.data.Semester;
import com.example.smartstudy.subject.data.SemesterDAO;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SubjectActivity extends AppCompatActivity {
    // Chung một hàng đợi: khi xoay màn hình, lần đọc mới chờ lần ghi cũ hoàn tất.
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SubjectDAO dao;
    private SemesterDAO semesterDAO;
    private List<Semester> semesters = new java.util.ArrayList<>();
    private Spinner semesterFilter;
    private long selectedSemester = SubjectDAO.ALL_SEMESTERS;
    private Bundle pendingDraft;
    private int loadVersion;

    private static class SemesterChoice {
        final long id;
        final String title;
        SemesterChoice(long id, String title) {
            this.id = id;
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private SubjectAdapter adapter;
    private TextView status;
    private CheckBox archivedFilter;
    private Button addButton;
    private boolean busy;
    private AlertDialog formDialog;
    private Subject editingSubject;
    private boolean saving;
    private static final int[] COLOR_IDS = {
        R.color.PeriwinkleBlue, R.color.PowderBlue,
        R.color.Peach, R.color.PastelPink, R.color.LightOrchid
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_subject);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.subject_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        dao = new SubjectDAO(this);
        semesterDAO = new SemesterDAO(this);
        ListView list = findViewById(R.id.subject_list);
        View filters = getLayoutInflater().inflate(R.layout.view_subject_filters, list, false);
        list.addHeaderView(filters, null, false);
        semesterFilter = filters.findViewById(R.id.subject_semester_filter);
        if (state != null)
            selectedSemester = state.getLong("semester_filter", SubjectDAO.ALL_SEMESTERS);
        if (state != null && state.getBoolean("form_open"))
            pendingDraft = new Bundle(state);
        adapter = new SubjectAdapter(this);
        status = filters.findViewById(R.id.subject_status);
        archivedFilter = filters.findViewById(R.id.subject_archived_filter);
        if (state != null)
            archivedFilter.setChecked(state.getBoolean("archived_filter"));
        addButton = findViewById(R.id.subject_add);
        list.setAdapter(adapter);
        findViewById(R.id.subject_back).setOnClickListener(v -> finish());
        addButton.setOnClickListener(v -> showForm(null));
        archivedFilter.setOnCheckedChangeListener((button, checked) -> loadSubjects());
        list.setOnItemClickListener((parent, view, position, id) -> {
            int index = position - list.getHeaderViewsCount();
            if (!busy && index >= 0 && index < adapter.getCount())
                showActions(adapter.getItem(index));
        });
        filters.findViewById(R.id.subject_manage_semesters).setOnClickListener(v ->
                startActivity(new Intent(this, SemesterActivity.class)));
        semesterFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SemesterChoice choice = (SemesterChoice) parent.getItemAtPosition(position);
                if (choice.id != selectedSemester) {
                    selectedSemester = choice.id;
                    loadSubjects();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSubjects();
    }

    private void restoreDraft() {
        Bundle state = pendingDraft;
        pendingDraft = null;
        if (state != null) {
            Subject subject = state.getLong("editing_id", -1) == -1 ? null
            : new Subject(state.getLong("editing_id"), state.getString("original_name"),
                    state.getString("original_teacher"), state.getString("original_color"), false,
                    state.containsKey("original_semester_id") ? state.getLong("original_semester_id") : null,
                    state.getString("original_semester_name"));
            showForm(subject);
            ((EditText) formDialog.findViewById(R.id.subject_input_name)).setText(state.getString("draft_name"));
            ((EditText) formDialog.findViewById(R.id.subject_input_teacher)).setText(state.getString("draft_teacher"));
            ((Spinner) formDialog.findViewById(R.id.subject_input_color)).setSelection(state.getInt("draft_color"));
            Spinner semester = formDialog.findViewById(R.id.subject_input_semester);
            long draftId = state.getLong("draft_semester", 0);
            for (int i = 0; i < semester.getCount(); i++)
                if (((SemesterChoice) semester.getItemAtPosition(i)).id == draftId)
                semester.setSelection(i);
        }
    }

    private void loadSubjects() {
        status.setOnClickListener(null);
        boolean archived = archivedFilter.isChecked();
        long filterId = selectedSemester;
        int version = ++loadVersion;
        setBusy(true);
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.subject_loading);
        executor.execute(() -> {
            try {
                List<Semester> loadedSemesters = semesterDAO.getAll();
                List<Subject> subjects = dao.getByArchived(archived, filterId);
                runOnUiThread(() -> {
                    if (!alive() || version != loadVersion)
                        return;
                    semesters = loadedSemesters;
                    updateSemesterFilter();
                    adapter.setSubjects(subjects);
                    setBusy(false);
                    status.setText(archived ? R.string.subject_empty_archived
                            : filterId == SubjectDAO.ALL_SEMESTERS
                                    ? R.string.subject_empty : R.string.subject_empty_filter);
                    status.setVisibility(subjects.isEmpty() ? View.VISIBLE : View.GONE);
                    restoreDraft();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!alive() || version != loadVersion)
                        return;
                    adapter.setSubjects(java.util.Collections.emptyList());
                    setBusy(false);
                    status.setText(R.string.subject_load_error);
                    status.setVisibility(View.VISIBLE);
                    status.setOnClickListener(v -> {
                        status.setOnClickListener(null);
                        loadSubjects();
                    });
                });
            }
        });
    }

    private void updateSemesterFilter() {
        List<SemesterChoice> choices = new java.util.ArrayList<>();
        choices.add(new SemesterChoice(SubjectDAO.ALL_SEMESTERS, getString(R.string.semester_all)));
        choices.add(new SemesterChoice(0, getString(R.string.semester_unassigned)));
        int selected = 0;
        for (Semester semester : semesters) {
            choices.add(new SemesterChoice(semester.getId(), semester.toString()));
            if (semester.getId() == selectedSemester)
                selected = choices.size() - 1;
        }

        if (selectedSemester == 0)
            selected = 1;
        selectedSemester = choices.get(selected).id;
        semesterFilter.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, choices));
        semesterFilter.setSelection(selected);
    }

    private void showActions(Subject subject) {
        if (subject.isArchived()) {
            new AlertDialog.Builder(this).setTitle(subject.getName())
                    .setMessage(R.string.subject_restore_message)
                    .setNegativeButton(R.string.subject_cancel, null)
                    .setPositiveButton(R.string.subject_restore, (d, w) -> changeArchive(subject, false)).show();
        } else {
            new AlertDialog.Builder(this).setTitle(subject.getName())
                    .setItems(R.array.subject_actions, (d, which) -> {
                if (which == 0)
                    showForm(subject);
                else new AlertDialog.Builder(this).setTitle(R.string.subject_archive)
                        .setMessage(R.string.subject_archive_message)
                        .setNegativeButton(R.string.subject_cancel, null)
                        .setPositiveButton(R.string.subject_archive,
                        (dialog, button) -> changeArchive(subject, true)).show();
            }).show();
        }
    }

    private void changeArchive(Subject subject, boolean archived) {
        setBusy(true);
        executor.execute(() -> {
            try {
                if (!dao.setArchived(subject.getId(), archived))
                    throw new IllegalStateException();
                runOnUiThread(() -> {
                    if (!alive())
                        return;
                    toast(archived ? R.string.subject_archived : R.string.subject_restored);
                    loadSubjects();
                });
            } catch (Exception error) {
                showWriteError();
            }
        });
    }

    private String colorHex(int index) {
        return String.format(Locale.ROOT, "#%06X", ContextCompat.getColor(this, COLOR_IDS[index]) & 0xFFFFFF);
    }

    private void showForm(Subject subject) {
        editingSubject = subject;
        View form = getLayoutInflater().inflate(R.layout.dialog_subject, null);
        EditText name = form.findViewById(R.id.subject_input_name);
        EditText teacher = form.findViewById(R.id.subject_input_teacher);
        Spinner colors = form.findViewById(R.id.subject_input_color);
        View preview = form.findViewById(R.id.subject_color_preview);
        Spinner semesterSpinner = form.findViewById(R.id.subject_input_semester);
        List<SemesterChoice> choices = new java.util.ArrayList<>();
        choices.add(new SemesterChoice(0, getString(R.string.semester_unassigned)));
        long currentId = subject == null ? Math.max(0, selectedSemester)
        : subject.getSemesterId() == null ? 0 : subject.getSemesterId();
        int selected = 0;
        for (Semester semester : semesters) {
            if (!semester.isArchived() || semester.getId() == currentId) {
                choices.add(new SemesterChoice(semester.getId(), semester.toString()));
                if (semester.getId() == currentId)
                    selected = choices.size() - 1;
            }
        }

        semesterSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, choices));
        semesterSpinner.setSelection(selected);
        java.util.List<String> colorNames = new java.util.ArrayList<>(java.util.Arrays.asList(
                getResources().getStringArray(R.array.subject_colors)));
        if (subject != null)
            colorNames.add(getString(R.string.subject_current_color));
        colors.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, colorNames));
        colors.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                int color = pos < COLOR_IDS.length
                ? ContextCompat.getColor(SubjectActivity.this, COLOR_IDS[pos])
                : android.graphics.Color.parseColor(subject.getColorHex());
                preview.setBackgroundTintList(ColorStateList.valueOf(color));
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        if (subject != null) {
            name.setText(subject.getName());
            teacher.setText(subject.getTeacherName());
            colors.setSelection(COLOR_IDS.length);
            for (int i = 0; i < COLOR_IDS.length; i++)
                if (colorHex(i).equalsIgnoreCase(subject.getColorHex()))
                colors.setSelection(i);
        }

        formDialog = new AlertDialog.Builder(this)
                .setTitle(subject == null ? R.string.subject_add : R.string.subject_edit)
                .setView(form).setNegativeButton(R.string.subject_cancel, null)
                .setPositiveButton(R.string.subject_save, null).create();
        AlertDialog dialog = formDialog;
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String subjectName = name.getText().toString().trim();
            if (subjectName.isEmpty()) {
                name.setError(getString(R.string.subject_name_required));
                name.requestFocus();
                return;
            }

            String teacherName = teacher.getText().toString().trim();
            int position = colors.getSelectedItemPosition();
            String color = position < COLOR_IDS.length ? colorHex(position) : subject.getColorHex();
            final String selectedColor = color;
            long chosenSemester = ((SemesterChoice) semesterSpinner.getSelectedItem()).id;
            Long semesterId = chosenSemester == 0 ? null : chosenSemester;
            saving = true;
            dialog.setCancelable(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            executor.execute(() -> {
                try {
                    if (subject == null)
                        dao.insert(subjectName, teacherName, selectedColor, semesterId);
                    else if (!dao.update(subject.getId(), subjectName, teacherName, selectedColor, semesterId))
                        throw new IllegalStateException();
                    runOnUiThread(() -> {
                        if (!alive())
                            return;
                        saving = false;
                        dialog.dismiss();
                        toast(R.string.subject_saved);
                        // Đưa bộ lọc tới học kỳ vừa lưu để người dùng luôn thấy môn.
                        selectedSemester = chosenSemester;
                        if (subject == null && archivedFilter.isChecked())
                            archivedFilter.setChecked(false);
                        else loadSubjects();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        if (!alive())
                            return;
                        saving = false;
                        dialog.setCancelable(true);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                        toast(R.string.subject_write_error);
                    });
                }
            });
        }));
        dialog.show();
    }

    private void setBusy(boolean value) {
        busy = value;
        addButton.setEnabled(!value);
        archivedFilter.setEnabled(!value);
        semesterFilter.setEnabled(!value);
    }

    private void showWriteError() {
        runOnUiThread(() -> {
            if (!alive())
                return;
            setBusy(false);
            toast(R.string.subject_write_error);
        });
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    private void toast(int message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("archived_filter", archivedFilter.isChecked());
        state.putLong("semester_filter", selectedSemester);
        if (pendingDraft != null)
            state.putAll(pendingDraft);
        if (formDialog != null && formDialog.isShowing() && !saving) {
            state.putBoolean("form_open", true);
            state.putString("draft_name",
                    ((EditText) formDialog.findViewById(R.id.subject_input_name)).getText().toString());
            state.putString("draft_teacher",
                    ((EditText) formDialog.findViewById(R.id.subject_input_teacher)).getText().toString());
            state.putLong("draft_semester",
                    ((SemesterChoice) ((Spinner) formDialog.findViewById(R.id.subject_input_semester)).getSelectedItem()).id);
            state.putInt("draft_color",
                    ((Spinner) formDialog.findViewById(R.id.subject_input_color)).getSelectedItemPosition());
            if (editingSubject != null) {
                state.putLong("editing_id", editingSubject.getId());
                state.putString("original_name", editingSubject.getName());
                state.putString("original_teacher", editingSubject.getTeacherName());
                state.putString("original_color", editingSubject.getColorHex());
                if (editingSubject.getSemesterId() != null)
                    state.putLong("original_semester_id", editingSubject.getSemesterId());
                state.putString("original_semester_name", editingSubject.getSemesterName());
            }
        }

        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        if (formDialog != null)
            formDialog.dismiss();
        super.onDestroy();
    }
}
