package com.example.smartstudy.schedule.ui;

import android.app.Application;
import android.net.Uri;
import android.util.Base64;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Volley/JSON (C7); giữ request qua xoay màn hình, không giữ Activity hoặc API key. */
public class ImportAnalysisViewModel extends AndroidViewModel {
    public static final class State {
        public final boolean busy;
        public final String json, message;
        State(boolean busy, String json, String message) { this.busy = busy; this.json = json; this.message = message; }
    }
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, null, ""));
    private final RequestQueue queue;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean cleared;

    public ImportAnalysisViewModel(Application application) {
        super(application);
        queue = Volley.newRequestQueue(application);
    }
    public LiveData<State> state() { return state; }

    public void analyze(Uri uri, String baseUrl, String token) {
        if (state.getValue() != null && state.getValue().busy) return;
        state.setValue(new State(true, null, "Đang phân tích ảnh…"));
        worker.execute(() -> {
            try (InputStream input = getApplication().getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) throw new IllegalArgumentException();
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (output.size() + length > 8 * 1024 * 1024) throw new IllegalArgumentException();
                    output.write(buffer, 0, length);
                }
                JSONObject body = new JSONObject();
                body.put("image_base64", Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP));
                JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST,
                        baseUrl + "/v1/timetable/extract", body,
                        response -> state.setValue(new State(false, response.toString(), "")),
                        error -> {
                            int code = error.networkResponse == null ? 0 : error.networkResponse.statusCode;
                            String message = code == 401 ? "Thông tin kết nối chưa đúng. Kiểm tra kết nối máy chủ."
                                    : code == 429 ? "Dịch vụ AI đang bận hoặc hết hạn mức. Thử lại sau."
                                    : code == 413 || code == 400 ? "Chọn ảnh PNG/JPEG tối đa 8 MiB."
                                    : "Chưa phân tích được ảnh. Kiểm tra mạng và máy chủ rồi thử lại.";
                            state.setValue(new State(false, null, message));
                        }) {
                    @Override public Map<String, String> getHeaders() {
                        return Collections.singletonMap("Authorization", "Bearer " + token);
                    }
                };
                request.setShouldCache(false);
                // Free cloud instance can need time to wake before the AI request.
                request.setRetryPolicy(new DefaultRetryPolicy(180000, 0, 1f));
                request.setTag(this);
                synchronized (this) {
                    if (!cleared) queue.add(request);
                }
            } catch (Exception error) {
                if (!cleared) state.postValue(new State(false, null, "Không đọc được ảnh. Chọn PNG/JPEG tối đa 8 MiB."));
            }
        });
    }

    public void consume() { state.setValue(new State(false, null, "")); }

    @Override protected synchronized void onCleared() {
        cleared = true;
        queue.cancelAll(this);
        queue.stop();
        worker.shutdownNow();
    }
}
