# Thêm và quản lý lịch học — người 2

## Sử dụng

Mở tab Lịch học. Nút + mở form thêm lịch, điền sẵn thứ và ngày bắt đầu theo ngày đang chọn. Menu ⋮ mở Quản lý môn học và học kỳ hoặc Tất cả lịch đã tạo. Trong form, chọn môn, thứ, giờ, khoảng ngày, phòng và ghi chú. Môn cùng tên được phân biệt thêm bằng tên học kỳ.

Mỗi bản ghi là một buổi lặp hàng tuần, bao gồm ngày đầu/cuối. Một môn học nhiều thứ thì thêm nhiều bản ghi, dùng lại cùng subject_id. Lịch Ngày/Tuần chỉ hiển thị các buổi thực tế thuộc khoảng ngày áp dụng. Menu Tất cả lịch đã tạo vẫn cho truy cập các chuỗi cũ/tương lai. Môn lưu trữ làm lịch liên quan ẩn đi; khôi phục môn thì lịch xuất hiện trở lại.

Chọn Ngày để xem trục giờ của ngày được tô xanh. Chọn Tuần để xem 7 cột; cuộn ngang trên điện thoại. Mũi tên chuyển tuần giữ cùng thứ đang chọn; chạm nhãn khoảng tuần để nhảy đến một ngày bằng DatePicker. Nút Hôm nay quay lại ngày hiện tại theo múi giờ app. Dấu chấm dưới ngày đánh dấu hôm nay; đường hồng trên trục giờ là giờ hiện tại, cập nhật mỗi phút khi tab đang hoạt động.

Thẻ có nền pastel và vạch màu lấy từ màu môn đã lưu; chạm thẻ để xem đầy đủ giờ/phòng/ghi chú. Nút Sửa lịch lặp và Xóa lịch lặp áp dụng cho cả chuỗi, có nhãn và xác nhận rõ ràng. Các thẻ ngắn/chồng giờ được chia làn; giờ ghi trên thẻ vẫn là giờ thật, chiều cao tối thiểu chỉ giúp đọc/bấm.

Chọn 00:00 ở giờ kết thúc nghĩa là 24:00 cuối ngày. Buổi qua đêm phải tách thành hai lịch. Giờ học được hiểu theo múi giờ hiển thị trong form, giữ múi giờ cũ khi sửa. Lịch mới lấy múi giờ từ app_settings.

Tùy chọn nhắc trước lưu vào schedules.remind_before_minutes: NULL không nhắc, 0 đúng giờ, giá trị dương nhắc trước. Đã nối AlarmManager và thông báo Android; tự đồng bộ khi lịch/môn thay đổi. Xem [Nhắc giờ học](nguoi2-nhaclich.md) để cấp quyền và thử trên máy.

## Áp dụng bài học

- C1/C3: giao diện XML, findViewById, Spinner, EditText, Button và listener. Chọn ngày/giờ bằng DatePickerDialog/TimePickerDialog. Mỗi thuộc tính XML một dòng.
- C2: Intent mở ScheduleEditActivity; Bundle giữ nội dung đang nhập khi Activity được tạo lại.
- C3: ScheduleAdapter extends BaseAdapter, item_schedule.xml riêng, tái sử dụng dòng và notifyDataSetChanged. ListView có header nên vị trí dòng được trừ số header trước khi lấy model.
- C6: Schedule model; ScheduleDAO dùng Helper chung, ContentValues, Cursor, tham số truy vấn và transaction. Không đổi schema, version hay tên file database.
- ScheduleRules tách quy tắc lịch khỏi Android để kiểm thử trực tiếp bằng Java.
- ScheduleCalendar tính buổi thực tế theo tuần, ngoại lệ và múi giờ. ScheduleOccurrence biểu diễn buổi hiển thị; CalendarCardLayout chia làn thẻ. ScheduleTimelineView là FrameLayout đặt các thẻ XML theo giờ; không dùng Compose hay thay khung điều hướng chung.
- ScheduleEditViewModel dùng thành phần lifecycle đã có trong project để giữ tác vụ ghi khi xoay màn hình, tránh bấm Lưu lặp. Database chạy trên ExecutorService; giao diện cập nhật ở luồng chính.

## Kiểm tra dữ liệu

Không nhận giờ kết thúc trước/bằng bắt đầu, ngày không có thật, khoảng ngày đảo ngược hoặc khoảng không có lần xuất hiện của thứ đã chọn. DAO kiểm tra lại môn còn đang dùng khi ghi, kể cả khi form đã mở trước lúc môn bị lưu trữ.

Kiểm tra trùng trên lần xuất hiện thật trong khoảng ngày. Hai buổi nối tiếp được phép. Các lịch khác múi giờ được đổi sang Instant theo ngày tương ứng; kiểm tra trùng và ghi lịch nằm trong cùng transaction để tránh hai lần lưu đồng thời cùng vượt qua kiểm tra.

Nếu database đã có ngoại lệ đổi/hủy, kiểm tra trùng bỏ buổi gốc và tính buổi học bù. Giao diện tạo ngoại lệ chưa làm. Chưa cho sửa cả chuỗi đã có ngoại lệ, tránh làm mất hiệu lực các ngoại lệ cũ. Xóa chuỗi sẽ xóa ngoại lệ con qua khóa ngoại CASCADE, giữ nguyên môn học.

## Thử trên Android Studio hoặc điện thoại

1. Dừng app rồi Run lại vì Manifest có khai báo Activity mới. Vào Lịch học, tạo môn Java nếu cần.
2. Thêm thứ Hai 08:30–10:30, từ 19/10/2026 đến 31/12/2026, phòng A203. Danh sách phải hiện tên, màu, thứ/giờ, phòng và khoảng ngày đúng.
3. Đóng mở app; sửa lịch thành 09:00–11:00, ghi chú Mang laptop. Mở lại form xác nhận dữ liệu còn nguyên.
4. Thêm buổi thứ Hai 10:00–11:30 cùng khoảng ngày: phải báo rõ trùng môn, ngày và giờ; giữ nội dung form để sửa. Đổi thành 11:00–12:00 sau bước 3: phải lưu được.
5. Chọn giờ kết thúc trước bắt đầu hoặc khoảng ngày đảo ngược: báo lỗi. Chọn thứ Hai nhưng khoảng 20–25/10/2026 (không có thứ Hai): cũng báo lỗi.
6. Xoay màn hình khi đang nhập và khi đang lưu: giữ nội dung, không tạo hai dòng do thao tác Lưu lặp. Quay về từ Quản lý môn học phải tải lại danh sách môn.
7. Hủy hộp xác nhận xóa: lịch vẫn còn. Xác nhận xóa: lịch biến mất, môn và các lịch khác vẫn còn.
8. Chưa có môn hoặc tất cả môn đã lưu trữ: form hướng dẫn vào Quản lý môn học, không lưu được lịch với môn không hợp lệ.
9. Chọn đúng ngày có buổi (ví dụ thứ Hai 19/10/2026) để thấy thẻ. Chọn ngày không có buổi: hiện trạng thái trống. Chuyển Tuần, cuộn ngang đến Chủ nhật; đổi tuần qua tháng/năm và dùng Hôm nay.
10. Xoay màn hình/chuyển tab: giữ ngày và chế độ Ngày/Tuần. Thử phông chữ hệ thống lớn, thẻ ngắn và nhiều buổi liền nhau. Vẫn mở được lịch quá khứ/tương lai qua menu Tất cả lịch đã tạo.

## Kiểm thử đã thực hiện

AAPT2 compile tài nguyên và đối chiếu tham chiếu R thành công. javac kiểm tra kiểu toàn bộ Java main/test/androidTest thành công với thư viện cache và R tạm. JUnitCore chạy 8 bài ScheduleRulesTest và 2 bài SemesterTest, tổng 10 bài đều qua: trùng/nối tiếp giờ, giao khoảng ngày nhưng không chung buổi, bỏ qua chính lịch đang sửa, múi giờ khác ngày, đổi DST, buổi hủy, buổi dời ra ngoài khoảng gốc và đầu vào không hợp lệ.

Mốc giao diện ngày/tuần bổ sung 9 bài ScheduleCalendarTest; tổng 19 bài JVM đều qua. Các ca mới bao gồm ngày trống, biên tháng/năm, Chủ nhật, hủy/dời/đổi phòng, ngoại lệ có ngày gốc không hợp lệ, khác múi giờ tại biên tuần, tách thẻ qua nửa đêm mà không đếm hai buổi và chia làn thẻ. Chưa xác minh bố cục thực tế trên điện thoại ở môi trường này; cần chạy Run và kiểm tra các bước trên.

ScheduleDAOTest dùng database riêng để kiểm tra thêm/sửa/đọc lại sau mở file/xóa, rollback khi trùng, buổi nối tiếp, môn lưu trữ và ngoại lệ. Chưa chạy bộ kiểm thử này hoặc giao diện trên thiết bị tại môi trường hiện tại do lỗi loopback Gradle/ADB đã gặp trước đó. Chạy `gradlew.bat :app:connectedDebugAndroidTest` trên máy có thiết bị kết nối; biên dịch riêng không thay thế build Gradle/link tài nguyên đầy đủ.

## Phần làm sau

Sao chép lịch, trang Hôm nay và giao diện đổi/hủy từng buổi. Luồng tạo môn mới trực tiếp trong form lịch chưa có; hiện dùng Quản lý môn học để tạo môn trước.
