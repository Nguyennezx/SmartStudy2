# Render Free — chuẩn bị backend online (01/11/2026)

Trạng thái: đã chuẩn bị source/cấu hình và kiểm thử cục bộ; CHƯA tạo dịch vụ Render, chưa có URL HTTPS, chưa kiểm tra từ điện thoại qua Internet. Không mặc định đã triển khai chỉ vì có render.yaml.

## Cấu hình đã chuẩn bị

- File root `render.yaml`: Python web service, plan free, branch hai, rootDir tools/ai_timetable, auto deploy off; không tạo database hoặc paid disk.
- Build: `pip install -r requirements-cloud.txt`.
- Run: `gunicorn cloud_app:app --bind 0.0.0.0:$PORT --workers 1 --threads 2 --timeout 120`.
- `.python-version` chọn Python 3.12; Gunicorn 26.2.0 dành cho Unix/cloud, không chạy Gunicorn trên Windows. Backend local server.py/start_server.ps1 vẫn dùng để test trước.
- `/health`: chỉ kiểm tra process/config; không gọi Gemini, không tiết lộ key/token, không kiểm tra quota/model của tài khoản.
- API `/v1/timetable/extract` giữ JSON giống Android đang dùng. Auth Bearer, giới hạn ảnh 8 MiB, body 12 MiB, một request AI đồng thời; 20 lượt gọi AI/giờ cho toàn service demo.
- Giới hạn đếm trong bộ nhớ một worker, mất khi restart, không phải hạn mức chi tiêu bền vững hoặc hệ thống đăng nhập nhiều người dùng. Không bật billing Gemini trong bước này.
- Không lưu ảnh gốc hoặc dữ liệu SQLite lên Render. Key chỉ nhập vào biến môi trường của dashboard.

## Thao tác khi có tài khoản

1. Tạo tài khoản Render và đăng nhập bằng GitHub. Không cần tạo dịch vụ trả phí.
2. Cần đưa source backend đã kiểm tra lên nhánh `hai` trước để Render lấy đúng mã. UI Android hiện có nhiều thay đổi cục bộ; không tự gộp chúng vào một commit deploy. Không upload `.venv`, ảnh, local_runs, `.env`, connection.local.json hoặc Gemini key.
3. Dashboard → New → Blueprint → chọn repository SmartStudy2, branch hai và render.yaml. Xác nhận chỉ có một web service plan Free trước khi tạo. Nếu tài khoản không có quyền đọc repository của nhóm, cần cấp quyền GitHub phù hợp hoặc bàn lại repository triển khai.
4. Nhập GEMINI_API_KEY trực tiếp ở Render. Blueprint tạo AI_IMPORT_TOKEN ngẫu nhiên mới; token laptop cũ không tự đồng bộ. GEMINI_MODEL và hourly limit đã khai báo, có thể chỉnh tại dashboard.
5. Deploy và đợi Live. Kiểm tra `https://<tên-service>.onrender.com/health` trả `{"status":"ok"}`.
6. Trong app → dấu + → Quét ảnh TKB (AI) → biểu tượng cài đặt: dùng HTTPS URL được Render cấp (không tự đoán URL) và AI_IMPORT_TOKEN của Render. Không nhập Gemini API key vào app/chat/Git.
7. Tắt backend laptop, dùng 4G/Wi-Fi khác, thử ảnh → danh sách → form → lưu → Lịch học để xác minh tính năng dùng độc lập laptop.

## Phần đã kiểm tra

55 Python tests đạt gồm WSGI validator, health, xác thực, body lỗi, giới hạn lượt, request đang bận và lỗi AI không trả bí mật. Chưa test Gunicorn trong Linux hoặc deploy Render thật. Chưa có tài khoản Render được kết nối trong phiên này.

Render Free ngủ sau 15 phút không có traffic và có thể mất khoảng một phút để khởi động. App đã tăng request timeout lên 180 giây, vẫn không retry tự động để tránh gọi AI lặp. Hạn mức và điều kiện miễn phí do Render/Gemini quản lý; kiểm tra dashboard trước khi đồng ý thay gói.

Nguồn đối chiếu:
- https://render.com/docs/free
- https://render.com/docs/blueprint-spec
- https://render.com/docs/python-version
- https://pypi.org/project/gunicorn/26.2.0/
