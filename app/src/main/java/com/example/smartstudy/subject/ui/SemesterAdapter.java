package com.example.smartstudy.subject.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.example.smartstudy.R;
import com.example.smartstudy.subject.data.Semester;
import java.util.ArrayList;
import java.util.List;

public class SemesterAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<Semester> items = new ArrayList<>();
    public SemesterAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setItems(List<Semester> values) {
        items.clear();
        items.addAll(values);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Semester getItem(int position) {
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
        if (row == null)
            row = inflater.inflate(R.layout.item_semester, parent, false);
        Semester semester = getItem(position);
        ((TextView) row.findViewById(R.id.semester_name)).setText(semester.getName());
        ((TextView) row.findViewById(R.id.semester_dates)).setText(semester.getDateRange());
        row.findViewById(R.id.semester_archived).setVisibility(semester.isArchived() ? View.VISIBLE : View.GONE);
        return row;
    }
}
