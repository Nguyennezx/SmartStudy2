"""Contract v3: AI evidence -> user-confirmed draft -> Schedule-shaped proposal.

No SQLite writes. Android must still call ScheduleRules/DAO at save time.
"""
from __future__ import annotations

from datetime import timedelta
from typing import Literal

from pydantic import Field

from layout_review import LayoutDocument, LocatedLesson, StrictModel, SchoolProfile, _profile_times, resolve, LAYOUT_PROMPT
from prototype import WEEKDAYS, minute, parse_date


class WeekRule(StrictModel):
    kind: Literal['every_week', 'listed_weeks', 'odd_weeks', 'even_weeks', 'unknown']
    weeks: list[int] = Field(description='Tuần được ghi rõ; không tự tính từ niên khóa.')
    source_text: str | None


class ImportLesson(LocatedLesson):
    subject_code: str | None
    teacher_text: str | None
    note_text: str | None
    week_rule: WeekRule
    is_makeup: bool | None


class ImportDocument(LayoutDocument):
    lessons: list[ImportLesson]


IMPORT_PROMPT = LAYOUT_PROMPT + '''
Các trường bổ sung: subject_code là mã môn riêng, teacher_text chỉ tên giảng viên,
note_text giữ ghi chú của buổi; subject_text chỉ tên môn, không ghép mã môn.
week_rule giữ quy tắc tuần riêng: every_week chỉ khi lịch cho thấy lặp mỗi tuần;
listed_weeks giữ đúng danh sách tuần ghi trên ảnh (1-3,5 -> [1,2,3,5]);
odd_weeks/even_weeks giữ phạm vi tuần được ghi trong weeks, không tự chọn tuần 1;
không rõ trả unknown và weeks=[]. Giữ nguyên bằng chứng trong source_text.
is_makeup=true chỉ khi có ghi học bù, không tự nhân buổi bù thành lịch hằng tuần.
Nếu tiêu đề là cả kỳ thì observed_scope=term, không gán single chỉ vì thấy một ô.
Không sửa các nhãn ngày/tiết mâu thuẫn để làm cho dữ liệu có vẻ hợp lệ.
'''


class RowDecision(StrictModel):
    """Human input, never included in Gemini's response schema.

    verified_fields means the displayed row, including corrections, was checked
    against the source. Overrides are an explicit field patch; absent != null.
    """
    index: int = Field(ge=0)
    include: bool
    verified_fields: bool
    subject_id: int | None = Field(default=None, gt=0)
    subject_text: str | None = None
    weekday: Literal['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'] | None = None
    date: str | None = None
    start_time: str | None = None
    end_time: str | None = None
    room: str | None = None
    note_text: str | None = None
    teacher_text: str | None = None
    recurrence_kind: Literal['single', 'weekly', 'irregular', 'unknown'] | None = None
    week_rule: WeekRule | None = None


class ImportSettings(StrictModel):
    """Provided by user/UI, separate from untrusted model output."""
    mode: Literal['week', 'term']
    week_start: str | None
    valid_from: str | None
    valid_to: str | None
    profile: SchoolProfile | None
    rows: list[RowDecision]


def _day(value):
    try:
        return parse_date(value)
    except ValueError:
        return None


def build_drafts(doc: ImportDocument, settings: ImportSettings) -> dict:
    decisions = {r.index: r for r in settings.rows}
    if len(decisions) != len(settings.rows) or any(i >= len(doc.lessons) for i in decisions):
        raise ValueError('Chỉ số dòng xác nhận trùng hoặc không tồn tại.')
    week = _day(settings.week_start)
    if settings.week_start and (week is None or week.weekday() != 0):
        raise ValueError('Tuần người dùng chọn phải bắt đầu bằng ngày thứ Hai hợp lệ.')
    # v2 provides consistency checks only. Its single-week policy is not used
    # as the v3 business rule for term schedules.
    layout = LayoutDocument.model_validate(doc.model_dump(exclude={'lessons'}) | {
        'lessons': [LocatedLesson.model_validate(r.model_dump(include=set(LocatedLesson.model_fields))).model_dump()
                    for r in doc.lessons]})
    try:
        audit = resolve(layout)['review']['rows']
    except ValueError:
        audit = [{'evidence': {'issues': ['Ngày/trục lịch từ AI không hợp lệ.']}} for _ in doc.lessons]
    clocks = _profile_times(settings.profile) if settings.profile else {}
    output = []
    for i, source in enumerate(doc.lessons):
        decision = decisions.get(i)
        data = source.model_dump()
        if decision:
            for key in decision.model_fields_set - {'index', 'include', 'verified_fields', 'subject_id'}:
                value = getattr(decision, key)
                data[key] = value.model_dump() if isinstance(value, WeekRule) else value
        row = ImportLesson.model_validate(data)
        issues = []
        excluded = decision is not None and not decision.include
        if not excluded:
            if not decision or not decision.verified_fields:
                issues.append('Cần người dùng đối chiếu và xác nhận dòng này.')
                issues.extend(audit[i].get('evidence', {}).get('issues', []))
            if not decision or not decision.subject_id:
                issues.append('Cần chọn môn học đã có trong app hoặc tạo môn trước.')
            if not row.subject_text or not row.subject_text.strip():
                issues.append('Thiếu tên môn.')
            if not row.source_text.strip():
                issues.append('Thiếu nội dung gốc để đối chiếu.')
        start, end = row.start_time, row.end_time
        user_hours = decision and {'start_time', 'end_time'} <= decision.model_fields_set
        if row.time_basis != 'explicit' and not user_hours:
            a, b = row.period_start, row.period_end
            if (clocks and a is not None and b is not None and 1 <= a <= b <= 99
                    and all(p in clocks for p in range(a, b + 1))
                    and (len(doc.time_table_labels) <= 1 or settings.profile.selected_time_table)):
                start, end = clocks[a].start, clocks[b].end
            elif not excluded:
                issues.append('Cần nhập giờ hoặc xác nhận bảng quy đổi tiết của trường.')
        try:
            a, b = minute(start), minute(end, end=True)
        except ValueError:
            a = b = None
        if (a is None or b is None or b <= a) and not excluded:
            issues.append('Giờ bắt đầu/kết thúc chưa hợp lệ.')
        first = last = None
        weekday = WEEKDAYS.index(row.weekday) + 1 if row.weekday else None
        if settings.mode == 'week':
            if row.recurrence_kind != 'single':
                issues.append('Cần xác nhận đây là buổi thuộc tuần đang nhập, không tự bỏ quy tắc lặp.')
            if doc.observed_scope not in ('dated_week', 'undated_week'):
                issues.append('Không đổi lịch cả kỳ/không rõ phạm vi thành một tuần.')
            first = _day(row.date) if doc.date_evidence == 'full_year_visible' else None
            if decision and 'date' in decision.model_fields_set:
                first = _day(decision.date)
            if first is None and week and weekday:
                first = week + timedelta(days=weekday - 1)
            last = first
            if first and (not weekday or first.isoweekday() != weekday or (week and not week <= first < week + timedelta(days=7))):
                issues.append('Ngày và thứ hoặc tuần áp dụng không khớp.')
            if row.date and doc.date_evidence == 'full_year_visible' and _day(row.date) is None:
                issues.append('Ngày ghi trong dòng không tồn tại.')
        else:
            first, last = _day(settings.valid_from), _day(settings.valid_to)
            if doc.observed_scope != 'term':
                issues.append('Không tự nhân ảnh một tuần thành lịch cả kỳ.')
            if row.recurrence_kind != 'weekly' or row.week_rule.kind != 'every_week' or row.week_rule.weeks:
                issues.append('Quy tắc tuần chưa phải lặp mỗi tuần; giữ nguyên để xử lý riêng.')
            if row.is_makeup is not False:
                issues.append('Buổi bù/chưa rõ loại buổi cần chọn ngày riêng, không lặp cả kỳ.')
        if not first or not last or last < first:
            issues.append('Cần khoảng ngày áp dụng hợp lệ.')
        elif weekday and first + timedelta(days=(weekday - first.isoweekday()) % 7) > last:
            issues.append('Khoảng ngày không có buổi học vào thứ này.')
        if not weekday:
            issues.append('Cần chọn thứ trong tuần.')
        proposal = None
        if not excluded and not issues:
            proposal = {'subject_id': decision.subject_id, 'day_of_week': weekday,
                        'start_minute': a, 'end_minute': b, 'valid_from': first.isoformat(),
                        'valid_to': last.isoformat(), 'time_zone_id': 'Asia/Ho_Chi_Minh',
                        'room': row.room, 'note': row.note_text, 'remind_before_minutes': None}
        output.append({'index': i, 'status': 'excluded' if excluded else 'needs_input' if issues else 'ready_for_review',
                       'fields': row.model_dump(), 'issues': [] if excluded else list(dict.fromkeys(issues)),
                       'schedule_proposal': proposal, 'conflicts_with': []})
    # Compare actual weekly occurrences, not just intersecting calendar ranges.
    for i, left in enumerate(output):
        a = left['schedule_proposal']
        if not a:
            continue
        for right in output[i + 1:]:
            b = right['schedule_proposal']
            if not b or a['day_of_week'] != b['day_of_week']:
                continue
            first = max(parse_date(a['valid_from']), parse_date(b['valid_from']))
            last = min(parse_date(a['valid_to']), parse_date(b['valid_to']))
            occurrence = first + timedelta(days=(a['day_of_week'] - first.isoweekday()) % 7)
            if occurrence <= last and a['start_minute'] < b['end_minute'] and b['start_minute'] < a['end_minute']:
                left['conflicts_with'].append(right['index'])
                right['conflicts_with'].append(left['index'])
    for row in output:
        if row['conflicts_with']:
            row.update(status='conflict', schedule_proposal=None)
            row['issues'].append('Trùng giờ với dòng khác trong bản nháp; cần chỉnh hoặc bỏ chọn.')
    return {'schema_version': 3, 'human_confirmation_required': True, 'database_written': False,
            'existing_database_check': 'required_in_android_dao_at_save', 'rows': output}
