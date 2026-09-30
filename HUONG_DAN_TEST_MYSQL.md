# Kiểm thử bằng MySQL local trên Windows

## 1. Môi trường

- MySQL **8.0.43** đang chạy tại **localhost:3306**; không cần Docker.
- Khuyến nghị JDK 21. Java 8 trên PATH không chạy được dự án.
- Database `lenglish_test` chỉ dành cho test; không chứa dữ liệu thật và không dùng để chạy ứng dụng thường ngày.
- Test nhanh: `./mvnw.cmd test` (không kết nối MySQL). Toàn bộ: `./mvnw.cmd clean verify`.

## 2. Provision một lần trong MySQL Workbench

Đăng nhập bằng tài khoản có quyền tạo database/user. Chạy các câu dưới đây sau khi thay placeholder mật khẩu bằng mật khẩu riêng trên máy; không gửi mật khẩu vào chat hoặc commit nó. Các câu lệnh cố ý không có `IF NOT EXISTS`: nếu tên đã tồn tại, dừng và kiểm tra thay vì dùng nhầm database/tài khoản cũ.

```sql
CREATE DATABASE lenglish_test
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'lenglish_test'@'localhost'
  IDENTIFIED BY '<THAY_BANG_MAT_KHAU_TEST_RIENG>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON lenglish_test.* TO 'lenglish_test'@'localhost';
SHOW GRANTS FOR 'lenglish_test'@'localhost';
```

Không cấp quyền toàn server, không dùng root cho test, không cấp DROP. Tài khoản phải chỉ có quyền trên database này. Flyway tự tạo 8 bảng và bảng lịch sử khi chạy test; không chạy migration bằng tay trước lần nghiệm thu đầu tiên.

## 3. Chạy trong PowerShell

Đặt JAVA_HOME đến JDK 21 đã cài nếu cần, và thêm `$env:JAVA_HOME\bin` vào PATH của cửa sổ hiện tại. Trong thư mục dự án:

```powershell
$env:TEST_DB_USERNAME = 'lenglish_test'
# URL có thể bỏ qua: mặc định chính xác là URL sau.
$env:TEST_DB_URL = 'jdbc:mysql://localhost:3306/lenglish_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$secret = Read-Host 'Mat khau tai khoan MySQL test' -AsSecureString
$credential = [pscredential]::new('lenglish_test', $secret)
try {
    $env:TEST_DB_PASSWORD = $credential.GetNetworkCredential().Password
    ./mvnw.cmd clean verify
} finally {
    Remove-Item Env:TEST_DB_PASSWORD -ErrorAction SilentlyContinue
    $credential = $null
    $secret = $null
}
```

Nhập mật khẩu vào prompt, không gõ trực tiếp vào command history. Maven vẫn cần mật khẩu dạng plaintext trong environment của process khi chạy; không in environment/log debug chứa secret. Biến đặt ở PowerShell chỉ được kế thừa bởi Maven chạy từ cửa sổ đó, không tự truyền sang IDE hoặc phiên harness đang chạy.

## 4. Cơ chế an toàn và chạy lại

- [MySqlTestConfiguration.java](src/test/java/com/example/english_app_cdcntt/support/MySqlTestConfiguration.java) chỉ chấp nhận URL nêu trên và user `lenglish_test`, từ chối password rỗng trước kết nối. Không đọc `DB_*` production. Không override `spring.flyway.url/user/password` trong test.
- Guard giảm nguy cơ cấu hình nhầm; quyền hạn thật của tài khoản phải được kiểm tra bằng `SHOW GRANTS`.
- Không tự drop/clean schema; Flyway clean bị tắt. `mvn clean` chỉ xóa build output, không xóa MySQL.
- Lần đầu phải bắt đầu từ database trống để nghiệm thu migration. Những lần sau giữ schema/Flyway history; các fixture DML hiện được rollback. Không chạy đồng thời hai phiên test trên cùng database.
- Nếu test bị ngắt hoặc có dữ liệu lạ, kiểm tra database test trước khi chạy lại. Không tự xóa bảng hoặc sửa checksum migration để che lỗi.
- JPA validate giữ nguyên; mapping entity được nghiệm thu sau khi triển khai Phase 1.

## 5. Lỗi thường gặp

- Triệu chứng khi **thiếu biến** (đã gặp 2026-09-29): unit test vẫn xanh 143 nhưng cả 21 IT lỗi `java.lang.IllegalStateException: Failed to load ApplicationContext`, trong đó `Caused by: ... Factory method 'dataSource' threw exception with message: TEST_DB_USERNAME must be the dedicated lenglish_test account (not root)`. Xác nhận bằng `target/failsafe-reports/*.txt`; đây là guard đang từ chối, không phải lỗi code. Biến không được kế thừa sang cửa sổ PowerShell mới — export lại trong đúng cửa sổ chạy Maven.
- `TEST_DB_USERNAME` / `TEST_DB_PASSWORD`: đặt biến trong đúng cửa sổ chạy Maven.
- `Access denied`: kiểm tra password, user host và quyền trên `lenglish_test`.
- `Unknown database`: chưa provision database.
- `Communications link failure`: kiểm tra MySQL Windows service và cổng 3306.
- Lỗi lấy public key khi xác thực: ưu tiên cấu hình TLS/server phù hợp, không tự thêm `allowPublicKeyRetrieval=true` hoặc tắt TLS đại trà; guard hiện cố ý không cho URL tùy ý.
- Failsafe thất bại: xem báo cáo trong thư mục `target/failsafe-reports`; không coi `test` thành công là migration đã đạt.

Đã có kết quả SQL thực tế trên MySQL local: `clean verify` đầy đủ đã đạt BUILD SUCCESS ở cả Phase 1 và Phase 2 (21 IT gồm `RepositoryLayerIT` 15 + `EnglishAppCdcnttApplicationIT` 6 schema/migration), lần gần nhất 2026-09-29 20:47 và đã chạy trên layout package chuẩn hóa của Đợt 6 — xem [TIEN_DO_TRIEN_KHAI.md](TIEN_DO_TRIEN_KHAI.md) Đợt 4–6. Việc còn lại của môi trường là kiểm chứng runtime JDK 21 (các lần nghiệm thu hiện dùng JDK 24 với compiler `release 21`).
