# Nhắc giờ học — người 2

Đã nối tùy chọn nhắc trước với AlarmManager và notification channel riêng `schedule_lessons`. Chạm thông báo mở form của lịch. Không đổi DatabaseHelper, DatabaseSchema, MainActivity, màu hay khung điều hướng chung. Manifest thêm quyền thông báo, nhắc đúng giờ, nhận khởi động máy và hai receiver của phần lịch.

## Cách hoạt động

- ReminderPlanner tính buổi cần nhắc tiếp theo theo ngày hiệu lực, thứ và múi giờ của lịch. Đọc ngoại lệ hủy/dời buổi có sẵn, kể cả buổi dời ra ngoài khoảng gốc. Chưa có giao diện tạo ngoại lệ.
- Mỗi chuỗi chỉ đặt một alarm sắp tới; sau khi nhận sẽ tính buổi tiếp. Không chạy vòng lặp đếm giờ hay service thường trực.
- Receiver đọc lại SQLite trước khi gửi để loại alarm cũ sau sửa/xóa. Một hàng đợi ExecutorService đồng bộ alarm và sổ đã nhắc.
- SharedPreferences giữ khóa buổi đã gửi để hạn chế lặp, tối đa 256 khóa mỗi chuỗi. SQLite vẫn là nguồn dữ liệu lịch chính. Nếu tiến trình chết giữa gửi thông báo và ghi sổ, không bảo đảm chỉ báo đúng một lần; tag thông báo ổn định giúp thay thế cùng buổi.
- Sửa/xóa lịch hủy alarm và thông báo cũ; lưu trữ/khôi phục hoặc sửa môn đồng bộ lại. Khi khởi động máy, cập nhật app, đổi giờ/múi giờ hệ thống hoặc cấp quyền exact alarm, receiver khôi phục từ database. Mở Hôm nay hoặc Lịch học cũng đồng bộ.
- Chỉ nhắc bù nếu chưa quá 10 phút sau giờ bắt đầu và buổi chưa kết thúc; qua giới hạn này bỏ thông báo quá hạn, chuyển buổi tiếp theo. Nếu đổi cấu hình nhắc sau khi đã nhắc, cấu hình mới có khóa khác và có thể nhắc lại buổi đó.
- Tôn trọng `app_settings.reminders_enabled`. Khi phần cài đặt chung được triển khai, người phụ trách gọi `ReminderCoordinator.refresh(context)` sau khi ghi cờ này; phần người 2 chưa thay màn hình cài đặt chung.

## Quyền Android

Android 13 trở lên cần quyền thông báo. Android 12 trở lên kiểm tra quyền nhắc đúng giờ; thiếu quyền thì dùng alarm không chính xác, có thể trễ. Tab Lịch học hiện nút hướng dẫn khi có buổi cần nhắc. Tắt cả thông báo app hoặc riêng kênh Nhắc giờ học cũng được phát hiện.

Android có thể hủy alarm khi thu hồi quyền nhắc đúng giờ; mở lại Hôm nay/Lịch học để đồng bộ phương án không chính xác. Force Stop ngăn app nhận nhắc cho đến khi mở lại. Chế độ tiết kiệm pin riêng của hãng cũng cần thử trên máy thực tế. Tài liệu nền tảng: [AlarmManager](https://developer.android.com/develop/background-work/services/alarms), [quyền thông báo](https://developer.android.com/develop/ui/views/notifications/notification-permission).

## Liên hệ kiến thức môn học

Java/XML, findViewById và listener giữ như các phần trước; Intent/receiver nhận sự kiện hệ thống, SharedPreferences giữ trạng thái nhỏ, model/DAO đọc SQLite chung. AlarmManager, notification channel và API xin quyền là phần bổ sung cần thiết để hiện thực yêu cầu nhắc lịch; không khẳng định các API bổ sung này đều nằm trong chương 1–6.

## Thử trên điện thoại

1. Stop rồi Run lại app vì Manifest thay đổi, không dùng Apply Changes.
2. Tạo lịch hôm nay bắt đầu sau khoảng 3 phút, chọn nhắc Đúng giờ. Vào tab Lịch học, cấp quyền thông báo và nhắc đúng giờ qua các nút hướng dẫn nếu xuất hiện.
3. Đưa app về nền, không Force Stop. Đến giờ kiểm tra tên môn, phòng, giờ và chạm thông báo để mở đúng lịch.
4. Thử một lịch nhắc trước 5 phút. Sửa giờ, xóa lịch hoặc lưu trữ môn trước lúc nhắc: alarm theo cấu hình cũ không được báo. Khôi phục môn còn buổi tương lai sẽ đặt lại nhắc.
5. Chọn Không nhắc hoặc từ chối quyền: không crash; tab giải thích quyền còn thiếu nếu có buổi cần nhắc. Cấp lại rồi quay về tab để đồng bộ.
6. Khởi động lại điện thoại, mở khóa và kiểm tra buổi tương lai. Thử đổi giờ hệ thống và xác nhận không nhắc lặp buổi đã gửi.
7. Nếu chưa cấp quyền nhắc đúng giờ, thời điểm nhận có thể trễ; thông báo quá hạn sẽ bị bỏ theo giới hạn ở trên.

## Kiểm tra tại môi trường phát triển

28 bài JVM qua: 19 bài semester/rules/calendar và 9 bài ReminderPlanner về múi giờ, nhắc trước/không nhắc, chống lặp sau lùi đồng hồ, hủy/dời buổi, hết hạn và thay cấu hình. AAPT2 compile XML và javac kiểm tra kiểu toàn bộ main/test/androidTest với thư viện cache cùng R tạm đã qua; tham chiếu R được đối chiếu tài nguyên.

Đây chưa phải build APK/link tài nguyên đầy đủ. Gradle/ADB của môi trường gặp lỗi loopback đã ghi ở phần lịch; chưa chạy kiểm thử thiết bị hoặc xác nhận thông báo thực tế. Cần Run trong Android Studio và thử các bước trên.
