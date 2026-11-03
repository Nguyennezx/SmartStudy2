package com.example.smartstudy.home;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.smartstudy.home.data.HomeDAO;
import com.example.smartstudy.home.data.HomeSnapshot;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Giữ dữ liệu qua xoay màn hình; Fragment không chạy SQL. */
public class HomeViewModel extends AndroidViewModel {
    public static final class State {
        public final HomeSnapshot data;
        public final boolean loading;
        public final String error;
        State(HomeSnapshot data, boolean loading, String error) {
            this.data = data; this.loading = loading; this.error = error;
        }
    }
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(null, false, ""));
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean loading, cleared;
    public HomeViewModel(Application app) { super(app); }
    public LiveData<State> state() { return state; }
    public void reload() {
        if (loading || cleared) return;
        loading = true;
        state.setValue(new State(state.getValue().data, true, ""));
        executor.execute(() -> {
            HomeSnapshot data = null;
            String error = "";
            try { data = new HomeDAO(getApplication()).load(Instant.now()); }
            catch (Exception failure) {
                android.util.Log.e("Home", "Không đọc được dữ liệu hôm nay", failure);
                error = "Chưa tải được dữ liệu. Chạm để thử lại.";
            }
            HomeSnapshot result = data;
            String message = error;
            main.post(() -> {
                loading = false;
                if (!cleared) state.setValue(new State(result, false, message));
            });
        });
    }
    @Override protected void onCleared() { cleared = true; executor.shutdown(); }
}
