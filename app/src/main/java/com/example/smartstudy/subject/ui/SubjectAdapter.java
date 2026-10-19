package com.example.smartstudy.subject.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.example.smartstudy.R;
import com.example.smartstudy.subject.data.Subject;
import java.util.ArrayList;
import java.util.List;

/** BaseAdapter và layout từng dòng theo bài ListView chương 3. */
public class SubjectAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<Subject> subjects = new ArrayList<>();

    public SubjectAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setSubjects(List<Subject> values) {
        subjects.clear();
        subjects.addAll(values);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return subjects.size();
    }

    @Override
    public Subject getItem(int position) {
        return subjects.get(position);
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
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null)
            convertView = inflater.inflate(R.layout.item_subject, parent, false);
        Subject subject = getItem(position);
        TextView name = convertView.findViewById(R.id.subject_name);
        TextView teacher = convertView.findViewById(R.id.subject_teacher);
        View color = convertView.findViewById(R.id.subject_color);
        name.setText(subject.getName());
        teacher.setText(subject.getTeacherName() == null
                ? inflater.getContext().getString(R.string.subject_no_teacher) : subject.getTeacherName());
        ((TextView) convertView.findViewById(R.id.subject_semester)).setText(subject.getSemesterName() == null
                ? inflater.getContext().getString(R.string.semester_unassigned) : subject.getSemesterName());
        color.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(subject.getColorHex())));
        return convertView;
    }
}
