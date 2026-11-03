import copy
import unittest

from import_draft import ImportDocument, ImportSettings, build_drafts
from test_layout_review import grid


def document(term=False):
    data = grid()
    data['lessons'][0].update(subject_code='INT1', teacher_text='Thầy A', note_text='Mang laptop',
                             week_rule={'kind': 'every_week' if term else 'unknown', 'weeks': [], 'source_text': None},
                             is_makeup=False, recurrence_kind='weekly' if term else 'single')
    if term:
        data.update(observed_scope='term', week_start=None, date_evidence='missing')
        data['lessons'][0]['date'] = None
    return data


def settings(term=False):
    return {'mode': 'term' if term else 'week', 'week_start': None if term else '2026-10-19',
            'valid_from': '2026-10-19' if term else None, 'valid_to': '2027-01-01' if term else None,
            'profile': None,
            'rows': [{'index': 0, 'include': True, 'verified_fields': True, 'subject_id': 8}]}


def run(data, config):
    return build_drafts(ImportDocument.model_validate(data), ImportSettings.model_validate(config))


class ImportDraftTest(unittest.TestCase):
    def test_week_maps_to_existing_schedule_contract(self):
        row = run(document(), settings())['rows'][0]
        self.assertEqual(row['status'], 'ready_for_review')
        self.assertEqual(row['schedule_proposal'], {
            'subject_id': 8, 'day_of_week': 1, 'start_minute': 420, 'end_minute': 635,
            'valid_from': '2026-10-19', 'valid_to': '2026-10-19', 'time_zone_id': 'Asia/Ho_Chi_Minh',
            'room': None, 'note': 'Mang laptop', 'remind_before_minutes': None})

    def test_term_keeps_confirmed_range_not_a_single_occurrence(self):
        row = run(document(True), settings(True))['rows'][0]
        self.assertEqual(row['schedule_proposal']['valid_to'], '2027-01-01')

    def test_week_is_not_expanded_to_term_or_term_collapsed_to_week(self):
        for data, config in [(document(), settings(True)), (document(True), settings())]:
            self.assertIsNone(run(data, config)['rows'][0]['schedule_proposal'])

    def test_no_subject_or_human_check_prevents_proposal(self):
        for change in [{'subject_id': None}, {'verified_fields': False}]:
            config = settings(); config['rows'][0].update(change)
            self.assertIsNone(run(document(), config)['rows'][0]['schedule_proposal'])

    def test_unknown_or_odd_or_listed_weeks_stay_blocked(self):
        for kind in ['unknown', 'listed_weeks', 'odd_weeks', 'even_weeks']:
            data = document(True); data['lessons'][0]['week_rule']['kind'] = kind
            self.assertIsNone(run(data, settings(True))['rows'][0]['schedule_proposal'])

    def test_makeup_cannot_repeat_through_term(self):
        data = document(True); data['lessons'][0]['is_makeup'] = True
        self.assertIsNone(run(data, settings(True))['rows'][0]['schedule_proposal'])

    def test_missing_period_clock_requires_explicit_hours(self):
        data = document(); data['lessons'][0].update(time_basis='periods', start_time=None, end_time=None)
        self.assertIsNone(run(data, settings())['rows'][0]['schedule_proposal'])
        config = settings(); config['rows'][0].update(start_time='08:00', end_time='09:00')
        self.assertEqual(run(data, config)['rows'][0]['schedule_proposal']['start_minute'], 480)

    def test_human_can_correct_ai_weekday_with_date(self):
        data = document(); data['lessons'][0]['weekday'] = 'THU'
        config = settings(); config['rows'][0].update(weekday='WED', date='2026-10-21')
        self.assertEqual(run(data, config)['rows'][0]['schedule_proposal']['day_of_week'], 3)

    def test_draft_overlap_blocks_both_but_touching_times_do_not(self):
        data = document(True); data['lessons'].append(copy.deepcopy(data['lessons'][0]))
        config = settings(True); config['rows'].append({**config['rows'][0], 'index': 1, 'subject_id': 9})
        report = run(data, config)
        self.assertEqual([r['status'] for r in report['rows']], ['conflict', 'conflict'])
        data['lessons'][1].update(start_time='10:35', end_time='12:00')
        self.assertTrue(all(r['schedule_proposal'] for r in run(data, config)['rows']))

    def test_excluding_duplicate_allows_remaining_row(self):
        data = document(); data['lessons'].append(copy.deepcopy(data['lessons'][0]))
        config = settings(); config['rows'].append({'index': 1, 'include': False, 'verified_fields': False})
        report = run(data, config)
        self.assertEqual([r['status'] for r in report['rows']], ['ready_for_review', 'excluded'])

    def test_no_weekday_occurrence_in_term_range(self):
        config = settings(True); config.update(valid_from='2026-10-20', valid_to='2026-10-21')
        self.assertIsNone(run(document(True), config)['rows'][0]['schedule_proposal'])

    def test_invalid_date_and_reverse_clock_are_blocked(self):
        for change in [{'date': '2026-02-30'}, {'start_time': '12:00', 'end_time': '08:00'}]:
            data = document(); data['lessons'][0].update(change)
            self.assertIsNone(run(data, settings())['rows'][0]['schedule_proposal'])

    def test_unknown_field_or_ai_confirmations_are_not_accepted(self):
        data = document(); data['lessons'][0]['verified_fields'] = True
        with self.assertRaises(ValueError):
            ImportDocument.model_validate(data)

    def test_decision_index_must_be_unique_and_in_range(self):
        duplicate = settings()
        duplicate['rows'].append(copy.deepcopy(duplicate['rows'][0]))
        outside = settings()
        outside['rows'][0]['index'] = 99
        for config in [duplicate, outside]:
            with self.assertRaises(ValueError):
                run(document(), config)
