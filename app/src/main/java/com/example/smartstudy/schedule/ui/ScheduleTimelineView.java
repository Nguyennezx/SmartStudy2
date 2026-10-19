package com.example.smartstudy.schedule.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.example.smartstudy.R;
import com.example.smartstudy.schedule.data.CalendarCardLayout;
import com.example.smartstudy.schedule.data.ScheduleCalendar;
import com.example.smartstudy.schedule.data.ScheduleOccurrence;
import com.example.smartstudy.schedule.data.ScheduleRules;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** FrameLayout đặt thẻ XML theo trục giờ, dùng các View đã học ở chương 3. */
public class ScheduleTimelineView extends FrameLayout {
    public interface OnOccurrenceClickListener {
        void onClick(ScheduleOccurrence occurrence);
    }

    private final List<ScheduleOccurrence> events = new ArrayList<>();
    private LocalDate selectedDate;
    private ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
    private boolean weekMode;
    private boolean dirty = true;
    private int lastWidth;
    private int firstHour = 8;
    private int lastHour = 18;
    private OnOccurrenceClickListener listener;

    public ScheduleTimelineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
    }

    public void setOnOccurrenceClickListener(OnOccurrenceClickListener listener) {
        this.listener = listener;
    }

    public void setData(LocalDate selectedDate, boolean weekMode, ZoneId zone,
                        List<ScheduleOccurrence> occurrences) {
        this.selectedDate = selectedDate;
        this.weekMode = weekMode;
        this.zone = zone;
        events.clear();
        events.addAll(occurrences);
        firstHour = 8;
        lastHour = 18;
        for (ScheduleOccurrence event : events) {
            if (weekMode || event.getDate().equals(selectedDate)) {
                firstHour = Math.min(firstHour, event.getStartMinute() / 60);
                lastHour = Math.max(lastHour, (event.getEndMinute() + 59) / 60);
            }
        }
        lastHour = Math.min(24, lastHour);
        dirty = true;
        requestLayout();
    }

    public int getSelectedOffset() {
        return selectedDate == null || !weekMode ? 0
                : (selectedDate.getDayOfWeek().getValue() - 1) * weekColumnWidth();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int viewport = getParent() instanceof View ? ((View) getParent()).getWidth() : 0;
        if (viewport <= 0) {
            viewport = getResources().getDisplayMetrics().widthPixels - dp(32);
        }
        int width = weekMode ? Math.max(viewport, railWidth() + 7 * weekColumnWidth()) : viewport;
        int height = topOffset() + (lastHour - firstHour) * hourHeight() + Math.round(dp(76) * Math.max(1f, getResources().getConfiguration().fontScale));
        if (dirty || lastWidth != width) {
            rebuild(width);
            dirty = false;
            lastWidth = width;
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    private void rebuild(int width) {
        removeAllViews();
        if (selectedDate == null) {
            return;
        }
        int rail = railWidth();
        int columns = weekMode ? 7 : 1;
        int columnWidth = (width - rail) / columns;
        int top = topOffset();
        int gridHeight = (lastHour - firstHour) * hourHeight();
        int ruleColor = color(R.color.header_avatar_background);
        LocalDate monday = ScheduleCalendar.weekStart(selectedDate);
        for (int hour = firstHour; hour <= lastHour; hour++) {
            int y = top + (hour - firstHour) * hourHeight();
            TextView label = label(ScheduleRules.formatMinute(hour * 60), 11);
            place(label, 0, Math.max(0, y - dp(10)), rail - dp(6),
                    Math.round(dp(24) * getResources().getConfiguration().fontScale));
            View line = new View(getContext());
            line.setBackgroundColor(ruleColor);
            place(line, rail, y, width - rail, dp(1));
        }
        String[] days = getResources().getStringArray(R.array.calendar_short_days);
        for (int column = 0; column < columns; column++) {
            LocalDate day = weekMode ? monday.plusDays(column) : selectedDate;
            int x = rail + column * columnWidth;
            if (weekMode) {
                TextView title = label(days[column] + " · " + day.getDayOfMonth(), 14);
                title.setGravity(android.view.Gravity.CENTER);
                title.setTypeface(null, Typeface.BOLD);
                if (day.equals(selectedDate)) {
                    title.setTextColor(color(R.color.PeriwinkleBlue));
                }
                place(title, x, 0, columnWidth, top - dp(8));
            }
            View vertical = new View(getContext());
            vertical.setBackgroundColor(ruleColor);
            place(vertical, x, top, dp(1), gridHeight);
            List<ScheduleOccurrence> items = ScheduleCalendar.onDate(events, day);
            // 52dp tối thiểu + 4dp khoảng cách, quy đổi sang phút trên trục 64dp/giờ.
            int minimumHeight = Math.round(dp(52) * Math.max(1f,
                    getResources().getConfiguration().fontScale));
            int minimumMinutes = (int) Math.ceil((minimumHeight + dp(4)) * 60f / hourHeight());
            List<CalendarCardLayout.Placement> positions = CalendarCardLayout.place(items, minimumMinutes);
            for (CalendarCardLayout.Placement position : positions) {
                ScheduleOccurrence event = position.occurrence;
                int laneWidth = columnWidth / position.laneCount;
                int height = Math.max(minimumHeight, Math.round(
                        (event.getEndMinute() - event.getStartMinute()) * hourHeight() / 60f) - dp(4));
                int y = top + Math.round((event.getStartMinute() - firstHour * 60) * hourHeight() / 60f);
                View card = createCard(event, height);
                place(card, x + position.lane * laneWidth + dp(5), y,
                        Math.max(dp(12), laneWidth - dp(10)), height);
            }
        }
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        int minute = now.getHour() * 60 + now.getMinute();
        boolean visibleDate = weekMode ? !today.isBefore(monday) && today.isBefore(monday.plusWeeks(1))
                : today.equals(selectedDate);
        if (visibleDate && minute >= firstHour * 60 && minute <= lastHour * 60) {
            int column = weekMode ? today.getDayOfWeek().getValue() - 1 : 0;
            int x = rail + column * columnWidth;
            int y = top + Math.round((minute - firstHour * 60) * hourHeight() / 60f);
            View nowLine = new View(getContext());
            nowLine.setBackgroundColor(color(R.color.header_notification_dot));
            nowLine.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            place(nowLine, x, y, columnWidth, dp(2));
        }
    }

    private View createCard(ScheduleOccurrence event, int height) {
        View card = LayoutInflater.from(getContext()).inflate(R.layout.item_calendar_event, this, false);
        int subjectColor = Color.parseColor(event.getSchedule().getColorHex());
        int white = color(R.color.white);
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ColorUtils.blendARGB(subjectColor, white, 0.72f),
                        ColorUtils.blendARGB(subjectColor, white, 0.90f)});
        background.setCornerRadius(dp(14));
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(subjectColor, 45)), background, null));
        card.findViewById(R.id.calendar_event_stripe).setBackgroundTintList(
                ColorStateList.valueOf(subjectColor));
        TextView title = card.findViewById(R.id.calendar_event_title);
        title.setText(event.getSchedule().getSubjectName());
        title.setTextSize(weekMode ? 14 : 16);
        String time = ScheduleRules.formatMinute(event.getStartMinute()) + "–"
                + ScheduleRules.formatMinute(event.getEndMinute());
        ((TextView) card.findViewById(R.id.calendar_event_time)).setText(time);
        TextView room = card.findViewById(R.id.calendar_event_room);
        String location = event.getRoom();
        if (location == null || location.trim().isEmpty()) {
            location = getContext().getString(R.string.schedule_no_room);
        }
        if (event.isMoved()) {
            location = getContext().getString(R.string.calendar_moved) + " · " + location;
        }
        room.setText(location);
        room.setVisibility(height >= dp(84) * getResources().getConfiguration().fontScale ? VISIBLE : GONE);
        String date = event.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        card.setContentDescription(event.getSchedule().getSubjectName() + ", " + date
                + ", " + time + ", " + location);
        card.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(event);
            }
        });
        return card;
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color(R.color.header_icon));
        view.setGravity(android.view.Gravity.CENTER_VERTICAL);
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    private void place(View view, int x, int y, int width, int height) {
        LayoutParams params = new LayoutParams(width, height);
        params.leftMargin = x;
        params.topMargin = y;
        addView(view, params);
    }

    private int topOffset() {
        return dp(weekMode ? 48 * Math.max(1f, getResources().getConfiguration().fontScale) : 12);
    }

    private int railWidth() {
        return Math.round(dp(52) * Math.max(1f, getResources().getConfiguration().fontScale));
    }

    private int weekColumnWidth() {
        return Math.round(dp(168) * Math.max(1f, getResources().getConfiguration().fontScale));
    }

    private int hourHeight() {
        return Math.round(dp(64) * Math.max(1f, getResources().getConfiguration().fontScale));
    }

    private int color(int id) {
        return ContextCompat.getColor(getContext(), id);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
