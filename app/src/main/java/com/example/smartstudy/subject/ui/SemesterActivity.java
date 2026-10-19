package com.example.smartstudy.subject.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.smartstudy.R;
import com.example.smartstudy.subject.data.Semester;
import com.example.smartstudy.subject.data.SemesterDAO;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SemesterActivity extends AppCompatActivity {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SemesterDAO dao;
    private SemesterAdapter adapter;
    private TextView status;
    private Button add;
    private boolean busy, saving;
    private AlertDialog formDialog;
    private DatePickerDialog dateDialog;
    private long editingId = -1;
    private View form;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_semester);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.semester_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        dao = new SemesterDAO(this);
        adapter = new SemesterAdapter(this);
        status = findViewById(R.id.semester_status);
        add = findViewById(R.id.semester_add);
        ListView list = findViewById(R.id.semester_list);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (!busy)
                showForm(adapter.getItem(position), null);
        });
        add.setOnClickListener(v -> showForm(null, null));
        findViewById(R.id.semester_back).setOnClickListener(v -> finish());
        load();
        if (state != null && state.getBoolean("form_open"))
            showForm(null, state);
    }

    private void load() {
        setBusy(true);
        status.setOnClickListener(null);
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.semester_loading);
        executor.execute(() -> {
            try {
                List<Semester> semesters = dao.getAll();
                runOnUiThread(() -> {
                    if (!alive())
                        return;
                    adapter.setItems(semesters);
                    setBusy(false);
                    status.setText(R.string.semester_empty);
                    status.setVisibility(semesters.isEmpty() ? View.VISIBLE : View.GONE);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!alive())
                        return;
                    setBusy(false);
                    status.setText(R.string.semester_load_error);
                    status.setVisibility(View.VISIBLE);
                    status.setOnClickListener(v -> load());
                });
            }
        });
    }

    private void showForm(Semester semester, Bundle draft) {
        editingId = draft != null ? draft.getLong("editing_id", -1) : semester == null ? -1 : semester.getId();
        form = getLayoutInflater().inflate(R.layout.dialog_semester, null);
        EditText name = form.findViewById(R.id.semester_input_name);
        Button start = form.findViewById(R.id.semester_input_start);
        Button end = form.findViewById(R.id.semester_input_end);
        TextView error = form.findViewById(R.id.semester_form_error);
        LocalDate today = LocalDate.now();
        name.setText(draft != null ? draft.getString("draft_name") : semester == null ? "" : semester.getName());
        setDate(start, draft != null ? draft.getString("draft_start") : semester == null
                ? today.toString() : semester.getStartDate());
        setDate(end, draft != null ? draft.getString("draft_end") : semester == null
                ? today.plusMonths(4).toString() : semester.getEndDate());
        start.setOnClickListener(v -> pickDate(start));
        end.setOnClickListener(v -> pickDate(end));
        formDialog = new AlertDialog.Builder(this).setTitle(editingId == -1
                ? R.string.semester_add : R.string.semester_edit)
                .setView(form).setNegativeButton(R.string.subject_cancel, null)
                .setPositiveButton(R.string.subject_save, null).create();
        AlertDialog dialog = formDialog;
        long id = editingId;
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = name.getText().toString().trim();
            String first = (String) start.getTag(), last = (String) end.getTag();
            try {
                Semester.validate(title, first, last);
            } catch (IllegalArgumentException invalid) {
                error.setText(invalid.getMessage());
                error.setVisibility(View.VISIBLE);
                if (title.isEmpty()) {
                    name.setError(invalid.getMessage());
                    name.requestFocus();
                }

                return;
            }

            error.setVisibility(View.GONE);
            saving = true;
            dialog.setCancelable(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            executor.execute(() -> {
                try {
                    if (id == -1)
                        dao.insert(title, first, last);
                    else if (!dao.update(id, title, first, last))
                        throw new IllegalStateException();
                    runOnUiThread(() -> {
                        if (!alive())
                            return;
                        saving = false;
                        dialog.dismiss();
                        Toast.makeText(this, R.string.semester_saved, Toast.LENGTH_SHORT).show();
                        load();
                    });
                } catch (Exception failure) {
                    runOnUiThread(() -> {
                        if (!alive())
                            return;
                        saving = false;
                        dialog.setCancelable(true);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                        error.setText(R.string.subject_write_error);
                        error.setVisibility(View.VISIBLE);
                    });
                }
            });
        }));
        dialog.show();
    }

    private void pickDate(Button button) {
        LocalDate selected = LocalDate.parse((String) button.getTag());
        dateDialog = new DatePickerDialog(this, (picker, year, month, day) ->
                setDate(button, LocalDate.of(year, month + 1, day).toString()),
                selected.getYear(), selected.getMonthValue() - 1, selected.getDayOfMonth());
        dateDialog.show();
    }

    private void setDate(Button button, String isoDate) {
        button.setTag(isoDate);
        button.setText(LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    private void setBusy(boolean value) {
        busy = value;
        add.setEnabled(!value);
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (formDialog != null && formDialog.isShowing() && !saving) {
            state.putBoolean("form_open", true);
            state.putLong("editing_id", editingId);
            state.putString("draft_name",
                    ((EditText) form.findViewById(R.id.semester_input_name)).getText().toString());
            state.putString("draft_start", (String) form.findViewById(R.id.semester_input_start).getTag());
            state.putString("draft_end", (String) form.findViewById(R.id.semester_input_end).getTag());
        }

        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        if (dateDialog != null)
            dateDialog.dismiss();
        if (formDialog != null)
            formDialog.dismiss();
        super.onDestroy();
    }
}
