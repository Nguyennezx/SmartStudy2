package com.example.smartstudy.core.database;

/** Schema v1: 11 tables, 14 indexes and one read-only view. */
final class DatabaseSchema {
    private DatabaseSchema() {}

    static final String[] CREATE_STATEMENTS = {
            "CREATE TABLE user_profile (\n" +
                    "    id INTEGER PRIMARY KEY CHECK(id = 1),\n" +
                    "    display_name TEXT NOT NULL DEFAULT 'Bạn' CHECK(length(trim(display_name)) > 0),\n" +
                    "    avatar_uri TEXT,\n" +
                    "    created_at_ms INTEGER NOT NULL CHECK(created_at_ms >= 0),\n" +
                    "    updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms >= created_at_ms)\n" +
                    ");",
            "CREATE TABLE app_settings (\n" +
                    "    id INTEGER PRIMARY KEY CHECK(id = 1),\n" +
                    "    focus_minutes INTEGER NOT NULL DEFAULT 25 CHECK(focus_minutes BETWEEN 1 AND 240),\n" +
                    "    short_break_minutes INTEGER NOT NULL DEFAULT 5 CHECK(short_break_minutes BETWEEN 1 AND 60),\n" +
                    "    long_break_minutes INTEGER NOT NULL DEFAULT 15 CHECK(long_break_minutes BETWEEN 1 AND 120),\n" +
                    "    cycles_before_long_break INTEGER NOT NULL DEFAULT 4 CHECK(cycles_before_long_break BETWEEN 1 AND 12),\n" +
                    "    daily_goal_minutes INTEGER NOT NULL DEFAULT 120 CHECK(daily_goal_minutes BETWEEN 1 AND 1440),\n" +
                    "    reminders_enabled INTEGER NOT NULL DEFAULT 1 CHECK(reminders_enabled IN (0,1)),\n" +
                    "    time_zone_id TEXT NOT NULL DEFAULT 'Asia/Ho_Chi_Minh' CHECK(length(trim(time_zone_id)) > 0),\n" +
                    "    created_at_ms INTEGER NOT NULL CHECK(created_at_ms >= 0),\n" +
                    "    updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms >= created_at_ms)\n" +
                    ");",
            "CREATE TABLE semesters (\n" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "    name TEXT NOT NULL CHECK(length(trim(name)) > 0),\n" +
                    "    start_date TEXT NOT NULL CHECK(start_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),\n" +
                    "    end_date TEXT NOT NULL CHECK(end_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),\n" +
                    "    is_archived INTEGER NOT NULL DEFAULT 0 CHECK(is_archived IN (0,1)),\n" +
                    "    created_at_ms INTEGER NOT NULL CHECK(created_at_ms >= 0),\n" +
                    "    updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms >= created_at_ms),\n" +
                    "    CHECK(end_date >= start_date)\n" +
                    ");",
            "CREATE TABLE subjects (\n" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "    semester_id INTEGER REFERENCES semesters(id) ON DELETE RESTRICT,\n" +
                    "    name TEXT NOT NULL CHECK(length(trim(name)) > 0),\n" +
                    "    teacher_name TEXT,\n" +
                    "    color_hex TEXT NOT NULL DEFAULT '#6C7EE1' CHECK(length(color_hex)=7 AND substr(color_hex,1,1)='#' AND substr(color_hex,2) NOT GLOB '*[^0-9A-Fa-f]*'),\n" +
                    "    is_archived INTEGER NOT NULL DEFAULT 0 CHECK(is_archived IN (0,1)),\n" +
                    "    created_at_ms INTEGER NOT NULL CHECK(created_at_ms >= 0),\n" +
                    "    updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms >= created_at_ms)\n" +
                    ");",
            "CREATE TABLE schedules (\n" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "    subject_id INTEGER NOT NULL REFERENCES subjects(id) ON DELETE RESTRICT,\n" +
                    "    day_of_week INTEGER NOT NULL CHECK(day_of_week BETWEEN 1 AND 7),\n" +
                    "    start_minute INTEGER NOT NULL CHECK(start_minute BETWEEN 0 AND 1439),\n" +
                    "    end_minute INTEGER NOT NULL CHECK(end_minute BETWEEN 1 AND 1440),\n" +
                    "    valid_from TEXT NOT NULL CHECK(valid_from GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),\n" +
                    "    valid_to TEXT NOT NULL CHECK(valid_to GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),\n" +
                    "    time_zone_id TEXT NOT NULL DEFAULT 'Asia/Ho_Chi_Minh' CHECK(length(trim(time_zone_id)) > 0),\n" +
                    "    room TEXT,\n" +
                    "    note TEXT,\n" +
                    "    remind_before_minutes INTEGER CHECK(remind_before_minutes BETWEEN 0 AND 10080),\n" +
                    "    is_archived INTEGER NOT NULL DEFAULT 0 CHECK(is_archived IN (0,1)),\n" +
                    "    created_at_ms INTEGER NOT NULL CHECK(created_at_ms >= 0),\n" +
                    "    updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms >= created_at_ms),\n" +
                    "    CHECK(end_minute > start_minute),\n" +
                    "    CHECK(valid_to >= valid_from)\n" +
                    ");",
            "CREATE TABLE schedule_exceptions (\n" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "    schedule_id INTEGER NOT NULL REFERENCES schedules(id) ON DELETE CASCADE,\n" +
                    "    original_date TEXT NOT NULL CHECK(original_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),\n" +
                    "    action TEXT NOT NULL CHECK(action IN ('cancelled','rescheduled')),\n" +
                    "    new_date TEXT CHECK(new_date IS NULL OR new_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),\n" +
                    "    new_start_minute INTEGER CHECK(new_start_minute BETWEEN 0 AND 1439),\n" +
                    "    new_end_minute INTEGER CHECK(new_end_minute BETWEEN 1 AND 1440),\n" +
                    "    new_room TEXT,\n" +
                    "    note TEXT,\n" +
                    "    created_at_ms INTEGER NOT NULL CHECK(created_at_ms >= 0),\n" +
                    "    updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms >= created_at_ms),\n" +
                    "    UNIQUE(schedule_id, original_date),\n" +
                    "    CHECK((action='cancelled' AND new_date IS NULL AND new_start_minute IS NULL AND new_end_minute IS NULL AND new_room IS NULL) OR (action='rescheduled' AND new_date IS NOT NULL AND new_start_minute IS NOT NULL AND new_end_minute IS NOT NULL AND new_end_minute > new_start_minute))\n" +
                    ");",
            "CREATE TABLE assignments (\n" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "    subject_id INTEGER NOT NULL REFERENCES subjects(id) ON DELETE RESTRICT,\n" +
                    "    title TEXT NOT NULL CHECK(length(trim(title)) > 0),\n" +
                    "    description TEXT,\n" +
                    "    due_at_ms INTEGER CHECK(due_at_ms >= 0),\n" +
                    "    priority TEXT NOT NULL DEFAULT 'medium' CHECK(priority IN ('low','medium','high')),\n" +
                    "    status TEXT NOT NULL DEFAULT 'todo' CHECK(status IN ('todo','in_progress','done')),\n" +
                    "    completed_at_ms INTEGER CHECK(completed_at_ms >= 0),\n" +
                    "    remind_before_minutes INTEGER CHECK(remind_before_minutes BETWEEN 0 AND 10080),\n" +
                    "    created_at_ms INTEGER NOT NULL CHECK(created_at_ms >= 0),\n" +
                    "    updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms >= created_at_ms),\n" +
                    "    CHECK((status='done' AND completed_at_ms IS NOT NULL) OR (status<>'done' AND completed_at_ms IS NULL)),\n" +
                    "    CHECK(remind_before_minutes IS NULL OR due_at_ms IS NOT NULL)\n" +
                    ");",
            "CREATE TABLE exams (\n" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "    subject_id INTEGER NOT NULL REFERENCES subjects(id) ON DELETE RESTRICT,\n" +
                    "    title TEXT NOT NULL CHECK(length(trim(title)) > 0),\n" +
                    "    starts_at_ms INTEGER NOT NULL CHECK(starts_at_ms >= 0),\n" +
                    "    duration_minutes INTEGER NOT NULL CHECK(duration_minutes BETWEEN 1 AND 1440),\n" +
                    "    location TEXT,\n" +
                    "    note TEXT,\n" +
                    "    remind_before_minutes INTEGER CHECK(remind_before_minutes BETWEEN 0 AND 10080),\n" +
                    "    is_cancelled INTEGER NOT NULL DEFAULT 0 CHECK(is_cancelled IN (0,1)),\n" +
                    "    created_at_ms INTEGER NOT NULL CHECK(created_at_ms >= 0),\n" +
                    "    updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms >= created_at_ms)\n" +
                    ");",
            "CREATE TABLE devices (\n" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "    device_uid TEXT UNIQUE CHECK(device_uid IS NULL OR length(trim(device_uid)) > 0),\n" +
                    "    display_name TEXT NOT NULL CHECK(length(trim(display_name)) > 0),\n" +
                    "    last_ble_address TEXT,\n" +
                    "    firmware_version TEXT,\n" +
                    "    angle_threshold_deg REAL NOT NULL DEFAULT 15 CHECK(angle_threshold_deg > 0 AND angle_threshold_deg <= 180),\n" +
                    "    bad_posture_delay_ms INTEGER NOT NULL DEFAULT 5000 CHECK(bad_posture_delay_ms BETWEEN 0 AND 600000),\n" +
                    "    vibration_enabled INTEGER NOT NULL DEFAULT 1 CHECK(vibration_enabled IN (0,1)),\n" +
                    "    calibrated_at_ms INTEGER CHECK(calibrated_at_ms >= 0),\n" +
                    "    last_seen_at_ms INTEGER CHECK(last_seen_at_ms >= 0),\n" +
                    "    is_archived INTEGER NOT NULL DEFAULT 0 CHECK(is_archived IN (0,1)),\n" +
                    "    created_at_ms INTEGER NOT NULL CHECK(created_at_ms >= 0),\n" +
                    "    updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms >= created_at_ms)\n" +
                    ");",
            "CREATE TABLE study_sessions (\n" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "    subject_id INTEGER NOT NULL REFERENCES subjects(id) ON DELETE RESTRICT,\n" +
                    "    device_id INTEGER REFERENCES devices(id) ON DELETE RESTRICT,\n" +
                    "    goal TEXT,\n" +
                    "    mode TEXT NOT NULL DEFAULT 'pomodoro' CHECK(mode IN ('pomodoro','free')),\n" +
                    "    scheduled_at_ms INTEGER CHECK(scheduled_at_ms >= 0),\n" +
                    "    started_at_ms INTEGER CHECK(started_at_ms >= 0),\n" +
                    "    ended_at_ms INTEGER CHECK(ended_at_ms >= 0),\n" +
                    "    planned_focus_seconds INTEGER NOT NULL CHECK(planned_focus_seconds > 0),\n" +
                    "    focus_seconds INTEGER NOT NULL DEFAULT 0 CHECK(focus_seconds >= 0),\n" +
                    "    tracked_seconds INTEGER NOT NULL DEFAULT 0 CHECK(tracked_seconds >= 0),\n" +
                    "    correct_seconds INTEGER NOT NULL DEFAULT 0 CHECK(correct_seconds >= 0),\n" +
                    "    focus_block_seconds INTEGER NOT NULL DEFAULT 1500 CHECK(focus_block_seconds > 0),\n" +
                    "    short_break_seconds INTEGER NOT NULL DEFAULT 300 CHECK(short_break_seconds > 0),\n" +
                    "    long_break_seconds INTEGER NOT NULL DEFAULT 900 CHECK(long_break_seconds > 0),\n" +
                    "    cycles_before_long_break INTEGER NOT NULL DEFAULT 4 CHECK(cycles_before_long_break > 0),\n" +
                    "    status TEXT NOT NULL DEFAULT 'planned' CHECK(status IN ('planned','running','paused','completed','cancelled','interrupted')),\n" +
                    "    created_at_ms INTEGER NOT NULL CHECK(created_at_ms >= 0),\n" +
                    "    updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms >= created_at_ms),\n" +
                    "    CHECK(correct_seconds <= tracked_seconds AND tracked_seconds <= focus_seconds),\n" +
                    "    CHECK(device_id IS NOT NULL OR tracked_seconds = 0),\n" +
                    "    CHECK(started_at_ms IS NULL OR ended_at_ms IS NULL OR ended_at_ms >= started_at_ms),\n" +
                    "    CHECK((status='planned' AND started_at_ms IS NULL AND ended_at_ms IS NULL AND focus_seconds=0) OR (status IN ('running','paused') AND started_at_ms IS NOT NULL AND ended_at_ms IS NULL) OR (status IN ('completed','interrupted') AND started_at_ms IS NOT NULL AND ended_at_ms IS NOT NULL) OR (status='cancelled' AND ended_at_ms IS NOT NULL)),\n" +
                    "    CHECK(started_at_ms IS NOT NULL OR focus_seconds=0)\n" +
                    ");",
            "CREATE TABLE posture_events (\n" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    "    session_id INTEGER NOT NULL REFERENCES study_sessions(id) ON DELETE CASCADE,\n" +
                    "    event_type TEXT NOT NULL CHECK(event_type IN ('bad_posture','disconnected','sensor_error')),\n" +
                    "    started_at_ms INTEGER NOT NULL CHECK(started_at_ms >= 0),\n" +
                    "    ended_at_ms INTEGER CHECK(ended_at_ms >= 0),\n" +
                    "    peak_angle_deg REAL CHECK(peak_angle_deg BETWEEN 0 AND 180),\n" +
                    "    alert_count INTEGER NOT NULL DEFAULT 0 CHECK(alert_count >= 0),\n" +
                    "    end_reason TEXT CHECK(end_reason IN ('recovered','paused','session_ended','connection_lost','interrupted','state_changed')),\n" +
                    "    created_at_ms INTEGER NOT NULL CHECK(created_at_ms >= 0),\n" +
                    "    updated_at_ms INTEGER NOT NULL CHECK(updated_at_ms >= created_at_ms),\n" +
                    "    CHECK(ended_at_ms IS NULL OR ended_at_ms >= started_at_ms),\n" +
                    "    CHECK(event_type='bad_posture' OR (peak_angle_deg IS NULL AND alert_count=0)),\n" +
                    "    CHECK((ended_at_ms IS NULL AND end_reason IS NULL) OR (ended_at_ms IS NOT NULL AND end_reason IS NOT NULL))\n" +
                    ");",
            "CREATE INDEX idx_subjects_semester ON subjects(semester_id);",
            "CREATE INDEX idx_schedules_subject ON schedules(subject_id);",
            "CREATE INDEX idx_schedules_day ON schedules(day_of_week, is_archived, valid_from, valid_to);",
            "CREATE INDEX idx_exceptions_new_date ON schedule_exceptions(new_date);",
            "CREATE INDEX idx_assignments_subject ON assignments(subject_id);",
            "CREATE INDEX idx_assignments_due ON assignments(status, due_at_ms);",
            "CREATE INDEX idx_exams_subject ON exams(subject_id);",
            "CREATE INDEX idx_exams_start ON exams(starts_at_ms);",
            "CREATE INDEX idx_sessions_subject_start ON study_sessions(subject_id, started_at_ms);",
            "CREATE INDEX idx_sessions_device ON study_sessions(device_id);",
            "CREATE INDEX idx_sessions_start ON study_sessions(started_at_ms);",
            "CREATE INDEX idx_events_session_start ON posture_events(session_id, started_at_ms);",
            "CREATE UNIQUE INDEX idx_one_active_session ON study_sessions((1)) WHERE status IN ('running','paused');",
            "CREATE UNIQUE INDEX idx_one_open_posture_event ON posture_events(session_id) WHERE ended_at_ms IS NULL;",
            "CREATE VIEW v_session_summary AS\n" +
                    "SELECT s.id, s.subject_id, sub.name AS subject_name, s.status,\n" +
                    "       s.started_at_ms, s.ended_at_ms, s.focus_seconds,\n" +
                    "       s.tracked_seconds, s.correct_seconds,\n" +
                    "       CASE WHEN s.tracked_seconds > 0\n" +
                    "            THEN 100.0 * s.correct_seconds / s.tracked_seconds\n" +
                    "            ELSE NULL END AS correct_percent,\n" +
                    "       (SELECT COUNT(*) FROM posture_events p\n" +
                    "        WHERE p.session_id=s.id AND p.event_type='bad_posture') AS bad_posture_event_count,\n" +
                    "       (SELECT COALESCE(SUM(p.alert_count),0) FROM posture_events p\n" +
                    "        WHERE p.session_id=s.id) AS alert_count\n" +
                    "FROM study_sessions s JOIN subjects sub ON sub.id=s.subject_id;",
    };
}
