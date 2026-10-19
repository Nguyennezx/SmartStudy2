package com.example.smartstudy.schedule.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.smartstudy.schedule.data.Schedule;
import com.example.smartstudy.schedule.data.ScheduleDAO;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Giữ tác vụ lưu khi xoay màn hình, tránh tạo hai lịch do bấm Lưu nhiều lần. */
public class ScheduleEditViewModel extends AndroidViewModel {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<String> result = new MutableLiveData<>();
    private boolean saving;

    public ScheduleEditViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<String> getResult() {
        return result;
    }

    public boolean isSaving() {
        return saving;
    }

    public void save(Schedule schedule) {
        if (saving) {
            return;
        }
        saving = true;
        executor.execute(() -> {
            try {
                new ScheduleDAO(getApplication()).save(schedule);
                result.postValue("");
            } catch (IllegalArgumentException error) {
                result.postValue(error.getMessage());
            } catch (Exception error) {
                android.util.Log.e("ScheduleEdit", "Không lưu được lịch học", error);
                result.postValue("Chưa lưu được lịch học. Vui lòng thử lại.");
            }
        });
    }

    public void clearError() {
        saving = false;
        result.setValue(null);
    }

    @Override
    protected void onCleared() {
        executor.shutdown();
    }
}
