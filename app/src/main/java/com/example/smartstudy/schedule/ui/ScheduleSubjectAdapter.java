package com.example.smartstudy.schedule.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.smartstudy.R;
import com.example.smartstudy.subject.data.Subject;

import java.util.List;

/** Hai dòng giúp tên môn và học kỳ không chen chung một hàng trong Spinner. */
final class ScheduleSubjectAdapter extends ArrayAdapter<Subject> {
    ScheduleSubjectAdapter(Context context, List<Subject> subjects) {
        super(context, R.layout.item_schedule_subject_choice, subjects);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        return bind(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
        return bind(position, convertView, parent);
    }

    private View bind(int position, View view, ViewGroup parent) {
        if (view == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.item_schedule_subject_choice, parent, false);
        }
        Subject subject = getItem(position);
        ((TextView) view.findViewById(R.id.schedule_choice_name)).setText(subject.getName());
        ((TextView) view.findViewById(R.id.schedule_choice_semester)).setText(
                subject.getSemesterName() == null ? getContext().getString(R.string.semester_unassigned)
                        : subject.getSemesterName());
        return view;
    }
}
