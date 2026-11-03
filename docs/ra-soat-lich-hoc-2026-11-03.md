# Rà soát Lịch học — 03/11/2026

## Kết luận trong phạm vi kiểm tra

Chưa phát hiện lỗi mới làm sai dữ liệu hoặc chặn luồng lịch cơ bản khi đọc mã và chạy bộ kiểm thử hiện có. Có thể chuyển sang trang Hôm nay. Kết luận này không thay thế kiểm thử thao tác trên thiết bị hoặc cam kết không còn lỗi.

Người dùng đã xác nhận ngày 01/11: quét ảnh qua Render, kiểm tra/lưu trong app và gọi được khi đổi Wi-Fi, không cần server laptop. Đợt rà soát này không gửi ảnh tới Gemini hoặc thay cấu hình dịch vụ online.

## Các phần đã đối chiếu

- Form lịch: chọn môn theo ID; ngày/giờ bắt buộc; ngày thực sự có thứ đã chọn; kết thúc 00:00 được chọn thành 24:00; giữ dữ liệu nháp khi tạo lại Activity; giữ tác vụ lưu trong ViewModel và chặn bấm Lưu lặp.
- DAO: kiểm tra lại môn active khi ghi; kiểm tra trùng và ghi trong transaction; bỏ chính lịch đang sửa; cho phép giờ nối tiếp; xóa lịch giữ môn; đồng bộ nhắc sau khi thay đổi. Khôi phục môn kiểm tra lịch sẽ hiện trở lại trước khi đổi trạng thái.
- Ngày/tuần: lần xuất hiện theo khoảng hiệu lực, biên tuần, múi giờ, buổi hủy/dời trong dữ liệu; tách buổi qua nửa đêm theo múi giờ hiển thị và đếm theo khóa buổi; chia làn thẻ ngắn/chồng nhau; ẩn trục giờ khi không có buổi.
- Nhắc học: tính lần tiếp theo, sổ đã nhắc, quyền thông báo/nhắc đúng giờ, xử lý alarm cũ và sự kiện khởi động máy. Chưa thử thông báo thực tế trong đợt này.
- AI: chọn ảnh/chụp bằng Activity Result và FileProvider; giới hạn ảnh; Volley không retry tự động; cấu hình kết nối dùng SharedPreferences; không lưu Gemini key trong app. Kết quả là dữ liệu tạm, mở form rồi qua ScheduleRules/DAO như lịch thủ công.
- Ghép môn AI: chỉ tự chọn tên đầy đủ khớp duy nhất; tên gần giống hoặc nhiều môn cùng tên yêu cầu chọn; môn lưu trữ không tự khôi phục; tạo môn qua màn hình quản lý rồi trả ID về form.
- Backend: xác thực token, kích thước yêu cầu, chặn yêu cầu đồng thời, giới hạn lượt thử và xử lý lỗi qua bộ kiểm thử có mock, không gọi API thật.

## Điều chỉnh trong đợt này

Hộp kết nối trước đây chỉ có hint, biến mất khi ô đã có dữ liệu. Thêm nhãn cố định “Địa chỉ máy chủ (backend)” và “Mã kết nối (AI_IMPORT_TOKEN)”, có labelFor. Không thay palette, schema, helper hoặc điều hướng chung.

## Kiểm tra tự động

- 35 bài JVM về học kỳ, quy tắc lịch, ngày/tuần, khôi phục môn và nhắc học: đạt.
- 5 bài SubjectMatcherTest: đạt.
- 55 bài Python về trích xuất/bản nháp/backend: đạt, dùng dữ liệu/mocks, không đại diện độ chính xác trên mọi ảnh thực tế.
- AAPT2 liên kết tài nguyên với 38 thư viện Android cache; kiểm tra kiểu Java main/test/androidTest bằng javac: đạt. Đây không phải build APK Gradle hoặc chạy Android instrumentation/SQLite trên điện thoại.

## Giới hạn và phần chưa triển khai

- Lịch thi, giao diện đổi/hủy riêng từng buổi là phần mở rộng; không coi việc chưa có giao diện này là lỗi của luồng lịch cơ bản.
- AI chưa nhập thẳng các quy tắc tuần lẻ/chẵn, danh sách tuần ngắt quãng hoặc lịch bù theo kỳ. Dòng không hỗ trợ được giữ để đối chiếu, không tự biến thành lịch lặp mọi tuần. Thiếu năm/ngày/giờ thì để người dùng bổ sung.
- Tạo môn qua màn hình quản lý đang là thao tác lưu riêng; chưa đáp ứng yêu cầu tạo môn và lịch trong cùng một transaction. Hủy form lịch có thể để lại môn vừa tạo. Đây là khoảng thiếu so với bản phân công, cần hoàn thiện khi làm luồng tạo môn kèm lịch.
- Danh sách quét giữ trạng thái trong phiên/Bundle khi tạo lại Activity; chưa phải danh sách bản nháp lưu bền vững để đóng rồi mở lại màn hình sau này.
- Thông báo khi app ở nền, sau khởi động máy và chính sách pin của hãng cần kiểm tra trên thiết bị. Người dùng từng cho phép hoãn kiểm tra điện thoại; không ghi nhận là đã thử.
- Trang Hôm nay chưa nối dữ liệu thật, là việc tiếp theo của người 2.
