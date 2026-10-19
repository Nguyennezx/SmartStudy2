package com.example.smartstudy.schedule;

import android.app.DatePickerDialog;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.smartstudy.R;
import com.example.smartstudy.schedule.data.Schedule;
import com.example.smartstudy.schedule.data.ScheduleCalendar;
import com.example.smartstudy.schedule.data.ScheduleDAO;
import com.example.smartstudy.schedule.data.ScheduleOccurrence;
import com.example.smartstudy.schedule.data.ScheduleRules;
import com.example.smartstudy.schedule.ui.ScheduleAdapter;
import com.example.smartstudy.schedule.ui.ScheduleEditActivity;
import com.example.smartstudy.schedule.ui.ScheduleTimelineView;
import com.example.smartstudy.schedule.reminder.ReminderCoordinator;
import com.example.smartstudy.schedule.reminder.ReminderNotifications;
import com.example.smartstudy.subject.ui.SubjectActivity;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScheduleFragment extends Fragment {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private View screen;
    private ScheduleDAO dao;
    private TextView status;
    private ScheduleTimelineView timeline;
    private LocalDate selectedDate;
    private ZoneId displayZone = ZoneId.of("Asia/Ho_Chi_Minh");
    private boolean weekMode;
    private boolean busy;
    private boolean dataReady;
    private int loadVersion;
    private List<Schedule> allSchedules = new ArrayList<>();
    private List<ScheduleOccurrence> occurrences = new ArrayList<>();
    private DatePickerDialog dateDialog;
    private AlertDialog activeDialog;
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> refreshReminders());
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final Runnable minuteRefresh = new Runnable() {
        @Override
        public void run() {
            if (screen != null) {
                if (!busy) {
                    renderCalendar(false);
                }
                screen.postDelayed(this, 60000);
            }
        }
    };

    public static ScheduleFragment newInstance() {
        return new ScheduleFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        screen = inflater.inflate(R.layout.fragment_schedule, container, false);
        if (savedInstanceState != null) {
            String date = savedInstanceState.getString("calendar_selected_date");
            selectedDate = date == null ? null : LocalDate.parse(date);
            weekMode = savedInstanceState.getBoolean("calendar_week_mode");
        }
        dao = new ScheduleDAO(requireContext());
        status = screen.findViewById(R.id.schedule_status);
        timeline = screen.findViewById(R.id.schedule_timeline);
        timeline.setOnOccurrenceClickListener(event -> {
            if (!busy) {
                showActions(event.getSchedule(), event);
            }
        });
        screen.findViewById(R.id.schedule_add).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ScheduleEditActivity.class);
            if (selectedDate != null) {
                intent.putExtra(ScheduleEditActivity.EXTRA_DATE, selectedDate.toString());
            }
            startActivity(intent);
        });
        screen.findViewById(R.id.calendar_more).setOnClickListener(v -> showMore());
        screen.findViewById(R.id.calendar_previous).setOnClickListener(v -> changeWeek(-1));
        screen.findViewById(R.id.calendar_next).setOnClickListener(v -> changeWeek(1));
        screen.findViewById(R.id.calendar_today).setOnClickListener(v -> {
            selectedDate = LocalDate.now(displayZone);
            loadSchedules();
        });
        screen.findViewById(R.id.calendar_week_range).setOnClickListener(v -> pickDate());
        screen.findViewById(R.id.calendar_mode_day).setOnClickListener(v -> setWeekMode(false));
        screen.findViewById(R.id.calendar_mode_week).setOnClickListener(v -> setWeekMode(true));
        setBusy(true);
        return screen;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSchedules();
        refreshReminders();
        screen.removeCallbacks(minuteRefresh);
        screen.postDelayed(minuteRefresh, 60000);
    }

    @Override
    public void onPause() {
        if (screen != null) {
            screen.removeCallbacks(minuteRefresh);
        }
        super.onPause();
    }

    private void changeWeek(int amount) {
        if (selectedDate != null) {
            selectedDate = selectedDate.plusWeeks(amount);
            loadSchedules();
        }
    }

    private void refreshReminders() {
        if (screen == null || !isAdded()) {
            return;
        }
        View target = screen;
        ReminderCoordinator.refresh(requireContext(), state -> {
            if (!isCurrent(target)) {
                return;
            }
            View banner = target.findViewById(R.id.reminder_banner);
            TextView message = target.findViewById(R.id.reminder_status);
            TextView action = target.findViewById(R.id.reminder_action);
            banner.setVisibility(View.VISIBLE);
            action.setVisibility(View.VISIBLE);
            if (state.failed) {
                message.setText(R.string.reminder_sync_failed);
                action.setText(R.string.reminder_retry);
                action.setOnClickListener(v -> refreshReminders());
            } else if (state.upcoming == 0) {
                banner.setVisibility(View.GONE);
            } else if (!state.globallyEnabled) {
                message.setText(R.string.reminder_globally_disabled);
                action.setVisibility(View.GONE);
            } else if (!state.canPost) {
                message.setText(R.string.reminder_requires_notification);
                action.setText(R.string.reminder_enable_notifications);
                action.setOnClickListener(v -> enableNotifications());
            } else if (!state.exact) {
                message.setText(R.string.reminder_requires_exact);
                action.setText(R.string.reminder_enable_exact);
                action.setOnClickListener(v -> openExactAlarmSettings());
            } else {
                banner.setVisibility(View.GONE);
            }
        });
    }

    private void enableNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            boolean asked = ReminderCoordinator.hasAskedNotificationPermission(requireContext());
            if (!asked || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                ReminderCoordinator.markAskedNotificationPermission(requireContext());
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        Intent intent = new Intent(NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
                ? Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS : Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, ReminderNotifications.CHANNEL);
        openSettings(intent);
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            openSettings(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + requireContext().getPackageName())));
        }
    }

    private void openSettings(Intent intent) {
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException unavailable) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + requireContext().getPackageName())));
            } catch (android.content.ActivityNotFoundException missing) {
                Toast.makeText(requireContext(), R.string.reminder_settings_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setWeekMode(boolean value) {
        weekMode = value;
        renderCalendar(true);
    }

    private void pickDate() {
        if (selectedDate == null) {
            return;
        }
        dateDialog = new DatePickerDialog(dialogContext(), (picker, year, month, day) -> {
            selectedDate = LocalDate.of(year, month + 1, day);
            loadSchedules();
        }, selectedDate.getYear(), selectedDate.getMonthValue() - 1, selectedDate.getDayOfMonth());
        dateDialog.show();
    }

    private void loadSchedules() {
        if (screen == null) {
            return;
        }
        View target = screen;
        LocalDate requestedDate = selectedDate;
        int version = ++loadVersion;
        dataReady = false;
        setBusy(true);
        if (selectedDate != null) {
            renderHeader();
        }
        timeline.setVisibility(View.GONE);
        ((TextView) screen.findViewById(R.id.calendar_summary)).setText("");
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.schedule_loading);
        status.setOnClickListener(null);
        executor.execute(() -> {
            try {
                ScheduleDAO.CalendarData data = dao.getCalendarData();
                ZoneId zone = ZoneId.of(data.timeZoneId);
                LocalDate day = requestedDate == null ? LocalDate.now(zone) : requestedDate;
                List<ScheduleOccurrence> week = ScheduleCalendar.buildWeek(
                        day, zone, data.schedules, data.exceptions);
                target.post(() -> {
                    if (!isCurrent(target) || version != loadVersion) {
                        return;
                    }
                    selectedDate = day;
                    displayZone = zone;
                    allSchedules = data.schedules;
                    occurrences = week;
                    dataReady = true;
                    setBusy(false);
                    renderCalendar(true);
                });
            } catch (Exception error) {
                android.util.Log.e("ScheduleFragment", "Không tải được lịch ngày/tuần", error);
                target.post(() -> {
                    if (!isCurrent(target) || version != loadVersion) {
                        return;
                    }
                    setBusy(false);
                    timeline.setVisibility(View.GONE);
                    status.setText(R.string.schedule_load_error);
                    status.setVisibility(View.VISIBLE);
                    status.setOnClickListener(v -> loadSchedules());
                });
            }
        });
    }

    private void renderCalendar(boolean revealSelectedDay) {
        if (screen == null || selectedDate == null) {
            return;
        }
        renderHeader();
        if (busy || !dataReady) {
            return;
        }
        List<ScheduleOccurrence> visible = weekMode ? occurrences
                : ScheduleCalendar.onDate(occurrences, selectedDate);
        java.util.Set<String> unique = new java.util.HashSet<>();
        for (ScheduleOccurrence event : visible) {
            unique.add(event.getOccurrenceKey());
        }
        ((TextView) screen.findViewById(R.id.calendar_summary)).setText(getString(
                weekMode ? R.string.calendar_week_count : R.string.calendar_day_count, unique.size()));
        ((TextView) screen.findViewById(R.id.calendar_zone)).setText(
                getString(R.string.schedule_time_zone, displayZone.getId()));
        status.setOnClickListener(null);
        status.setText(weekMode ? R.string.calendar_empty_week : R.string.calendar_empty_day);
        status.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        timeline.setVisibility(visible.isEmpty() ? View.GONE : View.VISIBLE);
        timeline.setData(selectedDate, weekMode, displayZone, occurrences);
        if (revealSelectedDay) {
            HorizontalScrollView scroll = screen.findViewById(R.id.calendar_horizontal);
            View target = screen;
            scroll.post(() -> {
                if (isCurrent(target) && timeline != null) {
                    scroll.smoothScrollTo(timeline.getSelectedOffset(), 0);
                }
            });
        }
    }

    private void renderHeader() {
        LocalDate monday = ScheduleCalendar.weekStart(selectedDate);
        LocalDate sunday = monday.plusDays(6);
        String range = monday.getMonth() == sunday.getMonth() && monday.getYear() == sunday.getYear()
                ? getString(R.string.calendar_compact_week, monday.getDayOfMonth(), sunday.getDayOfMonth(),
                        monday.getMonthValue(), monday.getYear())
                : monday.format(dateFormat) + " – " + sunday.format(dateFormat);
        ((TextView) screen.findViewById(R.id.calendar_week_range)).setText(range);
        screen.findViewById(R.id.calendar_week_range).setContentDescription(
                getString(R.string.calendar_choose_date) + ", " + selectedDate.format(dateFormat));
        screen.findViewById(R.id.calendar_mode_day).setSelected(!weekMode);
        screen.findViewById(R.id.calendar_mode_week).setSelected(weekMode);
        ((TextView) screen.findViewById(R.id.calendar_mode_day)).setTextColor(ContextCompat.getColor(
                requireContext(), !weekMode ? R.color.white : R.color.header_icon));
        ((TextView) screen.findViewById(R.id.calendar_mode_week)).setTextColor(ContextCompat.getColor(
                requireContext(), weekMode ? R.color.white : R.color.header_icon));
        LinearLayout days = screen.findViewById(R.id.calendar_days);
        days.removeAllViews();
        String[] labels = getResources().getStringArray(R.array.calendar_short_days);
        LocalDate today = LocalDate.now(displayZone);
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            View chip = getLayoutInflater().inflate(R.layout.view_calendar_day, days, false);
            boolean selected = day.equals(selectedDate);
            chip.setSelected(selected);
            ((TextView) chip.findViewById(R.id.calendar_chip_weekday)).setText(labels[i]);
            ((TextView) chip.findViewById(R.id.calendar_chip_date)).setText(String.valueOf(day.getDayOfMonth()));
            int textColor = ContextCompat.getColor(requireContext(),
                    selected ? R.color.white : R.color.header_title);
            ((TextView) chip.findViewById(R.id.calendar_chip_weekday)).setTextColor(textColor);
            ((TextView) chip.findViewById(R.id.calendar_chip_date)).setTextColor(textColor);
            View dot = chip.findViewById(R.id.calendar_chip_today);
            dot.setVisibility(day.equals(today) ? View.VISIBLE : View.INVISIBLE);
            dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(
                    requireContext(), selected ? R.color.white : R.color.PeriwinkleBlue)));
            chip.setContentDescription(day.format(dateFormat)
                    + (day.equals(today) ? ", " + getString(R.string.calendar_today) : ""));
            chip.setOnClickListener(v -> {
                selectedDate = day;
                loadSchedules();
            });
            days.addView(chip);
        }
    }

    private ContextThemeWrapper dialogContext() {
        return new ContextThemeWrapper(requireContext(), R.style.Theme_SmartStudy_Subject);
    }

    private void showMore() {
        activeDialog = new AlertDialog.Builder(dialogContext())
                .setItems(R.array.calendar_more_actions, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(requireContext(), SubjectActivity.class));
                    } else {
                        showAllSchedules();
                    }
                })
                .show();
    }

    private void showAllSchedules() {
        if (!dataReady) {
            Toast.makeText(requireContext(), R.string.schedule_load_error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (allSchedules.isEmpty()) {
            Toast.makeText(requireContext(), R.string.schedule_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        ScheduleAdapter adapter = new ScheduleAdapter(dialogContext());
        adapter.setItems(allSchedules);
        activeDialog = new AlertDialog.Builder(dialogContext())
                .setTitle(R.string.calendar_all_schedules)
                .setAdapter(adapter, (dialog, position) -> showActions(adapter.getItem(position), null))
                .setNegativeButton(R.string.subject_cancel, null)
                .show();
    }

    private void showActions(Schedule schedule, ScheduleOccurrence occurrence) {
        String details;
        if (occurrence != null) {
            details = occurrence.getDate().format(dateFormat) + " · "
                    + ScheduleRules.formatMinute(occurrence.getStartMinute()) + "–"
                    + ScheduleRules.formatMinute(occurrence.getEndMinute()) + "\n"
                    + getString(R.string.schedule_time_zone, displayZone.getId());
        } else {
            details = LocalDate.parse(schedule.getValidFrom()).format(dateFormat) + " – "
                    + LocalDate.parse(schedule.getValidTo()).format(dateFormat);
        }
        String room = occurrence == null ? schedule.getRoom() : occurrence.getRoom();
        details += "\n" + (room == null || room.trim().isEmpty()
                ? getString(R.string.schedule_no_room) : room);
        if (schedule.getNote() != null && !schedule.getNote().trim().isEmpty()) {
            details += "\n" + schedule.getNote();
        }
        if (occurrence != null && occurrence.isMoved()) {
            details += "\n" + getString(R.string.calendar_moved);
        }
        details += "\n\n" + getString(R.string.calendar_series_hint);
        activeDialog = new AlertDialog.Builder(dialogContext())
                .setTitle(schedule.getSubjectName())
                .setMessage(details)
                .setPositiveButton(R.string.calendar_edit_series, (dialog, which) -> {
                    Intent intent = new Intent(requireContext(), ScheduleEditActivity.class);
                    intent.putExtra(ScheduleEditActivity.EXTRA_ID, schedule.getId());
                    startActivity(intent);
                })
                .setNeutralButton(R.string.calendar_delete_series, (dialog, which) -> confirmDelete(schedule))
                .setNegativeButton(R.string.subject_cancel, null)
                .show();
    }

    private void confirmDelete(Schedule schedule) {
        activeDialog = new AlertDialog.Builder(dialogContext())
                .setTitle(R.string.calendar_delete_series)
                .setMessage(R.string.schedule_delete_message)
                .setNegativeButton(R.string.subject_cancel, null)
                .setPositiveButton(R.string.schedule_delete, (dialog, which) -> delete(schedule))
                .show();
    }

    private void delete(Schedule schedule) {
        if (screen == null || busy) {
            return;
        }
        View target = screen;
        setBusy(true);
        executor.execute(() -> {
            try {
                if (!dao.delete(schedule.getId())) {
                    throw new IllegalStateException("Lịch không còn tồn tại.");
                }
                target.post(() -> {
                    if (isCurrent(target)) {
                        Toast.makeText(requireContext(), R.string.schedule_deleted, Toast.LENGTH_SHORT).show();
                        loadSchedules();
                        refreshReminders();
                    }
                });
            } catch (Exception error) {
                android.util.Log.e("ScheduleFragment", "Không xóa được lịch", error);
                target.post(() -> {
                    if (isCurrent(target)) {
                        setBusy(false);
                        Toast.makeText(requireContext(), R.string.subject_write_error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private boolean isCurrent(View target) {
        return target != null && isAdded() && screen == target;
    }

    private void setBusy(boolean value) {
        busy = value;
        screen.findViewById(R.id.schedule_add).setEnabled(!value);
        screen.findViewById(R.id.calendar_more).setEnabled(!value);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        if (selectedDate != null) {
            state.putString("calendar_selected_date", selectedDate.toString());
        }
        state.putBoolean("calendar_week_mode", weekMode);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onDestroyView() {
        screen.removeCallbacks(minuteRefresh);
        if (dateDialog != null) {
            dateDialog.dismiss();
        }
        if (activeDialog != null) {
            activeDialog.dismiss();
        }
        screen = null;
        timeline = null;
        status = null;
        super.onDestroyView();
    }
}
