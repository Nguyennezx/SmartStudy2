# Rà soát phần người 2 sau đợt sửa giao diện — 25/10/2026

## Phạm vi đã đọc và đối chiếu

Môn học, học kỳ, bộ lọc học kỳ/lưu trữ, adapter và hộp thêm/sửa; form lịch và dữ liệu nháp; lịch Ngày/Tuần, ngoại lệ, trục giờ và các hộp xem/sửa/xóa; DAO/validation; tính nhắc, alarm, notification, quyền và receiver; thay đổi tỷ lệ thanh tab chung. Đối chiếu XML/ID/kiểu View với Java, Manifest và schema hiện có. Không triển khai chức năng mới trong lần rà soát này.

| Phần | Kết quả đọc mã và kiểm tra |
| --- | --- |
| Môn học | DAO giữ nguyên ID khi sửa/lưu trữ/khôi phục; tên rỗng và màu sai bị từ chối. Icon giảng viên/học kỳ chỉ đổi trình bày. CompoundButton tương thích SwitchCompat, xử lý lọc vẫn dùng isChecked/listener. Số môn lấy từ kết quả truy vấn đang hiển thị. |
| Học kỳ | Kiểm tra ngày thật, khoảng ngày và năm; thêm/sửa, gán và lọc môn qua ID. Đổi nút Back thành ImageButton giữ cùng listener finish. |
| Form lịch | Mọi ID và kiểu dữ liệu giờ/ngày giữ nguyên; Button/MaterialButton tương thích. Thứ Hai=1 đến Chủ nhật=7; kết thúc 00:00 chuyển 1440 và hiển thị 24:00. Cặp giờ/ngày xếp dọc ở màn hình hẹp hoặc chữ lớn. Bundle giữ nháp; ViewModel giữ tác vụ lưu khi xoay và chặn bấm lưu lặp. |
| Lịch ngày/tuần | Tính các buổi thật theo khoảng hiệu lực, múi giờ và ngoại lệ; buổi dời thay buổi gốc. Thẻ tách qua nửa đêm giữ khóa buổi để không đếm đôi. Trạng thái trống tách với tải/lỗi và chỉ hiện ảnh trường khi không có buổi. |
| Ghi SQLite | DAO kiểm tra lại môn active và lịch còn tồn tại trước khi ghi; kiểm tra trùng và lưu trong transaction. Lỗi không ghi dở. Xóa lịch giữ môn và dùng CASCADE cho ngoại lệ. Helper/schema/version không đổi. |
| Nhắc học | Đọc lại dữ liệu trước khi gửi, khóa buổi hạn chế lặp; cập nhật alarm khi sửa/xóa lịch hoặc đổi trạng thái môn; có khôi phục sau các sự kiện hệ thống. Quyền thông báo và exact alarm được kiểm tra, có phương án không chính xác và banner hướng dẫn. |
| Khung chung | Đã cho root xử lý system-bar inset, thanh tab không cộng thêm; giữ 5 tab, icon và palette. Đổi này đã được người dùng cho phép. |

## Lỗi đã sửa / tình huống đã gia cố

1. Kiểm tra trùng trước đây tính cả buổi dời có ngày gốc sai thứ hoặc nằm ngoài chuỗi. Lịch Ngày/Tuần và nhắc học vốn bỏ qua ngoại lệ đó, gây bất nhất: một buổi không hiển thị vẫn chặn lưu. Thêm bài kiểm thử tái hiện thất bại trước sửa; ScheduleRules nay kiểm tra ngày gốc giống hai phần còn lại. Buổi dời hợp lệ ra ngoài khoảng gốc vẫn được kiểm tra trùng.
2. Form lịch trước đây dùng setSelection(-1) khi môn đã chọn không còn trong danh sách active. Gia cố bằng dòng nhắc ID 0 và chặn lưu đến khi chọn môn hợp lệ, không phụ thuộc vào cách Spinner chọn mặc định sau thay adapter/khôi phục View. Dữ liệu giờ, ngày, phòng, ghi chú và nhắc vẫn giữ.
3. Đã sửa tình huống khôi phục môn làm hiện lại lịch trùng: SubjectDAO kiểm tra các lịch sẽ hiện lại trong cùng transaction trước khi đổi is_archived. Nếu trùng, không đổi trạng thái môn, không xóa/sửa lịch và không lập nhắc mới; hộp báo chỉ rõ môn, ngày, giờ và múi giờ bị trùng. Sau khi điều chỉnh lịch đang dùng, có thể khôi phục lại. Kiểm tra tôn trọng ngoại lệ của cả hai phía, bỏ buổi hủy và tính giờ/ngày thực tế của buổi dời, kể cả ra ngoài khoảng chuỗi gốc. Các lịch đã lưu trữ riêng không được hiện lại nên không chặn khôi phục môn.

## Có áp dụng chương 1–6 không?

| Tài liệu thầy | Áp dụng trong project |
| --- | --- |
| C1 và C3: widget, listener, XML/layout/drawable | findViewById, EditText/Button/ImageButton/Spinner, listener, Toast, LinearLayout/ConstraintLayout, vector/shape, XML mỗi thuộc tính một dòng. |
| C2: Activity/Intent, vòng đời, BroadcastReceiver | Intent/extra mở màn hình và truyền ID/ngày; Bundle/onSaveInstanceState; receiver nhận sự kiện. |
| C3 thực hành ListView nâng cao | SubjectAdapter, SemesterAdapter, ScheduleAdapter kế thừa BaseAdapter, inflate layout dòng, lấy model, tái sử dụng convertView và notifyDataSetChanged. |
| C4: Fragment/navigation | ScheduleFragment trong khung điều hướng chung. |
| C5: SharedPreferences | Sổ buổi đã nhắc và trạng thái xin quyền, không dùng để thay SQLite lưu môn/lịch. |
| C6: DemoSQLite và quản lý lớp/sinh viên | Model → DAO → SQLiteOpenHelper chung; ContentValues, Cursor, truy vấn có tham số; Spinner theo ID, lọc ListView, nạp lại sau ghi. |

Nguồn đối chiếu: các bản trích xuất trong `../ghi_chu_tai_lieu/`, đặc biệt C2_03Chuong02_TPCoBanAndroid, C3_THUC_HANH_C3_DemoListViewNangCao, C4_THUC_HANH_C4DemoViewerPaperNNavigation, C5_06Chuong05_Storage_Shared Preferences, C6_DemoSQLite và C6_DemoQuanLySinhVIenLayOut.

Các phần bổ sung chưa nên nói là thầy đã dạy đầy đủ: AlarmManager/notification channel/quyền exact alarm và POST_NOTIFICATIONS; ViewModel/LiveData/ExecutorService; Java time và thuật toán trùng lịch/chia làn thẻ. Chúng hỗ trợ yêu cầu đồ án và vẫn dùng Java/XML, không chuyển sang Compose, Room hay backend khác.

## Bằng chứng kiểm tra

- 35 bài JVM qua: SemesterTest 2, ScheduleRulesTest 9, ScheduleCalendarTest 9, ReminderPlannerTest 9, ScheduleRestoreTest 6. Sáu ca khôi phục kiểm tra trùng/nối tiếp, buổi hủy ở cả hai phía, buổi dời ra ngoài khoảng gốc, khác múi giờ/ngày và ngoại lệ sai. Bài hồi quy ngoại lệ sai đã được chạy thất bại trước sửa và qua sau sửa.
- Bổ sung bài Android SubjectDAOTest kiểm tra khôi phục trùng bị rollback, giữ lịch và có thể thử lại sau khi dời lịch đang dùng. Bài này đã biên dịch, chưa chạy trên Android; không tính vào 35 bài JVM.
- AAPT2 compile toàn bộ tài nguyên và link thành công với Android SDK 36 cùng 38 thư viện Android trong cache. Các style, icon và tham chiếu mới được link; javac kiểm tra toàn bộ main/test/androidTest bằng R thật do bước link sinh ra thành công. Đây là kiểm tra mạnh hơn bước compile XML/R tạm trước đó, nhưng không thay APK build bằng Gradle.
- git diff --check qua. Không thay DatabaseHelper/DatabaseSchema, database version, colors.xml hay cấu hình dependency.
- Đã thử `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline`. Môi trường vẫn báo `Unable to establish loopback connection` kể cả khi cho phép dùng cache ngoài workspace. Chưa có APK build Gradle thành công trong lần này; các bài Android instrumentation chưa được chạy, không tính vào 29 bài trên.
- Chưa xác nhận toàn bộ bố cục, thao tác cảm ứng, bàn phím, xoay màn hình và thông báo trên điện thoại. Đọc mã và kiểm tra biên dịch không thay cho các bước đó.

## Hành vi hiện có cần biết

- Học kỳ là nhóm môn; khoảng hiệu lực lịch được nhập riêng, chưa tự buộc theo khoảng ngày học kỳ.
- Kiểm tra trùng được thực hiện khi lưu lịch và khi khôi phục môn cũ. Khôi phục bị chặn thì môn giữ trạng thái lưu trữ, người dùng được hướng dẫn điều chỉnh lịch bị trùng trước khi thử lại.
- Lịch có ngoại lệ chưa cho sửa cả chuỗi; chưa có màn tạo ngoại lệ. Thao tác xóa áp dụng toàn chuỗi và đã có thông báo xác nhận.
- Nhắc có thể trễ khi thiếu quyền exact; không gửi bù quá 10 phút sau bắt đầu hoặc sau kết thúc buổi. Force Stop cần mở app lại. Chi tiết ở `nguoi2-nhaclich.md`.
- Trang Hôm nay hiện vẫn là khung, chỉ có lời gọi đồng bộ nhắc; sao chép lịch, đổi/hủy từng buổi và các hạng mục mới chưa được coi là hoàn tất.

## Thử thực tế trước khi chuyển phần mới

1. Thêm/sửa học kỳ và môn, đổi màu, đổi học kỳ; kiểm tra lọc và tên sau đóng/mở app.
2. Thêm lịch, sửa giờ/phòng/nhắc, kiểm tra dữ liệu đọc lại; thử giờ đảo ngược, trùng giờ và hai buổi nối tiếp.
3. Đang nhập lịch, sang Quản lý môn lưu trữ môn đang chọn rồi quay lại: nháp còn, form yêu cầu chọn lại, không lưu nhầm môn khác.
4. Chuyển Ngày/Tuần, đổi tuần qua tháng/năm, chọn Hôm nay; có lịch/không có lịch; kiểm tra ảnh trống và nút thêm không bị che.
5. Xoay khi nhập và khi lưu; mở bàn phím, tăng cỡ chữ; kiểm tra form không cắt nội dung và không lưu đôi.
6. Tạo buổi sắp tới, cấp quyền thông báo/exact; đưa app về nền và thử nhắc. Sửa/xóa/lưu trữ trước giờ nhắc để kiểm tra alarm cũ không phát.

Đợt UI và các sửa lỗi rà soát được quản lý trên nhánh `hai`, chưa gộp vào `main`.
