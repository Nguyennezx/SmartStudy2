"""WSGI entry point for the online demo. Serve with Gunicorn, not http.server."""
from collections import deque
import hmac
import json
import os
import threading
import time

from server import MAX_BODY, analyze, decode_image

gate = threading.Lock()
attempts = deque()


def configured():
    return bool(os.environ.get('GEMINI_API_KEY') and os.environ.get('GEMINI_MODEL')
                and len(os.environ.get('AI_IMPORT_TOKEN', '')) >= 24)


def app(environ, start_response):
    def reply(code, payload):
        labels = {200: 'OK', 400: 'Bad Request', 401: 'Unauthorized', 404: 'Not Found',
                  405: 'Method Not Allowed', 413: 'Content Too Large', 415: 'Unsupported Media Type',
                  429: 'Too Many Requests', 502: 'Bad Gateway', 503: 'Service Unavailable'}
        body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        start_response(f'{code} {labels[code]}', [('Content-Type', 'application/json; charset=utf-8'),
                       ('Content-Length', str(len(body))), ('Cache-Control', 'no-store')])
        return [body]

    if environ.get('PATH_INFO') == '/health' and environ.get('REQUEST_METHOD') == 'GET':
        return reply(200 if configured() else 503, {'status': 'ok' if configured() else 'not_configured'})
    if environ.get('PATH_INFO') != '/v1/timetable/extract':
        return reply(404, {'error': 'Không có đường dẫn này.'})
    if environ.get('REQUEST_METHOD') != 'POST':
        return reply(405, {'error': 'Yêu cầu POST.'})
    if not configured():
        return reply(503, {'error': 'Máy chủ chưa cấu hình xong.'})
    supplied = environ.get('HTTP_AUTHORIZATION', '').encode()
    expected = ('Bearer ' + os.environ['AI_IMPORT_TOKEN']).encode()
    if not hmac.compare_digest(supplied, expected):
        return reply(401, {'error': 'Thông tin kết nối chưa đúng.'})
    if environ.get('CONTENT_TYPE', '').split(';')[0].strip() != 'application/json':
        return reply(415, {'error': 'Sai kiểu dữ liệu.'})
    try:
        length = int(environ.get('CONTENT_LENGTH', '0'))
    except ValueError:
        return reply(400, {'error': 'Độ dài yêu cầu không hợp lệ.'})
    if not 0 < length <= MAX_BODY:
        return reply(413, {'error': 'Ảnh quá lớn hoặc yêu cầu rỗng.'})
    if not gate.acquire(blocking=False):
        return reply(429, {'error': 'Đang phân tích ảnh khác. Vui lòng thử lại sau.'})
    try:
        now = time.monotonic()
        while attempts and attempts[0] <= now - 3600:
            attempts.popleft()
        limit = int(os.environ.get('AI_IMPORT_HOURLY_LIMIT', '20'))
        if limit < 1 or len(attempts) >= limit:
            return reply(429, {'error': 'Đã đạt giới hạn lượt thử trong giờ. Vui lòng thử lại sau.'})
        try:
            body = environ['wsgi.input'].read(length)
            if len(body) != length:
                raise ValueError()
            image = decode_image(body)
        except (ValueError, OSError):
            return reply(400, {'error': 'Chọn ảnh PNG/JPEG tối đa 8 MiB.'})
        attempts.append(now)
        try:
            return reply(200, analyze(image, os.environ['GEMINI_MODEL']))
        except Exception as error:
            code = getattr(error, 'code', None)
            return reply(429 if code == 429 else 502, {'error': 'Dịch vụ AI đang bận hoặc chưa đọc được ảnh.'})
    finally:
        gate.release()
