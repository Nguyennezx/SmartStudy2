"""Local authenticated demo backend. Gemini key stays on this machine."""
import argparse
import base64
import binascii
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import tempfile
import threading

from import_draft import ImportDocument, IMPORT_PROMPT
from prototype import call_gemini

MAX_BODY = 12 * 1024 * 1024
MAX_IMAGE = 8 * 1024 * 1024
gate = threading.Lock()


def decode_image(body: bytes) -> bytes:
    if len(body) > MAX_BODY:
        raise ValueError('Ảnh quá lớn.')
    try:
        data = json.loads(body)
        encoded = data['image_base64']
        if not isinstance(encoded, str):
            raise ValueError('Ảnh không hợp lệ.')
        image = base64.b64decode(encoded, validate=True)
    except (ValueError, KeyError, TypeError, binascii.Error):
        raise ValueError('Dữ liệu ảnh không hợp lệ.') from None
    if not image or len(image) > MAX_IMAGE:
        raise ValueError('Ảnh tối đa 8 MiB.')
    if not image.startswith((b'\x89PNG\r\n\x1a\n', b'\xff\xd8\xff')):
        raise ValueError('Chọn ảnh PNG hoặc JPEG.')
    return image


def analyze(image: bytes, model: str) -> dict:
    # No personal images, raw model responses or API keys are logged/persisted.
    with tempfile.TemporaryDirectory(prefix='smartstudy-import-') as folder:
        path = Path(folder) / 'upload.img'
        path.write_bytes(image)
        doc, _ = call_gemini(path, model, schema_model=ImportDocument, prompt=IMPORT_PROMPT)
    result = {'schema_version': 3, 'extraction': doc.model_dump()}
    if len(doc.lessons) > 100 or len(json.dumps(result, ensure_ascii=False)) > 200000:
        raise ValueError('Kết quả quá lớn, chọn ảnh với ít buổi hơn.')
    return result


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass  # Do not log URLs, authentication or uploaded timetable data.

    def reply(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_POST(self):
        if self.path != '/v1/timetable/extract':
            self.reply(404, {'error': 'Không có đường dẫn này.'})
            return
        token = os.environ.get('AI_IMPORT_TOKEN', '')
        if not token or not hmac.compare_digest(self.headers.get('Authorization', '').encode(), ('Bearer ' + token).encode()):
            self.reply(401, {'error': 'Thông tin kết nối chưa đúng.'})
            return
        if self.headers.get('Content-Type', '').split(';')[0].strip() != 'application/json':
            self.reply(415, {'error': 'Sai kiểu dữ liệu yêu cầu.'})
            return
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= MAX_BODY:
                self.reply(413, {'error': 'Ảnh quá lớn hoặc yêu cầu rỗng.'})
                return
            self.connection.settimeout(20)
            image = decode_image(self.rfile.read(length))
        except (ValueError, OSError):
            self.reply(400, {'error': 'Không đọc được ảnh. Chọn PNG/JPEG tối đa 8 MiB.'})
            return
        if not gate.acquire(blocking=False):
            self.reply(429, {'error': 'Đang phân tích ảnh khác. Vui lòng thử lại sau.'})
            return
        try:
            result = analyze(image, self.server.model)
            self.reply(200, result)
        except Exception as error:
            code = getattr(error, 'code', None)
            self.reply(429 if code == 429 else 502, {
                'error': 'Dịch vụ AI hết hạn mức hoặc đang bận.' if code == 429
                else 'Chưa phân tích được ảnh. Bạn có thể chọn ảnh khác hoặc thử lại.'})
        finally:
            gate.release()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8765)
    parser.add_argument('--model', required=True)
    args = parser.parse_args()
    if not os.environ.get('GEMINI_API_KEY') or len(os.environ.get('AI_IMPORT_TOKEN', '')) < 24:
        raise SystemExit('Cần GEMINI_API_KEY và AI_IMPORT_TOKEN ít nhất 24 ký tự trong môi trường máy chủ.')
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    server.daemon_threads = True
    server.model = args.model
    print(f'SmartStudy demo backend listening on {args.host}:{args.port}; no database writes.')
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
