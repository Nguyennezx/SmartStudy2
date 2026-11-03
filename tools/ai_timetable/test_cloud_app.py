import base64
import io
import json
import os
import unittest
from unittest.mock import patch
from wsgiref.validate import validator
import cloud_app


class CloudAppTest(unittest.TestCase):
    def setUp(self):
        self.environment = patch.dict(os.environ, {'GEMINI_API_KEY': 'fake', 'AI_IMPORT_TOKEN': 'x' * 32,
                                                  'GEMINI_MODEL': 'test', 'AI_IMPORT_HOURLY_LIMIT': '20'})
        self.environment.start()
        self.addCleanup(self.environment.stop)
        cloud_app.attempts.clear()

    def request(self, path='/v1/timetable/extract', method='POST', auth=True, body=None):
        if body is None:
            body = json.dumps({'image_base64': base64.b64encode(b'\xff\xd8\xfftest').decode()}).encode()
        state = {}
        env = {'REQUEST_METHOD': method, 'PATH_INFO': path, 'SCRIPT_NAME': '', 'QUERY_STRING': '',
               'SERVER_NAME': 'localhost', 'SERVER_PORT': '80', 'SERVER_PROTOCOL': 'HTTP/1.1',
               'CONTENT_TYPE': 'application/json', 'CONTENT_LENGTH': str(len(body)),
               'HTTP_AUTHORIZATION': 'Bearer ' + 'x' * 32 if auth else '',
               'wsgi.version': (1, 0), 'wsgi.url_scheme': 'http', 'wsgi.input': io.BytesIO(body),
               'wsgi.errors': io.StringIO(), 'wsgi.multithread': True, 'wsgi.multiprocess': False, 'wsgi.run_once': False}
        def start(status, headers, exc_info=None):
            state['status'] = int(status.split()[0])
        result = validator(cloud_app.app)(env, start)
        try:
            payload = json.loads(b''.join(result))
        finally:
            result.close()
        return state['status'], payload

    def test_health_does_not_call_ai_or_return_secrets(self):
        with patch('cloud_app.analyze') as ai:
            self.assertEqual(self.request('/health', 'GET'), (200, {'status': 'ok'}))
            ai.assert_not_called()
        with patch.dict(os.environ, {'GEMINI_API_KEY': ''}):
            self.assertEqual(self.request('/health', 'GET')[0], 503)

    def test_auth_payload_and_route_fail_before_ai(self):
        with patch('cloud_app.analyze') as ai:
            self.assertEqual(self.request(auth=False)[0], 401)
            self.assertEqual(self.request(body=b'{}')[0], 400)
            self.assertEqual(self.request(body=b'')[0], 413)
            self.assertEqual(self.request('/missing')[0], 404)
            self.assertEqual(self.request(method='GET')[0], 405)
            ai.assert_not_called()

    def test_success_and_hourly_limit(self):
        with patch.dict(os.environ, {'AI_IMPORT_HOURLY_LIMIT': '1'}), patch('cloud_app.analyze', return_value={'schema_version': 3}) as ai:
            self.assertEqual(self.request(), (200, {'schema_version': 3}))
            self.assertEqual(self.request()[0], 429)
            self.assertEqual(ai.call_count, 1)

    def test_busy_and_upstream_errors(self):
        cloud_app.gate.acquire()
        try:
            self.assertEqual(self.request()[0], 429)
        finally:
            cloud_app.gate.release()
        with patch('cloud_app.analyze', side_effect=RuntimeError('secret-key-not-to-return')):
            code, payload = self.request()
            self.assertEqual(code, 502)
            self.assertNotIn('secret-key', str(payload))
