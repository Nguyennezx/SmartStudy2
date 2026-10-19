package com.example.smartstudy.schedule.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Android xóa alarm khi khởi động lại; đọc SQLite để dựng lại, không sao chép lịch. */
public class ScheduleRestoreReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        ReminderCoordinator.fromSystem(context, pending::finish);
    }
}
