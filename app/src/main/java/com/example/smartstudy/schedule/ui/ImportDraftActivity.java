package com.example.smartstudy.schedule.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.content.ActivityNotFoundException;
import android.text.InputType;
import androidx.lifecycle.ViewModelProvider;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.core.content.FileProvider;
import com.example.smartstudy.R;
import com.example.smartstudy.schedule.data.ImportLessonDraft;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;

/** Chọn ảnh, nhận JSON từ backend và duyệt từng buổi; không tự ghi database. */
public class ImportDraftActivity extends AppCompatActivity {
    public static final String EXTRA_JSON = "import_draft_json";
    private File cameraFile;
    private AlertDialog activeDialog;
    private final List<ImportLessonDraft> rows = new ArrayList<>();
    private ImportDraftAdapter adapter;
    private String raw;
    private int pending = -1;
    private ImportAnalysisViewModel analysis;
    private boolean analyzing;
    private final ActivityResultLauncher<String[]> chooseImage = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                analyzeImage(uri);
            });
    private final ActivityResultLauncher<Uri> takePhoto = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraFile != null && cameraFile.length() > 0) {
                    analyzeImage(FileProvider.getUriForFile(this, getPackageName() + ".timetable.images", cameraFile));
                } else clearCameraFile();
            });
    private final ActivityResultLauncher<Intent> edit = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && pending >= 0 && pending < rows.size()) rows.get(pending).saved = true;
                pending = -1;
                if (adapter != null) { adapter.notifyDataSetChanged(); render(); }
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_import_draft);
        adaptSourceButtons();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.import_root), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        raw = state == null ? getIntent().getStringExtra(EXTRA_JSON) : state.getString(EXTRA_JSON);
        if (state != null && state.getString("camera_file") != null) {
            File restored = new File(state.getString("camera_file"));
            if (isCameraFile(restored)) cameraFile = restored;
        }
        findViewById(R.id.import_back).setOnClickListener(v -> finish());
        findViewById(R.id.import_choose_image).setOnClickListener(v -> pickImage(1));
        findViewById(R.id.import_take_photo).setOnClickListener(v -> pickImage(0));
        findViewById(R.id.import_connection).setOnClickListener(v -> connection());
        try {
            if (raw != null) rows.addAll(ImportLessonDraft.parse(raw));
        } catch (JSONException error) {
            ((TextView) findViewById(R.id.import_empty)).setText(R.string.import_invalid);
        }
        if (state != null) {
            pending = state.getInt("pending", -1);
            boolean[] saved = state.getBooleanArray("saved"), skipped = state.getBooleanArray("skipped");
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).saved = saved != null && i < saved.length && saved[i];
                rows.get(i).skipped = skipped != null && i < skipped.length && skipped[i];
            }
        }
        adapter = new ImportDraftAdapter(rows, this::render);
        ListView list = findViewById(R.id.import_list);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> openRow(position));
        findViewById(R.id.import_next).setOnClickListener(v -> {
            for (int i = 0; i < rows.size(); i++) {
                ImportLessonDraft row = rows.get(i);
                if (!row.saved && !row.skipped && row.supported) { openRow(i); return; }
            }
            Toast.makeText(this, R.string.import_no_next, Toast.LENGTH_SHORT).show();
        });
        analysis = new ViewModelProvider(this).get(ImportAnalysisViewModel.class);
        analysis.state().observe(this, result -> {
            analyzing = result.busy;
            if (!analyzing && (!result.message.isEmpty() || result.json != null)) clearCameraFile();
            findViewById(R.id.import_choose_image).setEnabled(!analyzing);
            findViewById(R.id.import_take_photo).setEnabled(!analyzing);
            findViewById(R.id.import_connection).setEnabled(!analyzing);
            if (result.json != null) {
                try {
                    List<ImportLessonDraft> parsed = ImportLessonDraft.parse(result.json);
                    raw = result.json;
                    rows.clear();
                    rows.addAll(parsed);
                    adapter.notifyDataSetChanged();
                    ((TextView) findViewById(R.id.import_empty)).setText(R.string.import_nothing_found);
                } catch (JSONException error) {
                    Toast.makeText(this, R.string.import_invalid, Toast.LENGTH_LONG).show();
                }
                analysis.consume();
            }
            ((TextView) findViewById(R.id.import_message)).setText(result.message);
            render();
        });
        render();
    }

    private void pickImage(int source) {
        if (analyzing || isFinishing() || isDestroyed()) return;
        Runnable open = () -> {
            Runnable launch = () -> launchSource(source);
            SharedPreferences p = getSharedPreferences("import_connection", MODE_PRIVATE);
            if (p.getString("url", "").isEmpty() || p.getString("token", "").isEmpty()) connection(launch);
            else launch.run();
        };
        if (rows.isEmpty()) open.run();
        else activeDialog = new AlertDialog.Builder(this).setMessage("Chọn ảnh mới sẽ thay danh sách đang xem. Các lịch đã lưu vẫn được giữ.")
                .setNegativeButton("Hủy", null).setPositiveButton("Chọn ảnh", (d, w) -> open.run()).show();
    }

    private void launchSource(int source) {
        try {
            if (source == 0) {
                clearCameraFile();
                File folder = new File(getCacheDir(), "timetable_capture");
                if (!folder.exists() && !folder.mkdirs()) throw new IOException();
                cameraFile = File.createTempFile("tkb_", ".jpg", folder);
                takePhoto.launch(FileProvider.getUriForFile(this, getPackageName() + ".timetable.images", cameraFile));
            } else chooseImage.launch(new String[]{"image/png", "image/jpeg"});
        } catch (ActivityNotFoundException error) {
            clearCameraFile();
            Toast.makeText(this, R.string.import_source_unavailable, Toast.LENGTH_LONG).show();
        } catch (IOException | SecurityException | IllegalArgumentException error) {
            clearCameraFile();
            Toast.makeText(this, R.string.import_camera_error, Toast.LENGTH_LONG).show();
        }
    }

    private void analyzeImage(Uri uri) {
        SharedPreferences p = getSharedPreferences("import_connection", MODE_PRIVATE);
        analysis.analyze(uri, p.getString("url", ""), p.getString("token", ""));
    }

    private boolean isCameraFile(File file) {
        try {
            return new File(getCacheDir(), "timetable_capture").getCanonicalFile().equals(file.getCanonicalFile().getParentFile())
                    && file.getName().startsWith("tkb_") && file.getName().endsWith(".jpg");
        } catch (IOException error) { return false; }
    }

    private void clearCameraFile() {
        if (cameraFile != null && isCameraFile(cameraFile)) cameraFile.delete();
        cameraFile = null;
    }

    private void connection() {
        connection(null);
    }

    private void connection(Runnable onReady) {
        SharedPreferences p = getSharedPreferences("import_connection", MODE_PRIVATE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(padding, padding, padding, padding);
        EditText url = new EditText(this), token = new EditText(this);
        url.setHint("Địa chỉ máy chủ, ví dụ https://…");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(p.getString("url", ""));
        token.setHint("Mã kết nối (không phải Gemini API key)");
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setText(p.getString("token", ""));
        TextView urlLabel = new TextView(this);
        urlLabel.setText(R.string.import_server_address);
        url.setId(View.generateViewId());
        urlLabel.setLabelFor(url.getId());
        TextView tokenLabel = new TextView(this);
        tokenLabel.setText(R.string.import_server_token);
        tokenLabel.setPadding(0, padding / 2, 0, 0);
        token.setId(View.generateViewId());
        tokenLabel.setLabelFor(token.getId());
        box.addView(urlLabel);
        box.addView(url);
        box.addView(tokenLabel);
        box.addView(token);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Kết nối máy chủ phân tích")
                .setView(box).setNegativeButton("Hủy", null).setPositiveButton("Lưu kết nối", null).create();
        activeDialog = dialog;
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String address = url.getText().toString().trim().replaceAll("/+$", "");
            boolean debug = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            try {
                java.net.URI uri = new java.net.URI(address);
                if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                        || !("https".equals(uri.getScheme()) || debug && "http".equals(uri.getScheme()))
                        || !uri.getPath().isEmpty() || token.getText().length() < 24) throw new IllegalArgumentException();
                p.edit().putString("url", address).putString("token", token.getText().toString()).apply();
                dialog.dismiss();
                if (onReady != null) onReady.run();
            } catch (Exception error) { url.setError("Nhập địa chỉ HTTPS và mã kết nối ít nhất 24 ký tự. Bản debug cho phép HTTP nội bộ."); }
        }));
        dialog.show();
    }

    private void openRow(int position) {
        if (analyzing) return;
        ImportLessonDraft row = rows.get(position);
        if (row.saved || row.skipped) return;
        activeDialog = new AlertDialog.Builder(this).setTitle(row.subject.isEmpty() ? "Buổi học" : row.subject)
                .setMessage(row.source + "\n\n" + row.problem)
                .setNegativeButton("Đóng", null)
                .setPositiveButton(row.supported ? "Kiểm tra trong form" : "Đã hiểu", (dialog, which) -> {
                    if (!row.supported) return;
                    pending = position;
                    Intent intent = new Intent(this, ScheduleEditActivity.class);
                    intent.putExtra(ScheduleEditActivity.EXTRA_IMPORT, row.toForm());
                    edit.launch(intent);
                }).show();
    }

    private void render() {
        boolean hasRows = !rows.isEmpty();
        ((TextView) findViewById(R.id.import_title)).setText(hasRows ? R.string.import_results_title : R.string.import_title);
        for (int id : new int[]{R.id.import_illustration, R.id.import_upload_title,
                R.id.import_upload_hint, R.id.import_subtitle, R.id.import_steps}) {
            findViewById(id).setVisibility(hasRows ? View.GONE : View.VISIBLE);
        }
        View intro = findViewById(R.id.import_intro);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) intro.getLayoutParams();
        params.height = hasRows ? LinearLayout.LayoutParams.WRAP_CONTENT : 0;
        params.weight = hasRows ? 0 : 1;
        intro.setLayoutParams(params);
        findViewById(R.id.import_list).setVisibility(hasRows ? View.VISIBLE : View.GONE);
        findViewById(R.id.import_summary).setVisibility(hasRows ? View.VISIBLE : View.GONE);
        findViewById(R.id.import_next).setVisibility(hasRows ? View.VISIBLE : View.GONE);
        findViewById(R.id.import_progress).setVisibility(analyzing ? View.VISIBLE : View.GONE);
        TextView message = findViewById(R.id.import_message);
        message.setVisibility(message.getText().length() == 0 ? View.GONE : View.VISIBLE);
        long saved = rows.stream().filter(row -> row.saved).count();
        ((TextView) findViewById(R.id.import_summary)).setText(getString(R.string.import_count, rows.size(), saved));
        TextView empty = findViewById(R.id.import_empty);
        if (!hasRows && raw != null && empty.getText().length() == 0) empty.setText(R.string.import_nothing_found);
        empty.setVisibility(!hasRows && !analyzing && empty.getText().length() > 0 ? View.VISIBLE : View.GONE);
        findViewById(R.id.import_next).setEnabled(!analyzing && rows.stream().anyMatch(row -> row.supported && !row.skipped && !row.saved));
    }

    private void adaptSourceButtons() {
        android.content.res.Configuration config = getResources().getConfiguration();
        if (config.screenWidthDp / config.fontScale >= 340) return;
        LinearLayout row = findViewById(R.id.import_action_row);
        row.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < row.getChildCount(); i++) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) row.getChildAt(i).getLayoutParams();
            p.width = LinearLayout.LayoutParams.MATCH_PARENT;
            p.weight = 0;
            p.setMarginEnd(0);
            row.getChildAt(i).setLayoutParams(p);
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(EXTRA_JSON, raw);
        state.putInt("pending", pending);
        if (cameraFile != null) state.putString("camera_file", cameraFile.getPath());
        boolean[] saved = new boolean[rows.size()], skipped = new boolean[rows.size()];
        for (int i = 0; i < rows.size(); i++) { saved[i] = rows.get(i).saved; skipped[i] = rows.get(i).skipped; }
        state.putBooleanArray("saved", saved);
        state.putBooleanArray("skipped", skipped);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (activeDialog != null) activeDialog.dismiss();
        super.onDestroy();
    }
}
