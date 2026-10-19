package com.example.smartstudy.schedule.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.smartstudy.R;
import com.example.smartstudy.schedule.data.Schedule;
import com.example.smartstudy.schedule.data.ScheduleRules;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** ListView + BaseAdapter + XML từng dòng theo chương 3. */
public class ScheduleAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<Schedule> items = new ArrayList<>();

    public ScheduleAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setItems(List<Schedule> schedules) {
        items.clear();
        items.addAll(schedules);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Schedule getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int position, View row, ViewGroup parent) {
        if (row == null) {
            row = inflater.inflate(R.layout.item_schedule, parent, false);
        }
        Schedule schedule = getItem(position);
        Context context = inflater.getContext();
        String day = context.getResources().getStringArray(R.array.schedule_days)[schedule.getDayOfWeek() - 1];
        String hours = day + " · " + ScheduleRules.formatMinute(schedule.getStartMinute())
                + "–" + ScheduleRules.formatMinute(schedule.getEndMinute());
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String dates = LocalDate.parse(schedule.getValidFrom()).format(format)
                + " – " + LocalDate.parse(schedule.getValidTo()).format(format);
        ((TextView) row.findViewById(R.id.schedule_item_subject)).setText(schedule.getSubjectName());
        ((TextView) row.findViewById(R.id.schedule_item_time)).setText(hours);
        ((TextView) row.findViewById(R.id.schedule_item_dates)).setText(dates);
        String room = schedule.getRoom();
        ((TextView) row.findViewById(R.id.schedule_item_room)).setText(
                room == null || room.trim().isEmpty() ? context.getString(R.string.schedule_no_room) : room);
        ((TextView) row.findViewById(R.id.schedule_item_zone)).setText(schedule.getTimeZoneId());
        row.findViewById(R.id.schedule_item_color).setBackgroundTintList(
                ColorStateList.valueOf(Color.parseColor(schedule.getColorHex())));
        return row;
    }
}
