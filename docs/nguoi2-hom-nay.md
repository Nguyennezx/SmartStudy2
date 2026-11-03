# Trang Hôm nay — 03/11/2026

## Hành vi đã triển khai

- Ngày hiện tại lấy theo `app_settings.time_zone_id`, không mặc định theo múi giờ điện thoại.
- Dùng `ScheduleCalendar` có sẵn để đọc các buổi thực tế hôm nay, kể cả ngoại lệ hủy/dời đã có trong DB. Thẻ đầu là buổi đang học hoặc buổi sắp bắt đầu; buổi đã kết thúc bị bỏ. Các buổi tiếp theo nằm ở danh sách còn lại, không lặp thẻ đầu.
- Chạm thẻ hoặc hàng lịch: xem ngày, giờ, múi giờ, phòng, giảng viên, ghi chú; nút Sửa lịch lặp mở form hiện có. Chỉ có một đích xem chi tiết, không có nút “Chuẩn bị…” riêng.
- “Xem lịch”: gửi Fragment Result gồm ngày hôm nay, chuyển tab bằng BottomNavigationView hiện có; ScheduleFragment chuyển đúng chế độ Ngày, kể cả đang giữ tuần cũ. Không sửa MainActivity hoặc menu chung.
- Bài tập: tối đa 3 bài chưa hoàn thành, thuộc môn active, có hạn trước đầu ngày thứ 7 tính từ hôm nay; bao gồm bài quá hạn và sắp xếp hạn tăng dần. Không coi bài không hạn là quá hạn. Chạm để xem mô tả/hạn; Home không sửa trạng thái bài tập.
- Thời gian tập trung: SUM(focus_seconds) đã lưu của phiên có started_at_ms thuộc ngày hiện tại. Bao gồm lịch sử môn lưu trữ; không cộng đồng hồ đang chạy và không tự cộng thời gian nghỉ. Phiên qua đêm tính vào ngày bắt đầu theo phân công.
- “Bắt đầu học” chuyển tab Học tập; Home không tự tạo phiên hay viết bộ đếm.
- Đọc SQLite nền qua ExecutorService; ViewModel giữ tác vụ/dữ liệu qua xoay màn hình; tải lại khi quay về và mỗi phút khi đang mở Home. Có trạng thái tải, lỗi/chạm thử lại, hết lịch, chưa có lịch và không có bài gần hạn.

## Áp dụng kiến thức thầy

- C1/C3: XML, LinearLayout/ScrollView/TextView, findViewById, listener, inflate item riêng. Danh sách bài tập nhỏ giới hạn 3 dòng nên dùng LinearLayout, không lồng ListView vào ScrollView.
- C2: Intent mở form sửa lịch hiện có; không tạo form lịch thứ hai.
- C4: HomeFragment và điều hướng tab có sẵn. Fragment Result là API bổ sung để truyền ngày giữa hai Fragment mà không sửa khung chung.
- C6: HomeSnapshot/Task là dữ liệu, HomeDAO giữ SQL/Cursor/truy vấn có tham số; dùng Helper và bảng chung. Không sửa schema/version, không ghi dữ liệu mẫu.
- ViewModel/LiveData và ExecutorService là phần hỗ trợ lifecycle/luồng đã dùng trong phần lịch.

## Phối hợp người 3

Home hiện đọc trực tiếp các cột đã thống nhất trong `assignments` và `study_sessions` ở DAO chỉ đọc. Người 3 tiếp tục sở hữu thao tác ghi và màn hình quản lý các bảng này. Khi ghép code có thể chuyển HomeDAO sang gọi DAO đọc do người 3 cung cấp.

Ở checkout hiện tại, StudyFragment vẫn là khung và chưa có danh sách bài tập riêng. Vì vậy chưa đặt nút “Tất cả bài tập” dẫn tới màn hình không tồn tại; các hàng chỉ mở chi tiết đọc. “Bắt đầu học” chuyển tới tab Học tập hiện có, chưa đồng nghĩa tạo được phiên học. Cần báo người 3 khi ghép hai phần này; không tự triển khai thay phạm vi của bạn đó.

## Kiểm tra

- 44 bài JVM đạt: 35 bài lịch/nhắc/học kỳ cũ, 5 bài ghép môn và 4 bài TodayAgenda mới (buổi đang học, nối giờ, hết lịch, qua nửa đêm, hủy/dời và không lặp thẻ).
- HomeDAO: chạy đúng chuỗi SQL trong source trên SQLite với schema trích từ DatabaseSchema; kiểm tra bài đã hoàn thành/môn lưu trữ/hạn NULL/biên 7 ngày và biên ngày của tổng focus_seconds.
- AAPT2 link với thư viện cache và javac main/test/androidTest đạt.
- `:app:assembleDebug --offline` build APK thành công bằng Gradle sau khi được phép truy cập cache. Chưa chạy/thao tác giao diện trên điện thoại trong đợt này.
- `:app:testDebugUnitTest --offline`: 45 tests, 0 failures/errors/skipped (44 bài chức năng nêu trên và 1 bài mẫu của project).

## Thử trên điện thoại

1. Run app, mở Home khi chưa có lịch: trạng thái trống, thời gian 0 phút; không hiện dữ liệu minh họa trong ảnh thiết kế.
2. Thêm lịch hôm nay bắt đầu sau vài phút và một lịch sau đó. Home hiện đúng thẻ đầu và một hàng còn lại, không lặp. Đến giờ bắt đầu/kết thúc, đợi tối đa một phút để kiểm tra thay trạng thái.
3. Chạm thẻ xem đúng giảng viên/phòng/ghi chú; sửa giờ rồi quay về, dữ liệu cập nhật.
4. Mở Lịch học chọn tuần khác và chế độ Tuần; về Home → Xem lịch: phải trở về hôm nay, chế độ Ngày.
5. Xoay màn hình, đổi tab rồi quay lại: không crash, không nhân đôi các hàng.
6. Khi ghép người 3: bài todo/in_progress có hạn hiện, done/không hạn/môn lưu trữ không hiện; kiểm tra tổng thời gian theo ngày bắt đầu phiên.
