# SmartStudy

Ứng dụng Android hỗ trợ học tập, viết bằng Java và giao diện XML.

## Hiện có

- Header SmartStudy, biểu tượng thông báo và hồ sơ (chưa nối chức năng).
- Navigation gồm 5 màn hình: Hôm nay, Lịch học, Học tập, Thiết bị, Thống kê.
- Icon tô đầy khi chọn, icon viền khi chưa chọn.
- Giữ trạng thái màn hình khi chuyển tab và khôi phục tab khi Activity được tạo lại.

Các màn hình hiện là giao diện khung, chưa hoàn thiện chức năng học tập.

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
