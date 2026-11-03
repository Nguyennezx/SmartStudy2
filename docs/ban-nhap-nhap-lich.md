# Quét ảnh TKB (AI) — cập nhật 03/11/2026

## Luồng hiện tại

Lịch học → dấu + → Quét ảnh TKB (AI) → Chụp ảnh hoặc Chọn ảnh có sẵn → danh sách buổi học → chạm từng buổi → form lịch hiện có → đối chiếu/sửa → lưu SQLite. Không tự lưu kết quả AI hoặc tự tạo hàng loạt lịch.

- Java/XML, ListView/BaseAdapter; Activity Result chọn tài liệu/chụp qua FileProvider; Volley/JSON gọi backend.
- Request giữ qua xoay màn hình bằng ViewModel; ảnh PNG/JPEG tối đa 8 MiB, không retry tự động.
- Cấu hình địa chỉ máy chủ và AI_IMPORT_TOKEN trong SharedPreferences. Không đưa Gemini API key vào APK.
- Backend online đã triển khai: https://smartstudy-timetable-ai.onrender.com. Người dùng xác nhận quét/lưu và dùng được khi đổi Wi-Fi, không cần bật server laptop.
- Đầu ra là ImportDocument v3, envelope extraction.lessons hoặc document thuần. Không tin status/proposal/subject ID do AI trả.
- Thiếu ngày/giờ/thứ thì để người dùng bổ sung; không tự chọn năm suy luận hoặc khoảng bốn tháng cho bản nhập AI.
- Ghép tên môn đầy đủ duy nhất trong danh sách active; nếu không có thì hướng dẫn tạo môn; tên gần giống/nhiều tên trùng phải chọn lại. Không tự khôi phục môn lưu trữ.
- Sau khi form trả RESULT_OK mới đánh dấu đã lưu. Hủy form không đánh dấu. DAO kiểm tra lại trùng và môn hợp lệ trước mỗi lần ghi.

## Giới hạn

Chưa có lưu hàng loạt; danh sách quét chỉ giữ trong phiên và Bundle, không lưu bền sau khi đóng màn hình. Tuần lẻ/chẵn/ngắt quãng và lịch bù theo kỳ chưa tự nhập. Tạo môn qua màn hình quản lý là thao tác lưu riêng, chưa cùng transaction với lịch mới. AI có thể đọc sai; cần đối chiếu ảnh trước khi lưu.

## Áp dụng bài học và kiểm tra

C1–C3: XML/findViewById/listener; C2: Intent/Bundle/Activity Result; C3/C6: model/BaseAdapter/ListView, form → DAO → Helper chung; C7: Volley/JSON. ViewModel/FileProvider và backend Python là phần bổ sung để hoàn thiện luồng.

45 unit tests Java (gồm bài mẫu project), 55 Python tests đạt. Build APK Gradle thành công ngày 03/11. Parser androidTest đã biên dịch nhưng chưa chạy trong đợt này. Người dùng đã thử quét online; không khẳng định mọi dạng TKB đều nhận diện đúng.

Chi tiết triển khai: tools/ai_timetable/deploy_render.md. Chi tiết Home: docs/nguoi2-hom-nay.md.

## Phối hợp nhóm

Các thay đổi cần thông báo khi ghép: dependency Volley, quyền INTERNET, provider/Activity chụp và quét trong Manifest, form lịch nhận Bundle gợi ý và tạo môn trả ID. Giữ Helper/schema/bảng màu và khung điều hướng chung.
