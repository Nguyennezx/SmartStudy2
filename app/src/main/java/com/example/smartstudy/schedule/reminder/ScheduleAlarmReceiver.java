package com.example.smartstudy.schedule.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScheduleAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        ReminderCoordinator.onAlarm(context, intent.getLongExtra(ReminderCoordinator.EXTRA_ID, -1),
                intent.getStringExtra(ReminderCoordinator.EXTRA_KEY), pending::finish);
    }
}
