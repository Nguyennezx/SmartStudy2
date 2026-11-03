import base64
import json
import unittest
import tempfile
from pathlib import Path
from unittest.mock import patch

from server import decode_image, analyze, MAX_IMAGE
from import_draft import ImportDocument
from test_import_draft import document


class ServerTest(unittest.TestCase):
    def body(self, data):
        return json.dumps({'image_base64': base64.b64encode(data).decode()}).encode()

    def test_png_and_jpeg_transport(self):
        for data in (b'\x89PNG\r\n\x1a\nTEST', b'\xff\xd8\xffTEST'):
            self.assertEqual(decode_image(self.body(data)), data)

    def test_bad_payloads_never_reach_ai(self):
        for body in (b'{}', b'[]', b'null', b'{', b'{"image_base64":9}',
                     b'{"image_base64":"!!!"}', self.body(b'text')):
            with self.assertRaises(ValueError):
                decode_image(body)

    def test_limit_is_checked_after_decoding(self):
        with self.assertRaises(ValueError):
            decode_image(self.body(b'\xff\xd8\xff' + b'x' * MAX_IMAGE))

    def test_api_receives_image_and_schema_and_temp_image_is_removed(self):
        seen = []
        def fake(path, model, **kwargs):
            seen.append(path)
            self.assertEqual(path.read_bytes(), b'\xff\xd8\xffTEST')
            self.assertEqual(kwargs['schema_model'], ImportDocument)
            return ImportDocument.model_validate(document()), {'usage': 'not returned'}
        scratch = Path(__file__).resolve().parent / 'local_runs'
        scratch.mkdir(exist_ok=True)
        self.assertTrue(scratch.is_relative_to(Path(__file__).resolve().parent))
        temporary = tempfile.TemporaryDirectory
        with patch('server.call_gemini', side_effect=fake), patch('server.tempfile.TemporaryDirectory',
                side_effect=lambda **kwargs: temporary(dir=scratch, **kwargs)):
            result = analyze(b'\xff\xd8\xffTEST', 'explicit-test-model')
        self.assertEqual(result['schema_version'], 3)
        self.assertNotIn('metadata', result)
        self.assertFalse(seen[0].exists())
