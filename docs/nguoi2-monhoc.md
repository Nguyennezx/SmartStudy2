# Quản lý môn học — người 2

## Cách mở và sử dụng

Mở app → Lịch học → Quản lý môn học. Chọn Thêm môn học, nhập tên, giảng viên tùy chọn và màu. Chạm một dòng để sửa hoặc lưu trữ. Bật Xem môn đã lưu trữ rồi chạm dòng để khôi phục.

Chọn Quản lý học kỳ để thêm/sửa tên và khoảng ngày bằng DatePicker. Trong form môn, chọn học kỳ hoặc Chưa chọn học kỳ. Bộ lọc danh sách hỗ trợ Tất cả học kỳ, Chưa chọn học kỳ và từng học kỳ. Dòng môn hiển thị tên học kỳ; đổi học kỳ không đổi ID môn.

## Liên hệ bài thực hành của thầy

- C1/C3: XML Views, findViewById, listener, Spinner và ListView.
- C2: Intent mở SubjectActivity từ ScheduleFragment; Bundle giữ form khi xoay màn hình.
- C3: SubjectAdapter kế thừa BaseAdapter, layout item_subject riêng, cập nhật bằng notifyDataSetChanged; tái sử dụng convertView.
- C6: Subject model, SubjectDAO, ContentValues, Cursor và SQLiteOpenHelper dùng chung. Cursor đọc cột theo tên và đóng sau khi đọc. SQL nằm trong DAO, dùng tham số cho ID và điều kiện lọc.
- ExecutorService đưa thao tác SQLite ra luồng nền; runOnUiThread cập nhật giao diện. Một hàng đợi dùng chung trong màn hình giúp lần đọc sau khi xoay chờ lần ghi trước hoàn tất.

## File và phối hợp

- subject/data/Subject.java: model dùng chung, có ID, tên, giảng viên, màu và trạng thái lưu trữ.
- subject/data/SubjectDAO.java: getActiveSubjects(), getByArchived(), insert(), update(), setArchived(). Các hàm cần được gọi ngoài luồng giao diện.
- subject/data/Semester.java, SemesterDAO.java: học kỳ và đọc/ghi bảng semesters. Ngày lưu ISO YYYY-MM-DD; kiểm tra ngày có thật bằng LocalDate, hiển thị dd/MM/yyyy.
- subject/ui/SemesterActivity.java, SemesterAdapter.java: màn hình học kỳ và chọn ngày bằng DatePickerDialog. Không xóa học kỳ trong mốc này.
- subject/ui/SubjectActivity.java và SubjectAdapter.java: form, danh sách và xử lý sự kiện.
- res/layout/activity_subject.xml, dialog_subject.xml, item_subject.xml: giao diện.
- res/layout/view_subject_filters.xml: header cuộn của ListView chứa bộ lọc và nút Quản lý học kỳ. Handler click dòng trừ số header để lấy đúng môn.
- res/values/subject_strings.xml: nhãn và danh sách tên màu; subject_theme.xml chỉ áp dụng màn hình môn học, lấy màu từ palette hiện có.
- Đã nối nút trong ScheduleFragment/fragment_schedule và khai báo Activity trong Manifest. MainActivity, menu chung, theme chung, colors.xml, DatabaseHelper và DatabaseSchema được giữ nguyên.

Người 3 dùng new SubjectDAO(context).getActiveSubjects() trên luồng nền để lấy ID, tên và màu môn; model có thêm getSemesterId()/getSemesterName(). ID là khóa liên kết, không tự ghép môn dựa vào tên. Môn cùng tên vẫn được phép vì có thể thuộc học kỳ hoặc lớp khác.

getByArchived(archived, semesterFilter) nhận -1 để xem tất cả, 0 cho môn chưa chọn học kỳ, hoặc ID dương để lọc học kỳ. insert/update có overload nhận Long semesterId, null nghĩa là bỏ gán học kỳ. Hàm update cũ không nhận semesterId vẫn giữ nguyên học kỳ, để tương thích bên gọi hiện có.

DAO dùng DatabaseHelper hiện có, nên file app hiện dùng là smart_study.db. Tài liệu ZIP ghi smartstudy.db; chưa đổi tên để tránh tạo database mới ngoài ý muốn. Không chép database demo đè dữ liệu app.

Lưu trữ chỉ đổi subjects.is_archived, giữ bản ghi liên quan. Khi triển khai lịch và nhắc lịch, cần lọc môn đang dùng và đồng bộ/hủy nhắc của môn lưu trữ.

## Kiểm tra thủ công trong Android Studio

1. Mở SmartStudy2, sync và Run. Thêm “Lập trình Java”, giảng viên và màu xanh tím; kiểm tra danh sách cập nhật.
2. Thử lưu tên rỗng hoặc chỉ dấu cách: form báo lỗi và giữ nội dung.
3. Chạm môn → Sửa, đổi tên/màu, xóa tên giảng viên; lưu và đọc lại.
4. Nhập tên, giảng viên, chọn màu rồi xoay màn hình trước khi lưu: nội dung form vẫn còn.
5. Lưu trữ môn: môn biến mất khỏi danh sách đang dùng. Bật bộ lọc môn lưu trữ, khôi phục và kiểm tra trở lại.
6. Đóng hẳn rồi mở app: môn vẫn còn, ID ổn định. Thêm môn từ danh sách lưu trữ sẽ đưa về danh sách đang dùng để thấy môn mới.
7. Thử bàn phím, chữ cỡ lớn và máy màn hình nhỏ; form cuộn được. Kiểm tra màu và khoảng cách với khung của nhóm trưởng.
8. Vào Quản lý học kỳ, thêm HK1 (01/09/2026–31/12/2026) và HK2 (01/01/2027–31/05/2027). Thử ngày kết thúc trước ngày bắt đầu: form phải báo lỗi, không ghi dữ liệu.
9. Gán Java vào HK1. Lọc HK1 phải thấy Java, HK2 không thấy; Tất cả vẫn có các môn chưa chọn học kỳ.
10. Sửa tên HK1 rồi quay lại: tên học kỳ trên môn và bộ lọc cập nhật. Đổi Java sang HK2 rồi bỏ gán học kỳ; kiểm tra ID môn và các liên kết cũ vẫn còn.
11. Lưu trữ/khôi phục môn trong khi lọc theo học kỳ. Xoay màn hình khi đang nhập học kỳ/môn, kiểm tra nội dung form và học kỳ đã chọn được giữ. Đóng mở app để xác nhận dữ liệu học kỳ và liên kết còn nguyên.

## Kiểm tra tự động và giới hạn xác minh

SubjectDAOTest dùng file SQLite riêng trong cache kiểm thử, không ghi vào database app. Kiểm tra thêm/sửa/lưu trữ/khôi phục, đóng mở lại file, giữ liên kết học kỳ và bài tập, từ chối tên rỗng/màu sai, xử lý ID không tồn tại.

Đã bổ sung kiểm thử gán/đổi/bỏ học kỳ, lọc môn chưa gán, đổi tên học kỳ, khóa ngoại và mở lại dữ liệu. SemesterTest là kiểm thử JVM cho ngày hợp lệ, ngày nhuận, ngày không tồn tại, tên rỗng, năm ngoài giới hạn và khoảng ngày đảo ngược.

Chạy: `gradlew.bat :app:connectedDebugAndroidTest` khi đã kết nối máy ảo hoặc điện thoại.

Kiểm tra phần học kỳ ngày 18/10/2026: AAPT2 compile tài nguyên thành công; mọi tham chiếu R trong Java đều có khai báo tài nguyên; javac biên dịch Java main/test/androidTest thành công với thư viện cache và R tạm để kiểm tra kiểu; chạy JUnitCore cho SemesterTest qua 2 bài kiểm thử. Chưa xác nhận build APK/link tài nguyên đầy đủ hoặc chạy test/UI trên Android cho thay đổi học kỳ: Gradle gặp lỗi kết nối loopback của môi trường. Kết quả javac không thay thế build Gradle và kiểm thử trên thiết bị. Người dùng đã xác nhận chạy được mốc quản lý môn học trước đó.
