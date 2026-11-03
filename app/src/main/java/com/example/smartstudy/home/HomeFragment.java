package com.example.smartstudy.home;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.smartstudy.R;
import com.example.smartstudy.home.data.HomeSnapshot;
import com.example.smartstudy.home.data.TodayAgenda;
import com.example.smartstudy.schedule.ScheduleFragment;
import com.example.smartstudy.schedule.data.ScheduleOccurrence;
import com.example.smartstudy.schedule.data.ScheduleRules;
import com.example.smartstudy.schedule.reminder.ReminderCoordinator;
import com.example.smartstudy.schedule.ui.ScheduleEditActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** C3/C4: Fragment, XML, inflate và listener. SQL ở HomeDAO (C6). */
public class HomeFragment extends Fragment {
    private View screen;
    private HomeViewModel model;
    private HomeSnapshot snapshot;
    private AlertDialog activeDialog;
    private final Runnable minuteRefresh = new Runnable() {
        @Override public void run() {
            if (screen != null && isResumed()) {
                model.reload();
                screen.postDelayed(this, 60000);
            }
        }
    };
    public static HomeFragment newInstance() { return new HomeFragment(); }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle state) {
        screen = inflater.inflate(R.layout.fragment_home, container, false);
        return screen;
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        model = new ViewModelProvider(this).get(HomeViewModel.class);
        view.findViewById(R.id.home_view_schedule).setOnClickListener(v -> openSchedule());
        view.findViewById(R.id.home_start_study).setOnClickListener(v -> navigate(R.id.nav_study));
        view.findViewById(R.id.home_status).setOnClickListener(v -> model.reload());
        model.state().observe(getViewLifecycleOwner(), result -> {
            screen.findViewById(R.id.home_loading).setVisibility(result.loading ? View.VISIBLE : View.GONE);
            TextView status = screen.findViewById(R.id.home_status);
            status.setText(result.error);
            status.setVisibility(result.error.isEmpty() ? View.GONE : View.VISIBLE);
            snapshot = result.data;
            screen.findViewById(R.id.home_content).setVisibility(snapshot == null ? View.GONE : View.VISIBLE);
            if (snapshot != null) render();
        });
    }

    @Override public void onResume() {
        super.onResume();
        ReminderCoordinator.refresh(requireContext());
        model.reload();
        screen.removeCallbacks(minuteRefresh);
        screen.postDelayed(minuteRefresh, 60000);
    }

    @Override public void onPause() {
        if (screen != null) screen.removeCallbacks(minuteRefresh);
        if (activeDialog != null) activeDialog.dismiss();
        super.onPause();
    }

    private void render() {
        Instant now = Instant.now();
        TodayAgenda agenda = TodayAgenda.at(snapshot.lessons, now, snapshot.zone);
        String date = snapshot.date.format(DateTimeFormatter.ofPattern("EEEE, dd 'tháng' MM", new Locale("vi", "VN")));
        setText(screen, R.id.home_date, date.substring(0, 1).toUpperCase(new Locale("vi")) + date.substring(1));
        TextView zone = screen.findViewById(R.id.home_zone);
        zone.setText(getString(R.string.schedule_time_zone, snapshot.zone.getId()));
        zone.setVisibility(snapshot.zone.getId().equals("Asia/Ho_Chi_Minh") ? View.GONE : View.VISIBLE);
        View featured = screen.findViewById(R.id.home_featured);
        featured.setVisibility(agenda.featured == null ? View.GONE : View.VISIBLE);
        TextView empty = screen.findViewById(R.id.home_no_lessons);
        empty.setVisibility(agenda.featured == null ? View.VISIBLE : View.GONE);
        empty.setText(snapshot.lessons.isEmpty() ? R.string.home_no_lessons : R.string.home_finished);
        if (agenda.featured != null) {
            ScheduleOccurrence lesson = agenda.featured;
            boolean ongoing = !TodayAgenda.start(lesson, snapshot.zone).isAfter(now);
            setText(featured, R.id.home_feature_status, getString(ongoing ? R.string.home_ongoing : R.string.home_upcoming));
            long minutes = Math.max(1, (Duration.between(now, TodayAgenda.start(lesson, snapshot.zone)).getSeconds() + 59) / 60);
            setText(featured, R.id.home_feature_badge, ongoing ? getString(R.string.home_now)
                    : minutes >= 60 ? getString(R.string.home_hours_until, minutes / 60, minutes % 60)
                    : getString(R.string.home_minutes_until, minutes));
            setText(featured, R.id.home_feature_title, lesson.getSchedule().getSubjectName());
            setText(featured, R.id.home_feature_time, hours(lesson));
            setText(featured, R.id.home_feature_room, room(lesson));
            featured.setContentDescription(getString(R.string.home_detail_accessibility,
                    lesson.getSchedule().getSubjectName()) + ", " + hours(lesson) + ", " + room(lesson));
            featured.setOnClickListener(v -> lessonDetails(lesson));
        }
        LinearLayout lessons = screen.findViewById(R.id.home_lessons);
        lessons.removeAllViews();
        for (ScheduleOccurrence lesson : agenda.remaining) {
            View row = getLayoutInflater().inflate(R.layout.item_home_lesson, lessons, false);
            setText(row, R.id.home_row_start, ScheduleRules.formatMinute(lesson.getStartMinute()));
            setText(row, R.id.home_row_end, ScheduleRules.formatMinute(lesson.getEndMinute()));
            setText(row, R.id.home_row_title, lesson.getSchedule().getSubjectName());
            setText(row, R.id.home_row_room, room(lesson));
            row.findViewById(R.id.home_row_stripe).setBackgroundColor(Color.parseColor(lesson.getSchedule().getColorHex()));
            row.setOnClickListener(v -> lessonDetails(lesson));
            lessons.addView(row);
        }
        screen.findViewById(R.id.home_lessons_empty).setVisibility(agenda.remaining.isEmpty() ? View.VISIBLE : View.GONE);
        LinearLayout tasks = screen.findViewById(R.id.home_tasks);
        tasks.removeAllViews();
        for (HomeSnapshot.Task task : snapshot.tasks) {
            if (tasks.getChildCount() > 0) {
                View divider = new View(requireContext());
                divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.header_avatar_background));
                tasks.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, Math.round(getResources().getDisplayMetrics().density))));
            }
            View row = getLayoutInflater().inflate(R.layout.item_home_task, tasks, false);
            setText(row, R.id.home_task_title, task.title);
            setText(row, R.id.home_task_subject, task.subject);
            LocalDate dueDate = Instant.ofEpochMilli(task.dueMillis).atZone(snapshot.zone).toLocalDate();
            String deadline = task.dueMillis < now.toEpochMilli() ? getString(R.string.home_overdue)
                    : dueDate.equals(snapshot.date) ? getString(R.string.home_today)
                    : dueDate.equals(snapshot.date.plusDays(1)) ? getString(R.string.home_tomorrow)
                    : dueDate.format(DateTimeFormatter.ofPattern("dd/MM"));
            String time = Instant.ofEpochMilli(task.dueMillis).atZone(snapshot.zone).format(DateTimeFormatter.ofPattern("HH:mm"));
            setText(row, R.id.home_task_due, deadline + "\n" + time);
            ((TextView) row.findViewById(R.id.home_task_due)).setTextColor(ContextCompat.getColor(requireContext(),
                    task.dueMillis < now.toEpochMilli() ? R.color.header_notification_dot : R.color.header_icon));
            row.setOnClickListener(v -> taskDetails(task));
            tasks.addView(row);
        }
        tasks.setVisibility(snapshot.tasks.isEmpty() ? View.GONE : View.VISIBLE);
        screen.findViewById(R.id.home_tasks_empty).setVisibility(snapshot.tasks.isEmpty() ? View.VISIBLE : View.GONE);
        long minutes = snapshot.focusSeconds / 60;
        String focus = minutes < 60 ? getString(R.string.home_duration_minutes, minutes)
                : getString(R.string.home_duration_hours, minutes / 60, minutes % 60);
        setText(screen, R.id.home_focus, getString(R.string.home_focus, focus));
    }

    private void lessonDetails(ScheduleOccurrence lesson) {
        String teacher = snapshot.teachers.get(lesson.getSchedule().getSubjectId());
        String details = lesson.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " · " + hours(lesson)
                + "\n" + getString(R.string.schedule_time_zone, snapshot.zone.getId())
                + "\n" + room(lesson) + "\n" + (teacher == null || teacher.trim().isEmpty()
                ? getString(R.string.home_no_teacher) : getString(R.string.home_teacher, teacher));
        if (lesson.isMoved()) details += "\n" + getString(R.string.calendar_moved);
        String note = lesson.getSchedule().getNote();
        if (note != null && !note.trim().isEmpty()) details += "\n\n" + note;
        details += "\n\n" + getString(R.string.calendar_series_hint);
        activeDialog = new AlertDialog.Builder(new ContextThemeWrapper(requireContext(), R.style.Theme_SmartStudy_Subject))
                .setTitle(lesson.getSchedule().getSubjectName()).setMessage(details)
                .setNegativeButton(R.string.subject_cancel, null)
                .setPositiveButton(R.string.calendar_edit_series, (dialog, which) -> startActivity(
                        new Intent(requireContext(), ScheduleEditActivity.class)
                                .putExtra(ScheduleEditActivity.EXTRA_ID, lesson.getSchedule().getId())))
                .show();
    }

    private void taskDetails(HomeSnapshot.Task task) {
        String due = Instant.ofEpochMilli(task.dueMillis).atZone(snapshot.zone)
                .format(DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy"));
        activeDialog = new AlertDialog.Builder(new ContextThemeWrapper(requireContext(), R.style.Theme_SmartStudy_Subject))
                .setTitle(task.title).setMessage(task.subject + "\n" + getString(R.string.home_due, due)
                        + "\n" + snapshot.zone.getId() + "\n\n" + (task.description == null || task.description.trim().isEmpty()
                        ? getString(R.string.home_no_description) : task.description))
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void openSchedule() {
        if (snapshot == null) return;
        Bundle request = new Bundle();
        request.putString(ScheduleFragment.REQUEST_DATE, LocalDate.now(snapshot.zone).toString());
        getParentFragmentManager().setFragmentResult(ScheduleFragment.REQUEST_OPEN_DAY, request);
        navigate(R.id.nav_schedule);
    }
    private void navigate(int id) {
        BottomNavigationView navigation = requireActivity().findViewById(R.id.bottom_navigation);
        navigation.setSelectedItemId(id);
    }
    private String hours(ScheduleOccurrence lesson) {
        return ScheduleRules.formatMinute(lesson.getStartMinute()) + " – " + ScheduleRules.formatMinute(lesson.getEndMinute());
    }
    private String room(ScheduleOccurrence lesson) {
        return lesson.getRoom() == null || lesson.getRoom().trim().isEmpty()
                ? getString(R.string.schedule_no_room) : lesson.getRoom();
    }
    private void setText(View root, int id, String value) { ((TextView) root.findViewById(id)).setText(value); }
    @Override public void onDestroyView() {
        if (screen != null) screen.removeCallbacks(minuteRefresh);
        if (activeDialog != null) activeDialog.dismiss();
        activeDialog = null;
        snapshot = null;
        screen = null;
        super.onDestroyView();
    }
}
