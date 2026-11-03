import unittest

from pydantic import ValidationError

from layout_review import LayoutDocument, SchoolProfile, resolve
from test_prototype import sample


def grid():
    data = sample()
    data.update(table_kind='weekly_grid', class_text='Lớp A', date_evidence='full_year_visible',
                time_table_labels=[],
                period_rows=[{'period': i, 'top': 100+(i-1)*100, 'bottom': 100+i*100} for i in range(1, 6)],
                day_columns=[{'weekday': 'MON', 'date': '2026-10-19', 'left': 200, 'right': 400}])
    data['lessons'][0].update(period_start=1, period_end=5,
                             bounds={'left': 200, 'right': 400, 'top': 100, 'bottom': 600})
    return data


def profile(**updates):
    data = {'confirmed': True, 'school_label': 'Trường giả lập cho unit test',
            'selected_time_table': 'Bảng giờ do người dùng chọn', 'week_start': '2026-10-19',
            'periods': [{'period': 1, 'start': '07:00', 'end': '07:45'},
                        {'period': 2, 'start': '07:50', 'end': '08:35'},
                        {'period': 3, 'start': '08:40', 'end': '09:25'},
                        {'period': 4, 'start': '09:30', 'end': '10:15'},
                        {'period': 5, 'start': '10:20', 'end': '11:05'}]}
    data.update(updates)
    return SchoolProfile.model_validate(data)


class LayoutReviewTest(unittest.TestCase):
    def test_invalid_partial_column_date_blocks_even_after_user_anchors_week(self):
        data = grid()
        data.update(date_evidence='missing', observed_scope='undated_week', week_start=None)
        data['day_columns'][0]['date'] = '31/11'
        data['lessons'][0]['date'] = None
        row = self.row(data, profile())
        self.assertIsNone(row['schedule_preview'])
        self.assertTrue(any('không tồn tại' in issue for issue in row['issues']))

    def row(self, data, settings=None):
        return resolve(LayoutDocument.model_validate(data), settings)['review']['rows'][0]

    def test_merged_cell_uses_all_row_centers_not_text_position(self):
        data = grid()
        data['lessons'][0]['period_start'] = 3
        data['lessons'][0]['time_basis'] = 'periods'
        row = self.row(data)
        self.assertEqual(row['evidence']['grid_periods'], [1, 5])
        self.assertEqual(row['status'], 'needs_input')
        self.assertIsNone(row['schedule_preview'])

    def test_clear_explicit_times_keep_single_day_preview(self):
        row = self.row(grid())
        self.assertEqual(row['status'], 'ready_for_review')
        self.assertEqual(row['schedule_preview']['valid_from'], row['schedule_preview']['valid_to'])

    def test_ambiguous_time_cannot_become_ready_without_user_profile(self):
        data = grid()
        data['lessons'][0]['time_basis'] = 'ambiguous'
        self.assertIsNone(self.row(data)['schedule_preview'])

    def test_confirmed_period_table_resolves_unknown_hours(self):
        data = grid()
        data['lessons'][0].update(time_basis='periods', start_time=None, end_time=None)
        row = self.row(data, profile())
        self.assertEqual(row['status'], 'ready_for_review')
        self.assertEqual(row['schedule_preview']['end_minute'], 665)
        self.assertEqual(row['evidence']['time_source'], 'user_confirmed_profile')

    def test_two_time_tables_require_explicit_selection(self):
        data = grid()
        data['time_table_labels'] = ['4 tiết', '5 tiết']
        data['lessons'][0]['time_basis'] = 'ambiguous'
        self.assertIsNone(self.row(data, profile(selected_time_table=None))['schedule_preview'])

    def test_missing_period_in_profile_is_not_interpolated(self):
        data = grid()
        data['lessons'][0]['time_basis'] = 'periods'
        incomplete = profile().model_copy(update={'periods': profile().periods[:-1]})
        self.assertIsNone(self.row(data, incomplete)['schedule_preview'])

    def test_year_inference_requires_confirmation_and_uses_user_week(self):
        data = grid()
        data['date_evidence'] = 'inferred_year'
        self.assertIsNone(self.row(data)['schedule_preview'])
        row = self.row(data, profile(week_start='2027-01-04'))
        self.assertEqual(row['schedule_preview']['valid_from'], '2027-01-04')

    def test_weekday_mismatch_and_missing_box_are_blocked(self):
        data = grid()
        data['lessons'][0]['weekday'] = 'TUE'
        self.assertIsNone(self.row(data)['schedule_preview'])
        data = grid()
        data['lessons'][0]['bounds'] = None
        self.assertIsNone(self.row(data)['schedule_preview'])

    def test_bad_axes_or_missing_middle_row_are_blocked(self):
        for change in ['duplicate', 'gap', 'overlap']:
            data = grid()
            if change == 'duplicate':
                data['period_rows'][1]['period'] = 1
            elif change == 'gap':
                data['period_rows'].pop(2)
            else:
                data['period_rows'][1]['top'] = 150
            self.assertIsNone(self.row(data)['schedule_preview'])

    def test_row_list_can_resolve_periods_without_grid_coordinates(self):
        data = grid()
        data.update(table_kind='row_list', period_rows=[], day_columns=[])
        data['lessons'][0].update(bounds=None, time_basis='periods', start_time=None, end_time=None)
        self.assertEqual(self.row(data, profile())['status'], 'ready_for_review')

    def test_irregular_weeks_and_unknown_layout_remain_blocked(self):
        data = grid()
        data['lessons'][0]['recurrence_kind'] = 'irregular'
        self.assertIsNone(self.row(data, profile())['schedule_preview'])
        data = grid()
        data['table_kind'] = 'unknown'
        self.assertIsNone(self.row(data)['schedule_preview'])

    def test_unconfirmed_profile_and_overlapping_period_times_are_rejected(self):
        with self.assertRaises(ValidationError):
            profile(confirmed=False)
        with self.assertRaises(ValueError):
            resolve(LayoutDocument.model_validate(grid()), profile(periods=[
                {'period': 1, 'start': '07:00', 'end': '08:00'},
                {'period': 2, 'start': '07:30', 'end': '08:30'}]))

    def test_partial_column_date_matches_full_week_date(self):
        data = grid()
        data['day_columns'][0]['date'] = '19/10'
        self.assertEqual(self.row(data)['status'], 'ready_for_review')
        data['day_columns'][0]['date'] = '20/10'
        self.assertIsNone(self.row(data)['schedule_preview'])

    def test_a_cell_spanning_two_days_is_not_assigned_by_center_alone(self):
        data = grid()
        data['day_columns'].append({'weekday': 'TUE', 'date': '2026-10-20', 'left': 400, 'right': 600})
        data['lessons'][0]['bounds']['right'] = 450
        self.assertIsNone(self.row(data)['schedule_preview'])

    def test_explicit_clock_wins_over_uncertain_period_geometry(self):
        data = grid()
        data['lessons'][0].update(period_start=2, period_end=5)
        row = self.row(data)
        self.assertEqual(row['status'], 'ready_for_review')
        self.assertEqual(row['schedule_preview']['end_minute'], 635)
        self.assertTrue(row['evidence']['warnings'])

    def test_wrapped_subject_name_is_not_polluted_by_group_room_and_clock(self):
        data = grid()
        data['lessons'][0]['subject_text'] = 'Phát triển ứng dụng cho\ncác thiết bị di động\nNhóm: 01\nPhòng: A203\n07:00 → 10:35'
        self.assertEqual(self.row(data)['schedule_preview']['subject_text'],
                         'Phát triển ứng dụng cho các thiết bị di động')


if __name__ == '__main__':
    unittest.main()
