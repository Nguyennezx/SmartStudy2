# SmartStudy

Ứng dụng Android hỗ trợ học tập, viết bằng Java và giao diện XML.

## Hiện có

- Header SmartStudy, biểu tượng thông báo và hồ sơ (chưa nối chức năng).
- Navigation gồm 5 màn hình: Hôm nay, Lịch học, Học tập, Thiết bị, Thống kê.
- Icon tô đầy khi chọn, icon viền khi chưa chọn.
- Giữ trạng thái màn hình khi chuyển tab và khôi phục tab khi Activity được tạo lại.

Tab Lịch học có nút Quản lý môn học: thêm, sửa, chọn màu theo palette, lưu trữ và khôi phục môn bằng SQLite. Có quản lý học kỳ (tên, khoảng ngày), gán môn vào học kỳ và lọc môn theo học kỳ. Các màn hình còn lại đang là giao diện khung.

Tab Lịch học có lịch Ngày/Tuần theo trục giờ, thẻ theo màu môn, chọn ngày và chuyển tuần. Chế độ tuần cuộn ngang để xem đủ 7 cột. Nút + thêm lịch vào ngày đang chọn; chạm thẻ để xem/sửa/xóa lịch lặp. Menu ⋮ mở quản lý môn hoặc tất cả lịch đã tạo. Có kiểm tra ngày/giờ và cảnh báo trùng buổi thực tế. Nhắc giờ học dùng AlarmManager và thông báo Android, có hướng dẫn cấp quyền trong tab Lịch học.

Hướng dẫn phần người 2 và cách kiểm tra: [Quản lý môn học](docs/nguoi2-monhoc.md).

Hướng dẫn lịch học: [Thêm và quản lý lịch học](docs/nguoi2-lichhoc.md).

Hướng dẫn quyền và thử thông báo: [Nhắc giờ học](docs/nguoi2-nhaclich.md).

## Chạy dự án

1. Mở thư mục dự án bằng Android Studio.
2. Dùng JDK 17 trở lên (đã build với JDK 21), cài Android SDK 36 và đồng bộ Gradle.
3. Chọn máy ảo hoặc thiết bị Android 8.1 (API 27) trở lên, rồi nhấn Run.

Build APK debug trên Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

APK được tạo tại `app/build/outputs/apk/debug/app-debug.apk`.

## Vị trí mã nguồn

- `app/src/main/java/com/example/smartstudy/main/MainActivity.java`: chuyển tab.
- `app/src/main/res/layout/view_app_header.xml`: header.
- `app/src/main/res/layout/fragment_*.xml`: giao diện từng màn hình.
- `app/src/main/res/drawable/ic_nav_*.xml`: icon navigation.
- `app/src/main/res/values/navigation_dimensions.xml`: kích thước icon dùng chung.

`local.properties`, cấu hình IDE, cache và kết quả build không được đưa vào Git.
