import os
from pathlib import Path
import unittest
from unittest.mock import patch

from pydantic import ValidationError
from prototype import Extraction, call_gemini, load_extraction, review


def sample():
    return {'observed_scope': 'dated_week', 'week_start': '2026-10-19',
            'semester_text': 'Học kỳ 1', 'academic_year_text': '2026-2027', 'cohort_text': None,
            'lessons': [{'subject_text': 'Tương tác người máy', 'date': '2026-10-19',
                         'weekday': 'MON', 'start_time': '07:00', 'end_time': '10:35',
                         'period_start': None, 'period_end': None, 'time_basis': 'explicit',
                         'room': None, 'recurrence_kind': 'single', 'source_text': '07:00 → 10:35'}]}


class PrototypeTest(unittest.TestCase):
    def result(self, data):
        return review(Extraction.model_validate(data))['rows'][0]

    def test_dated_week_creates_single_occurrence_not_entire_semester(self):
        row = self.result(sample())
        self.assertEqual(row['status'], 'ready_for_review')
        self.assertEqual(row['schedule_preview']['valid_from'], '2026-10-19')
        self.assertEqual(row['schedule_preview']['valid_to'], '2026-10-19')
        self.assertEqual(row['schedule_preview']['day_of_week'], 1)

    def test_optional_room_does_not_block_a_draft(self):
        self.assertIsNone(self.result(sample())['schedule_preview']['room'])

    def test_term_or_unknown_scope_cannot_become_single_even_with_full_dates(self):
        for scope in ('term', 'unknown'):
            data = sample()
            data['observed_scope'] = scope
            self.assertIsNone(self.result(data)['schedule_preview'])

    def test_unknown_year_is_not_inferred_from_academic_year(self):
        data = sample()
        data.update(observed_scope='undated_week', week_start=None)
        data['lessons'][0]['date'] = None
        self.assertEqual(self.result(data)['status'], 'needs_input')

    def test_user_can_anchor_an_undated_week(self):
        data = sample()
        data.update(observed_scope='undated_week', week_start=None)
        data['lessons'][0].update(date=None, weekday='FRI')
        result = review(Extraction.model_validate(data), '2026-10-19')['rows'][0]
        self.assertEqual(result['schedule_preview']['valid_from'], '2026-10-23')

    def test_two_time_tables_remain_blocked_even_if_model_fills_hours(self):
        data = sample()
        data['lessons'][0]['time_basis'] = 'ambiguous'
        self.assertIsNone(self.result(data)['schedule_preview'])

    def test_periods_are_not_automatically_converted(self):
        data = sample()
        data['lessons'][0].update(time_basis='periods', start_time=None, end_time=None,
                                  period_start=1, period_end=4)
        self.assertEqual(self.result(data)['status'], 'needs_input')

    def test_weekday_and_date_disagreement_is_flagged(self):
        data = sample()
        data['lessons'][0]['weekday'] = 'TUE'
        self.assertIsNone(self.result(data)['schedule_preview'])

    def test_reverse_hours_and_invalid_date_do_not_make_preview(self):
        for updates in ({'start_time': '12:00'}, {'date': '2026-02-30'}, {'end_time': '25:00'}):
            data = sample()
            data['lessons'][0].update(updates)
            self.assertIsNone(self.result(data)['schedule_preview'])

    def test_irregular_or_full_term_is_not_silently_imported_as_single_day(self):
        for recurrence in ['irregular', 'weekly', 'unknown']:
            data = sample()
            data['lessons'][0]['recurrence_kind'] = recurrence
            self.assertIsNone(self.result(data)['schedule_preview'])

    def test_week_boundary_and_midnight_end(self):
        data = sample()
        data['lessons'][0].update(date='2026-10-25', weekday='SUN', start_time='23:00', end_time='24:00')
        self.assertEqual(self.result(data)['schedule_preview']['end_minute'], 1440)
        data['lessons'][0].update(date='2026-10-26', weekday='MON')
        self.assertIsNone(self.result(data)['schedule_preview'])

    def test_no_class_is_not_a_subject(self):
        data = sample()
        data['lessons'][0]['subject_text'] = 'NGHỈ HỌC!!'
        self.assertEqual(self.result(data)['status'], 'ignored_no_class')

    def test_structural_json_errors_are_rejected(self):
        data = sample()
        data['lessons'][0]['weekday'] = '2'
        with self.assertRaises(ValidationError):
            Extraction.model_validate(data)

    def test_missing_key_never_starts_network_or_reads_image(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaisesRegex(ValueError, 'GEMINI_API_KEY'):
            call_gemini(Path('not-present.png'), 'test-model')

    def test_both_manual_reference_and_api_envelope_are_loadable(self):
        import json
        for data in [sample(), {'metadata': {'source': 'test'}, 'extraction': sample()}]:
            with patch.object(Path, 'read_text', return_value=json.dumps(data)):
                self.assertEqual(len(load_extraction(Path('result.json')).lessons), 1)

    def test_four_visible_ptit_lessons_have_complete_single_day_previews(self):
        extraction = load_extraction(Path(__file__).parent / 'references/ptit_week.manual.json')
        result = review(extraction)
        self.assertEqual(result['lesson_count'], 4)
        self.assertTrue(all(row['status'] == 'ready_for_review' for row in result['rows']))
        self.assertTrue(result['human_confirmation_required'])
        self.assertFalse(result['database_written'])


if __name__ == '__main__':
    unittest.main()
