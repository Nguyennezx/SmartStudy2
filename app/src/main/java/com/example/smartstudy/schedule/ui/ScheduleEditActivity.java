package com.example.smartstudy.schedule.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.smartstudy.R;
import com.example.smartstudy.schedule.data.Schedule;
import com.example.smartstudy.schedule.data.ScheduleDAO;
import com.example.smartstudy.schedule.data.ScheduleRules;
import com.example.smartstudy.subject.data.Subject;
import com.example.smartstudy.subject.data.SubjectDAO;
import com.example.smartstudy.subject.ui.SubjectActivity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScheduleEditActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "schedule_id";
    public static final String EXTRA_DATE = "schedule_selected_date";
    private static final ExecutorService loader = Executors.newSingleThreadExecutor();
    private ScheduleEditViewModel model;
    private Spinner subjectSpinner;
    private Spinner daySpinner;
    private Spinner reminderSpinner;
    private Button startButton;
    private Button endButton;
    private Button fromButton;
    private Button toButton;
    private Button saveButton;
    private EditText roomInput;
    private EditText noteInput;
    private TextView errorText;
    private List<Subject> subjects = new ArrayList<>();
    private final List<Integer> reminders = new ArrayList<>();
    private long editingId;
    private String zone;
    private boolean loaded;
    private boolean loading;
    private Bundle draft;
    private DatePickerDialog dateDialog;
    private TimePickerDialog timeDialog;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_schedule_edit);
        adaptPickerRows();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.schedule_edit_root), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        editingId = getIntent().getLongExtra(EXTRA_ID, 0);
        draft = state;
        subjectSpinner = findViewById(R.id.schedule_input_subject);
        daySpinner = findViewById(R.id.schedule_input_day);
        reminderSpinner = findViewById(R.id.schedule_input_reminder);
        startButton = findViewById(R.id.schedule_input_start);
        endButton = findViewById(R.id.schedule_input_end);
        fromButton = findViewById(R.id.schedule_input_from);
        toButton = findViewById(R.id.schedule_input_to);
        saveButton = findViewById(R.id.schedule_save);
        roomInput = findViewById(R.id.schedule_input_room);
        noteInput = findViewById(R.id.schedule_input_note);
        errorText = findViewById(R.id.schedule_edit_error);
        ((TextView) findViewById(R.id.schedule_edit_title)).setText(
                editingId > 0 ? R.string.schedule_edit : R.string.schedule_add);
        daySpinner.setAdapter(ArrayAdapter.createFromResource(this, R.array.schedule_days,
                android.R.layout.simple_spinner_dropdown_item));
        findViewById(R.id.schedule_edit_back).setOnClickListener(v -> finish());
        findViewById(R.id.schedule_edit_subjects).setOnClickListener(v ->
                startActivity(new Intent(this, SubjectActivity.class)));
        startButton.setOnClickListener(v -> pickTime(startButton, false));
        endButton.setOnClickListener(v -> pickTime(endButton, true));
        fromButton.setOnClickListener(v -> pickDate(fromButton));
        toButton.setOnClickListener(v -> pickDate(toButton));
        saveButton.setOnClickListener(v -> save());
        model = new ViewModelProvider(this).get(ScheduleEditViewModel.class);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (model.isSaving()) {
                    Toast.makeText(ScheduleEditActivity.this,
                            R.string.schedule_saving, Toast.LENGTH_SHORT).show();
                } else {
                    finish();
                }
            }
        });
        model.getResult().observe(this, message -> {
            if (message == null) {
                return;
            }
            if (message.isEmpty()) {
                Toast.makeText(this, R.string.schedule_saved, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                showError(message);
                model.clearError();
                enableForm(loaded);
            }
        });
        enableForm(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isFinishing() && !loading) {
            if (loaded) {
                draft = captureDraft();
            }
            loadForm();
        }
    }

    private void loadForm() {
        loading = true;
        enableForm(false);
        errorText.setOnClickListener(null);
        errorText.setText(R.string.schedule_loading);
        errorText.setVisibility(View.VISIBLE);
        loader.execute(() -> {
            try {
                ScheduleDAO dao = new ScheduleDAO(this);
                List<Subject> available = new SubjectDAO(this).getActiveSubjects();
                Schedule existing = editingId > 0 ? dao.getById(editingId) : null;
                if (editingId > 0 && existing == null) {
                    throw new IllegalArgumentException("Lịch học không còn tồn tại.");
                }
                String timeZone = existing == null ? dao.getDefaultTimeZone() : existing.getTimeZoneId();
                runOnUiThread(() -> {
                    if (!alive()) {
                        return;
                    }
                    loading = false;
                    subjects = available;
                    zone = timeZone;
                    if (subjects.isEmpty()) {
                        loaded = false;
                        showError(getString(R.string.schedule_need_subject));
                        return;
                    }
                    populate(existing);
                    loaded = true;
                    errorText.setVisibility(View.GONE);
                    enableForm(true);
                });
            } catch (Exception error) {
                android.util.Log.e("ScheduleEdit", "Không tải được form lịch", error);
                runOnUiThread(() -> {
                    if (!alive()) {
                        return;
                    }
                    loading = false;
                    loaded = false;
                    showError(error instanceof IllegalArgumentException
                            ? error.getMessage() : getString(R.string.schedule_load_error));
                    errorText.setOnClickListener(v -> loadForm());
                });
            }
        });
    }

    private void populate(Schedule existing) {
        long subjectId = draft != null && draft.containsKey("subject_id")
                ? draft.getLong("subject_id") : existing == null ? subjects.get(0).getId() : existing.getSubjectId();
        int selected = -1;
        for (int i = 0; i < subjects.size(); i++) {
            if (subjects.get(i).getId() == subjectId) {
                selected = i;
            }
        }
        // Spinner có thể tự chọn dòng đầu khi setSelection(-1). Dùng dòng nhắc ID 0
        // để môn vừa lưu trữ không bị âm thầm thay bằng một môn khác.
        if (selected < 0) {
            subjects = new ArrayList<>(subjects);
            subjects.add(0, new Subject(0, getString(R.string.schedule_select_subject),
                    null, subjects.get(0).getColorHex(), false));
            selected = 0;
        }
        subjectSpinner.setAdapter(new ScheduleSubjectAdapter(this, subjects));
        subjectSpinner.setSelection(selected);
        daySpinner.setSelection(draft != null && draft.containsKey("day")
                ? draft.getInt("day") - 1 : existing == null
                ? defaultDate().getDayOfWeek().getValue() - 1 : existing.getDayOfWeek() - 1);
        setTime(startButton, draft != null && draft.containsKey("start")
                ? draft.getInt("start") : existing == null ? 510 : existing.getStartMinute());
        setTime(endButton, draft != null && draft.containsKey("end")
                ? draft.getInt("end") : existing == null ? 630 : existing.getEndMinute());
        setDate(fromButton, draft != null && draft.containsKey("from")
                ? draft.getString("from") : existing == null ? defaultDate().toString() : existing.getValidFrom());
        setDate(toButton, draft != null && draft.containsKey("to")
                ? draft.getString("to") : existing == null
                ? defaultDate().plusMonths(4).toString() : existing.getValidTo());
        roomInput.setText(draft != null && draft.containsKey("room")
                ? draft.getString("room", "") : existing == null ? "" : existing.getRoom());
        noteInput.setText(draft != null && draft.containsKey("note")
                ? draft.getString("note", "") : existing == null ? "" : existing.getNote());
        Integer reminder = existing == null ? null : existing.getRemindBeforeMinutes();
        if (draft != null && draft.containsKey("reminder")) {
            int value = draft.getInt("reminder");
            reminder = value < 0 ? null : value;
        }
        fillReminders(reminder);
        TextView zoneText = findViewById(R.id.schedule_zone);
        zoneText.setText("Asia/Ho_Chi_Minh".equals(zone)
                ? getString(R.string.schedule_zone_vietnam) : getString(R.string.schedule_time_zone, zone));
        draft = null;
    }

    private void adaptPickerRows() {
        android.content.res.Configuration config = getResources().getConfiguration();
        if (config.screenWidthDp / config.fontScale >= 340) {
            return;
        }
        for (int id : new int[]{R.id.schedule_time_pair, R.id.schedule_date_pair}) {
            LinearLayout row = findViewById(id);
            row.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < row.getChildCount(); i++) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) row.getChildAt(i).getLayoutParams();
                params.width = LinearLayout.LayoutParams.MATCH_PARENT;
                params.weight = 0;
                params.setMarginStart(0);
                row.getChildAt(i).setLayoutParams(params);
            }
        }
    }

    private void fillReminders(Integer selected) {
        reminders.clear();
        reminders.add(null);
        for (int value : new int[]{0, 5, 10, 15, 30, 60}) {
            reminders.add(value);
        }
        if (selected != null && !reminders.contains(selected)) {
            reminders.add(selected);
        }
        List<String> labels = new ArrayList<>();
        for (Integer value : reminders) {
            labels.add(value == null ? getString(R.string.schedule_reminder_off)
                    : value == 0 ? getString(R.string.schedule_reminder_at_start)
                    : getString(R.string.schedule_reminder_minutes, value));
        }
        reminderSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        reminderSpinner.setSelection(reminders.indexOf(selected));
    }

    private LocalDate defaultDate() {
        String date = getIntent().getStringExtra(EXTRA_DATE);
        return date == null ? LocalDate.now(java.time.ZoneId.of(zone)) : LocalDate.parse(date);
    }

    private void save() {
        if (!loaded || model.isSaving()) {
            return;
        }
        int position = subjectSpinner.getSelectedItemPosition();
        if (position < 0 || position >= subjects.size() || subjects.get(position).getId() <= 0) {
            showError(getString(R.string.schedule_need_subject));
            return;
        }
        Schedule schedule = new Schedule(editingId, subjects.get(position).getId(),
                daySpinner.getSelectedItemPosition() + 1, (Integer) startButton.getTag(),
                (Integer) endButton.getTag(), (String) fromButton.getTag(), (String) toButton.getTag(),
                zone, roomInput.getText().toString(), noteInput.getText().toString(),
                reminders.get(reminderSpinner.getSelectedItemPosition()));
        try {
            ScheduleRules.validate(schedule);
        } catch (IllegalArgumentException error) {
            showError(error.getMessage());
            return;
        }
        errorText.setVisibility(View.GONE);
        model.save(schedule);
        enableForm(false);
    }

    private void pickTime(Button button, boolean end) {
        int minute = (Integer) button.getTag();
        timeDialog = new TimePickerDialog(this, (picker, hour, min) -> {
            int chosen = hour * 60 + min;
            setTime(button, end && chosen == 0 ? 1440 : chosen);
        }, minute / 60 % 24, minute % 60, true);
        timeDialog.show();
    }

    private void pickDate(Button button) {
        LocalDate date = LocalDate.parse((String) button.getTag());
        dateDialog = new DatePickerDialog(this, (picker, year, month, day) ->
                setDate(button, LocalDate.of(year, month + 1, day).toString()),
                date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        dateDialog.show();
    }

    private void setTime(Button button, int minute) {
        button.setTag(minute);
        button.setText(ScheduleRules.formatMinute(minute));
    }

    private void setDate(Button button, String date) {
        button.setTag(date);
        button.setText(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    private void enableForm(boolean enabled) {
        boolean editable = enabled && !model.isSaving();
        int[] controls = {R.id.schedule_input_subject, R.id.schedule_input_day,
                R.id.schedule_input_start, R.id.schedule_input_end, R.id.schedule_input_from,
                R.id.schedule_input_to, R.id.schedule_input_room, R.id.schedule_input_note,
                R.id.schedule_input_reminder, R.id.schedule_save};
        for (int id : controls) {
            findViewById(id).setEnabled(editable);
        }
        findViewById(R.id.schedule_edit_subjects).setEnabled(!model.isSaving());
        findViewById(R.id.schedule_edit_back).setEnabled(!model.isSaving());
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
        ((android.widget.ScrollView) findViewById(R.id.schedule_edit_scroll)).smoothScrollTo(0, 0);
    }

    private Bundle captureDraft() {
        Bundle result = new Bundle();
        int position = subjectSpinner.getSelectedItemPosition();
        result.putLong("subject_id", position < 0 ? -1 : subjects.get(position).getId());
        result.putInt("day", daySpinner.getSelectedItemPosition() + 1);
        result.putInt("start", (Integer) startButton.getTag());
        result.putInt("end", (Integer) endButton.getTag());
        result.putString("from", (String) fromButton.getTag());
        result.putString("to", (String) toButton.getTag());
        result.putString("room", roomInput.getText().toString());
        result.putString("note", noteInput.getText().toString());
        Integer reminder = reminders.get(reminderSpinner.getSelectedItemPosition());
        result.putInt("reminder", reminder == null ? -1 : reminder);
        return result;
    }

    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (loaded) {
            state.putAll(captureDraft());
        } else if (draft != null) {
            state.putAll(draft);
        }
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        if (dateDialog != null) {
            dateDialog.dismiss();
        }
        if (timeDialog != null) {
            timeDialog.dismiss();
        }
        super.onDestroy();
    }
}
