# TIẾN ĐỘ TRIỂN KHAI BACKEND LEnglish

Kế hoạch gốc: [KE_HOACH_TRIEN_KHAI.md](KE_HOACH_TRIEN_KHAI.md).
Triển khai theo từng đợt nhỏ; hoàn thành một đợt không có nghĩa toàn bộ phase đã đạt DoD.

## Đợt 1 — Phase 0.1: Dependencies và công cụ kiểm thử

**Trạng thái: đã thực hiện; test nhanh đạt. Phase 0 tổng thể chưa hoàn thành.**

### Đã làm

- Bổ sung Flyway/MySQL và jjwt 0.12.6 trong [pom.xml](pom.xml). Testcontainers đã được loại bỏ sau khi chốt dùng MySQL local 8.0.43.
- Cấu hình Surefire cho test nhanh; Failsafe chạy `*IT` trong lifecycle `verify`.
- Cấu hình JaCoCo 0.8.13, report và ngưỡng line coverage 80% theo từng package service. Chưa có service nên chưa có số liệu coverage nghiệp vụ; chưa xác nhận bước check bằng `verify`.
- Chuyển [EnglishAppCdcnttApplicationIT.java](src/test/java/com/example/english_app_cdcntt/EnglishAppCdcnttApplicationIT.java) sang datasource local có guard: chỉ database `lenglish_test` trên `localhost:3306`, chỉ user `lenglish_test`, UTC JDBC URL; không tự kết nối nếu thiếu mật khẩu.
- Thêm [JwtDependenciesTest.java](src/test/java/com/example/english_app_cdcntt/JwtDependenciesTest.java) để kiểm tra runtime jjwt + JSON adapter ký/đọc JWT với nested claims. Đây không phải kiểm thử nghiệp vụ auth.

### Kết quả kiểm tra

- `./mvnw.cmd test`: thành công với JDK 24.0.2 và compiler target/release 21.
- Surefire: **1 test, 0 failures, 0 errors, 0 skipped**.
- Test sources, bao gồm integration test, đã biên dịch thành công.
- Chưa chạy `verify`, integration test MySQL, migration hay ứng dụng với DB thực.
- Lần chạy đầu bị từ chối ghi cache Maven ngoài workspace; chạy lại với quyền được cấp đã thành công.
- Có cảnh báo Lombok gọi API `sun.misc.Unsafe` deprecated trên JDK 24; không làm test thất bại.

### Môi trường cần chuẩn bị

- Java trên PATH hiện là Java 8; tìm thấy JDK 24 tại `C:\Users\PC\.jdks\openjdk-24.0.2+12-54`. Chưa xác nhận runtime JDK 21.
- Docker/Testcontainers không còn là yêu cầu sau khi chuyển sang MySQL local 8.0.43.
- Không thay JAVA_HOME/PATH toàn máy; các giá trị chỉ được đặt trong process chạy Maven.

Lệnh tạm dùng JDK đã tìm thấy, chỉ áp dụng trong cửa sổ PowerShell hiện tại:

```powershell
$env:JAVA_HOME = 'C:\Users\PC\.jdks\openjdk-24.0.2+12-54'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
./mvnw.cmd test
```

Khi đã có JDK 21 và đã provision database `lenglish_test`, cấu hình JAVA_HOME đến JDK 21, đặt `TEST_DB_*` theo hướng dẫn rồi chạy:

```powershell
./mvnw.cmd clean verify
```

Ở đợt 1, `test` chỉ xác nhận smoke test dependencies; không thay thế `verify`. Hiện test nhanh đã bổ sung kiểm tra cấu hình và guard datasource; kiểm thử DB dùng MySQL local riêng, không cần Docker.

## Đợt 2 — Phase 0.2: Cấu hình và Clock UTC

**Trạng thái: đã thực hiện; 30 test nhanh đạt. Phase 0 tổng thể chưa hoàn thành.**

### Đã làm

- Chuyển cấu hình sang [application.yml](src/main/resources/application.yml), xóa cấu hình properties cũ để tránh hai nguồn mặc định: JPA `validate`, tắt open-in-view, Hibernate JDBC UTC; bật Flyway và tắt auto-baseline. Chưa có migration trong đợt này.
- Đăng ký typed configuration records và converter tại [ApplicationPropertiesConfig.java](src/main/java/com/example/english_app_cdcntt/config/ApplicationPropertiesConfig.java).
- [JwtProperties.java](src/main/java/com/example/english_app_cdcntt/config/JwtProperties.java): secret Base64 giải mã tối thiểu 32 byte; access/refresh duration dương, mặc định 30 phút/7 ngày. Phase auth phải decode secret này trước khi tạo khóa ký, không dùng byte của chuỗi Base64 trực tiếp.
- [AiProperties.java](src/main/java/com/example/english_app_cdcntt/config/AiProperties.java): yêu cầu API key/model, URL HTTPS tuyệt đối không chứa credentials/query/fragment; connect/read timeout dương, mặc định 10/30 giây. Chưa tạo HTTP client hoặc gọi AI. Test tích hợp AI sau này dùng mock/stub; không nới HTTPS mặc định để chạy local HTTP.
- [CleanupProperties.java](src/main/java/com/example/english_app_cdcntt/config/CleanupProperties.java): validate cron và zone, mặc định 03:00 UTC; có cờ `enabled`. Chưa bật scheduler hay triển khai cleanup; Phase 6 phải dùng cờ này để điều kiện hóa job.
- [SecretValue.java](src/main/java/com/example/english_app_cdcntt/config/SecretValue.java) che giá trị trong `toString()`; chỉ đọc secret qua `value()` ở nơi cần dùng. Không log accessor này hoặc serialize properties ra API.
- [ClockConfig.java](src/main/java/com/example/english_app_cdcntt/config/ClockConfig.java) cung cấp một bean `Clock.systemUTC()`; nghiệp vụ sau này inject Clock thay vì đọc giờ hệ thống trực tiếp.
- [ApplicationPropertiesTest.java](src/test/java/com/example/english_app_cdcntt/config/ApplicationPropertiesTest.java): 29 test dùng `ApplicationContextRunner`, không DB/Docker/network. Kiểm tra defaults, config thiếu/sai, secret redaction, Clock UTC, YAML thực và override bằng nguồn biến môi trường.
- Cập nhật [EnglishAppCdcnttApplicationIT.java](src/test/java/com/example/english_app_cdcntt/EnglishAppCdcnttApplicationIT.java) với cấu hình giả và tắt cleanup để tương thích validation mới; chưa chạy integration test này.

### Cấu hình môi trường

Không có secret thật hoặc credentials mặc định trong mã nguồn. Cần cung cấp khi chạy backend:

| Biến môi trường | Yêu cầu |
|---|---|
| `DB_URL` | JDBC MySQL URL, schema đã được provision; cấu hình UTC như bên dưới |
| `DB_USERNAME`, `DB_PASSWORD` | Tài khoản riêng của ứng dụng; không dùng root hoặc password rỗng |
| `JWT_SECRET` | Base64 của ít nhất 32 byte ngẫu nhiên mật mã; không dùng secret giả từ test |
| `AI_BASE_URL` | URL HTTPS tuyệt đối của nhà cung cấp AI |
| `AI_API_KEY`, `AI_MODEL` | API key và tên model, không để trống |

Ví dụ phần timezone của JDBC URL (TLS cấu hình theo môi trường, không mặc định tắt TLS production):

```text
jdbc:mysql://<host>:3306/<schema>?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
```

Đặt JVM `-Duser.timezone=UTC` khi vận hành. Clock và Hibernate đã dùng UTC nhưng không tự thay timezone toàn JVM hoặc DB session.

Override thời hạn/timeout bằng đơn vị rõ ràng, ví dụ `APP_JWT_ACCESSTOKENEXPIRATION=45m`, `APP_JWT_REFRESHTOKENEXPIRATION=7d`, `APP_AI_CONNECTTIMEOUT=10s`, `APP_AI_READTIMEOUT=30s`. Theo quy tắc Spring Boot, bỏ dấu gạch ngang trong tên biến môi trường của các key này. Cleanup dùng `APP_CLEANUP_ENABLED`, `APP_CLEANUP_CRON`, `APP_CLEANUP_ZONE`.

Properties bind lúc khởi động; thay biến môi trường/rotate secret cần restart. Chưa có cơ chế hot reload hay hỗ trợ nhiều khóa JWT trong giai đoạn chuyển đổi.

### Kết quả kiểm tra

- Lần đầu `./mvnw.cmd clean test` có 1 test thất bại: nguồn biến môi trường giả trong test đặt tên không chuẩn nên override 45 phút vẫn ra 30 phút. Đã sửa tên thành `systemEnvironment` để dùng đúng relaxed binding của Spring Boot; không thay production defaults để làm test pass.
- Chạy lại `./mvnw.cmd test`: **30 tests, 0 failures, 0 errors, 0 skipped** (29 cấu hình + 1 JWT).
- Chạy bằng JDK 24.0.2, compiler release 21; test sources bao gồm integration test đã biên dịch. Cảnh báo Lombok/Unsafe vẫn còn, không làm test thất bại.
- WARN khi khởi tạo context với cấu hình cố ý sai là kết quả mong đợi của negative tests.
- Chưa chạy ứng dụng với DB thật, `verify`, hoặc kiểm thử trên runtime JDK 21. Test cấu hình không chứng minh DB credentials, TLS hoặc AI credentials thực tế hoạt động.
- Chưa tạo endpoint, entity, migration, scheduler hoặc AI client. Test profile dùng chung và kiểm thử schema vẫn thuộc Phase 0.3.

## Đợt 3 — Phase 0.3: Migration và kiểm thử schema

**Trạng thái: hoàn tất Phase 0.3 trên môi trường MySQL local hiện tại. Người dùng xác nhận `clean verify` đạt BUILD SUCCESS sau sửa assertion CHECK. Runtime JDK 21 vẫn cần kiểm chứng riêng.**

### Đã làm

- [V1__init_schema.sql](src/main/resources/db/migration/V1__init_schema.sql): đủ 8 bảng theo kế hoạch, InnoDB/utf8mb4, BIGINT AUTO_INCREMENT, DATETIME(6); UNIQUE, FK, CHECK và index được đặt tên rõ ràng. Không seed dữ liệu hoặc dùng giờ DB làm default.
- Username/english dùng `utf8mb4_0900_bin`; refresh token dùng ASCII/`ascii_bin` và đủ 2048 ký tự. Chuẩn hóa english vẫn là trách nhiệm ứng dụng ở phase nghiệp vụ.
- Cascade chỉ áp dụng cho ba nhóm cha/con words, cache và phrases. FK đến users dùng RESTRICT; không có liên kết giữa cache và sổ từ cá nhân.
- [application-test.yml](src/test/resources/application-test.yml): secret giả, cấu hình AI giả, tắt cleanup, giữ JPA validate và Flyway; không chứa credentials DB cá nhân. Profile này chỉ có trong test resources, không dùng khi triển khai.
- [MySqlTestConfiguration.java](src/test/java/com/example/english_app_cdcntt/support/MySqlTestConfiguration.java): datasource MySQL local tại `localhost:3306/lenglish_test`, chỉ chấp nhận tài khoản `lenglish_test` và URL UTC cố định. Từ chối thiếu mật khẩu và override kết nối Flyway; không fallback sang `DB_*` của ứng dụng.
- [MySqlTestConfigurationTest.java](src/test/java/com/example/english_app_cdcntt/support/MySqlTestConfigurationTest.java): kiểm tra URL, tài khoản, mật khẩu và override Flyway mà không mở kết nối DB.
- [HUONG_DAN_TEST_MYSQL.md](HUONG_DAN_TEST_MYSQL.md): SQL provision database/user riêng, quyền tối thiểu và PowerShell nhập mật khẩu cục bộ để chạy `clean verify`. Chưa tạo database thực tế trong phiên này vì chưa có credentials.
- [EnglishAppCdcnttApplicationIT.java](src/test/java/com/example/english_app_cdcntt/EnglishAppCdcnttApplicationIT.java): 6 test kiểm tra migration V1 trên DB trống và migrate lại không có migration mới, metadata/index/FK, unique/case sensitivity, CHECK/null/orphan, cascade/cache independence, Unicode/độ dài/microsecond. Dữ liệu test được rollback sau từng test DML.
- [ApplicationPropertiesTest.java](src/test/java/com/example/english_app_cdcntt/config/ApplicationPropertiesTest.java): thêm test đọc profile test mà không cần secret môi trường cá nhân. Chưa có AI client; endpoint giả không thay thế mock/stub khi triển khai AI.

### Kết quả thực tế và phần còn chờ

- `./mvnw.cmd test` sau khi chuyển sang local: **47 tests, 0 failures, 0 errors, 0 skipped** (30 cấu hình + 1 JWT + 16 guard datasource). Toàn bộ test sources, kể cả integration test, biên dịch thành công.
- Lịch sử: `verify` ban đầu dừng ở Maven cache; sau khi được cấp quyền đã tới Failsafe nhưng **6 tests, 6 errors** vì `Could not find a valid Docker environment. Please see logs and check configuration`. Các assertion SQL chưa chạy. Cấu hình Docker sau đó đã được thay bằng MySQL local theo xác nhận của người dùng.
- Theo log `clean verify` người dùng cung cấp: đã kết nối MySQL local, **6 integration tests, 1 failure, 0 errors, 0 skipped** (5 test pass, bao gồm kiểm tra migration). Test CHECK dừng vì MySQL trả SQLState `HY000`, mã `3819`, được Spring bọc bằng `UncategorizedSQLException` thay vì `DataIntegrityViolationException`. Constraint `ck_words_review_count` đã từ chối dữ liệu âm đúng thiết kế.
- Đã sửa riêng 3 assertion CHECK (review_count âm, score -1 và 11) để kiểm tra root `SQLException`, SQLState `HY000`, mã `3819` và đúng tên constraint. Giữ nguyên assertion integrity khác, không sửa migration/schema hoặc xóa dữ liệu.
- Sau sửa assertion, harness chạy `./mvnw.cmd test`: **47 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS; integration test sources biên dịch thành công nhưng chưa chạy lại với DB.
- Sau đó người dùng xác nhận chạy lại `./mvnw.cmd clean verify` đạt **BUILD SUCCESS**. Ghi nhận hoàn tất Phase 0.3 trên MySQL local theo xác nhận này; harness không tự chạy lại integration test và chưa đọc báo cáo chi tiết của lần thành công. Không gửi mật khẩu vào chat.
- Môi trường chạy vẫn là JDK 24.0.2, compiler release 21; cần xác nhận thêm trên JDK 21. Không đổi JAVA_HOME/PATH toàn máy hoặc tự cài Docker.
- `ddl-auto=validate` vẫn giữ nguyên, nhưng chưa có entity: xác nhận mapping Hibernate đầy đủ thuộc Phase 1. Chưa triển khai entity/repository/API và chưa chuyển sang Phase 1.

## Đợt 4 — Phase 1.1–1.4: Entity, Repository và kiểm thử tầng dữ liệu

**Trạng thái: ĐÃ NGHIỆM THU (2026-09-29). Người dùng chạy `.\mvnw.cmd clean verify` local với MySQL: BUILD SUCCESS — 75 unit test + 21 integration test (15 RepositoryLayerIT + 6 schema IT), 0 failures / 0 errors. Trước đó harness: `./mvnw.cmd test` 75/75 xanh.**

### Đã làm

- 1.1: 8 entity JPA trong `src/main/java/com/example/english_app_cdcntt/entity/` (User, Word, WordValue, WordCache, WordCacheValue, Phrase, GrammarError, RefreshToken), enum [Level.java](src/main/java/com/example/english_app_cdcntt/enums/Level.java) và [PartOfSpeech.java](src/main/java/com/example/english_app_cdcntt/enums/PartOfSpeech.java), cùng [JpaAuditingConfig.java](src/main/java/com/example/english_app_cdcntt/config/JpaAuditingConfig.java) cho `createdAt/updatedAt`.
- Chuẩn hóa constructor theo KE_HOACH_TRIEN_KHAI ("không dùng Lombok `@Data`; ưu tiên `@Getter`, constructor protected và method nghiệp vụ"): chuyển 5 entity còn constructor public (User, WordValue, WordCacheValue, GrammarError, RefreshToken) sang `protected` + static factory `create(...)`; grep xác nhận không còn lời gọi `new` từ bên ngoài. Toàn bộ entity chỉ dùng `@Getter`, `@EqualsAndHashCode` theo `id`, `@ToString` ẩn password/token; nghiệp vụ đi qua method có kiểm tra (`requireNonNull`, score 0..10, `reviewCount >= 0`, đồng bộ hai chiều collection cha/con, `isExpired` tại biên).
- 1.2: 8 repository trong `src/main/java/com/example/english_app_cdcntt/repository/` — mọi query dữ liệu người dùng scope theo `userId`; due list dùng `nextReview <= cutoff` (inclusive); `findOwnedForUpdate` khóa `PESSIMISTIC_WRITE`; `@EntityGraph`/JOIN FETCH tránh N+1; bulk delete refresh token hết hạn dùng `@Modifying(flushAutomatically, clearAutomatically)`.
- 1.3: [EntityBusinessRulesTest.java](src/test/java/com/example/english_app_cdcntt/entity/EntityBusinessRulesTest.java) — 28 test JUnit thuần, không Spring context/DB, chạy bằng Surefire.
- 1.3: [RepositoryLayerIT.java](src/test/java/com/example/english_app_cdcntt/repository/RepositoryLayerIT.java) — 15 integration test chạy với MySQL local `lenglish_test` qua `MySqlTestConfiguration`, `@Transactional` rollback: validate mapping, cascade + orphanRemoval, unique violation, ownership isolation, due boundary inclusive, username case-sensitive (utf8mb4_0900_bin), bulk delete refresh token hết hạn, phân trang phrase theo `createdAt DESC`, audit timestamps.
- 1.4: xác nhận `ddl-auto: validate` ở cả [application.yml](src/main/resources/application.yml) và [application-test.yml](src/test/resources/application-test.yml) — schema V1 và mapping Hibernate bắt buộc khớp khi IT chạy.
- Điều chỉnh sau phản hồi người dùng: `word_cache` là kho dữ liệu AI dùng chung, tích lũy vĩnh viễn, không dọn theo thời gian — đã xóa `deleteAllUpdatedBefore` khỏi `WordCacheRepository`, xóa IT `deleteAllUpdatedBeforeRemovesStaleCacheRows` (16→15 IT) và ghi quyết định vào KE_HOACH_TRIEN_KHAI §2/§11. Bulk delete chỉ còn áp dụng cho refresh token hết hạn (scheduler Phase 6).

### Kết quả kiểm tra

- Hai lỗi phát hiện và đã sửa trong đợt: (1) `WordCacheRepository.findByEnglish` trả `Optional<WordCache>` chứ không trả entity trần — IT sửa thành `.orElseThrow()`/`isPresent()`; (2) test kỳ vọng `Word.updateLevel(null)` ném NPE nhưng field `level` nullable có chủ đích (chưa xác định level) — sửa test thành assert null hợp lệ, giữ nguyên entity.
- `./mvnw.cmd test` (JDK 24.0.2, compiler release 21): **75 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS** (30 config + 28 entity + 1 JWT + 16 guard datasource).
- Chưa chạy integration test với DB thật: harness không có MySQL/credentials. `RepositoryLayerIT` (15) và `EnglishAppCdcnttApplicationIT` (6) chỉ chạy khi người dùng thực hiện `clean verify` local.
- Lần `clean verify` đầu của người dùng (2026-09-29, sau khi đặt JAVA_HOME): 75 unit test pass (build chạy tới failsafe), nhưng cả 21 IT lỗi nạp ApplicationContext. Root cause duy nhất trong `target/failsafe-reports`: `java.lang.IllegalArgumentException: TEST_DB_USERNAME must be the dedicated lenglish_test account (not root)` tại `MySqlTestConfiguration.validateTarget` (MySqlTestConfiguration.java:35) — phiên PowerShell mới chưa export `TEST_DB_USERNAME`/`TEST_DB_PASSWORD` (guard coi biến thiếu, khác `lenglish_test`, hoặc root là lỗi). Không phải lỗi code/mapping. Người dùng sau đó export đủ biến theo HUONG_DAN_TEST_MYSQL.md §3 và chạy lại thành công.
- Lần chạy nghiệm thu (2026-09-29 14:40): **BUILD SUCCESS** — kiểm chứng trực tiếp từ báo cáo của lần chạy: `failsafe-summary.xml` completed 21 / failures 0 / errors 0 / skipped 0; `RepositoryLayerIT` 15/15 pass (0.697 s), `EnglishAppCdcnttApplicationIT` 6/6 pass (8.601 s); surefire xanh toàn bộ — ApplicationPropertiesTest 30, EntityBusinessRulesTest 28 (XML ghi đủ 28 testcase pass, skipped=0; thuộc tính `tests="24"` ở header chỉ là artifact đếm của surefire khi 6 lớp @Nested dùng chung tên method `createRejectsNullFields`, không phải test bị bỏ qua), JwtDependenciesTest 1, MySqlTestConfigurationTest 16. Tổng: 75 unit + 21 IT.

### Nghiệm thu Phase 1 — ĐÃ HOÀN TẤT (2026-09-29)

Người dùng đã mở PowerShell tại thư mục dự án, đặt biến môi trường theo [HUONG_DAN_TEST_MYSQL.md](HUONG_DAN_TEST_MYSQL.md) §3 (`JAVA_HOME`, `TEST_DB_USERNAME=lenglish_test`, `TEST_DB_PASSWORD` — mật khẩu nhập cục bộ, không gửi vào chat; không đặt `TEST_DB_URL`) và chạy:

```powershell
.\mvnw.cmd clean verify
```

Kết quả: **BUILD SUCCESS** với 75 unit test + 21 integration test (15 RepositoryLayerIT + 6 schema IT), 0 failures / 0 errors — đúng kỳ vọng. **Phase 1 chính thức đóng.**

## Đợt 5 — Phase 2: Auth và security

**Trạng thái: ĐÃ NGHIỆM THU (2026-09-29) — Phase 2 đóng, xem mục “Nghiệm thu Phase 2” cuối đợt.**

> Đường dẫn package trong mục “Đã làm” là layout lúc viết đợt này (trước Đợt 6). Layout hiện tại theo KE_HOACH §3, xem bảng di chuyển ở Đợt 6.

### Đã làm

- 2.1 JWT/security: `security/JwtService` (issue/parse, chọn thời hạn theo `TokenType`, claim `type`/`username`/`userId`/`expiresAt`), `TokenType` (ACCESS/REFRESH + `fromClaim` trả `Optional`), `JwtAuthenticationFilter` (chỉ chấp nhận access token, đặt `UserPrincipal`), `AppUserDetailsService`/`UserPrincipal`, `SecurityConfig` (stateless, CSRF tắt, 4 endpoint `/api/auth/**` permitAll, entry point + access-denied handler trả JSON), `config/JwtProperties` validate lúc khởi động qua `SecretValue` (không log secret).
- 2.2 Forms/DTO + handler: `auth/RegisterForm`, `LoginForm`, `RefreshForm`, `LogoutForm` (validation Jakarta, `@MaxUtf8Bytes` cho giới hạn 72 byte của BCrypt), `AuthResponse`/`MessageResponse`/`ErrorResponse`/`ValidationErrorResponse`/`FieldErrorDetail`; `web/GlobalExceptionHandler` map `DuplicateUsernameException`→409, `InvalidCredentialsException`/`TokenExpiredException`→401, lỗi validation→400 (riêng login trả 401 để không lộ thông tin), body sai định dạng→400.
- 2.3 Endpoint: `auth/AuthController` với `POST /api/auth/register` (201 + message), `POST /api/auth/login`, `POST /api/auth/logout`, `POST /api/auth/refresh`.
- 2.4 Rotation/refresh token: `service/RefreshTokenTxService` (`issueSession`, `attemptRotation`, `revoke`) + `service/AuthService` (điều phối, không `@Transactional` để write nằm trong tx bean riêng) + `service/RegistrationTxService`; `RefreshRotationResult` (sealed: `Rotated`/`Expired`/`Invalid`); `RefreshTokenRepository.findByTokenForUpdate` khóa `PESSIMISTIC_WRITE`, `deleteByTokenValue`; rotation xoá token cũ và phát hành cặp mới, token hết hạn bị xoá **trước khi** trả 401 (transaction commit), token đã thu hồi dùng lại → 401.
- 2.5 Test: 8 file test mới cho Phase 2 — `security/JwtServiceTest` (212 dòng), `security/TokenTypeTest` (57), `service/AuthServiceTest` (224), `service/RefreshTokenTxServiceTest` (337), `service/RegistrationTxServiceTest` (105), `auth/AuthControllerTest` (172, MockMvc standalone + controller advice), `web/GlobalExceptionHandlerTest` (240), phần bổ sung của `config/ApplicationPropertiesTest`.
- `pom.xml`: thêm property `mockito.agent.jar` và `<argLine>@{argLine} -javaagent:${mockito.agent.jar}</argLine>` cho surefire + failsafe. Lý do: Mockito 5.23 self-attach bằng cách spawn tiến trình ngoài, bị chặn trong môi trường sandbox/JDK 24 (`Mockito is unable to load the plugin ... MockMaker`); nạp sẵn `-javaagent` là cách Mockito khuyến nghị cho JDK 21+ và giữ nguyên `@{argLine}` để không phá jacoco.

### Kết quả kiểm tra

- `./mvnw.cmd verify -DskipITs` (JDK 24.0.2, compiler release 21): **BUILD SUCCESS** — `Tests run: 143, Failures: 0, Errors: 0, Skipped: 0`; `jacoco:check` báo `All coverage checks have been met.` (ngưỡng 80% dòng cho gói `service`).
- Độ phủ jacoco gói service (từ `target/site/jacoco/jacoco.csv`): `AuthService` 31/31, `RefreshTokenTxService` 31/31, `RegistrationTxService` 7/8, `RefreshRotationResult` 3/3 → **72/73 dòng = 98.6%**, vượt ngưỡng 80%.
- Lỗi đã sửa trong đợt (đều do test, không phải code production): (1) `AuthServiceTest` stub `void createUser` bằng `when(...).thenThrow(...)` → chuyển sang `doThrow(...).when(...)`; (2) 2 file test thiếu import repository; (3) `AuthControllerTest` dùng payload mật khẩu 7 ký tự (dưới min 8) và kỳ vọng 400 cho validation của login — thực tế handler trả 401 theo thiết kế, đã sửa payload và kỳ vọng; (4) `GlobalExceptionHandlerTest` gọi `HttpMessageNotReadableException(String, Throwable)` không tồn tại → dùng constructor hợp lệ.
- **Chưa chạy được IT trong phiên này**: 21 IT (`RepositoryLayerIT` 15 + `EnglishAppCdcnttApplicationIT` 6) lỗi nạp ApplicationContext với cùng một nguyên nhân duy nhất đã biết từ Phase 1 — `IllegalArgumentException: TEST_DB_USERNAME must be the dedicated lenglish_test account (not root)` tại `MySqlTestConfiguration.validateTarget` (MySqlTestConfiguration.java:35), vì phiên PowerShell này chưa export `TEST_DB_USERNAME`/`TEST_DB_PASSWORD`. Không phải lỗi code/mapping Phase 2. Người dùng cần export biến theo [HUONG_DAN_TEST_MYSQL.md](HUONG_DAN_TEST_MYSQL.md) §3 rồi chạy `.\mvnw.cmd clean verify`; việc này đã được thực hiện và ghi kết quả ở mục “Nghiệm thu Phase 2” bên dưới.

### Nghiệm thu Phase 2 — ĐÃ HOÀN TẤT (2026-09-29 20:47)

Người dùng mở PowerShell tại thư mục dự án, export `TEST_DB_USERNAME`/`TEST_DB_PASSWORD` theo [HUONG_DAN_TEST_MYSQL.md](HUONG_DAN_TEST_MYSQL.md) §3 rồi chạy `.\mvnw.cmd clean verify`: **BUILD SUCCESS**. Bằng chứng đọc trực tiếp từ báo cáo của chính lần chạy đó:

- `target/failsafe-reports/failsafe-summary.xml`: `completed 21 / errors 0 / failures 0 / skipped 0`.
- `RepositoryLayerIT` 15/15 (0,739 s) — mapping, cascade + orphanRemoval, unique, ownership isolation, due boundary, collation, bulk delete refresh token, phân trang phrase, audit timestamps.
- `EnglishAppCdcnttApplicationIT` 6/6 (9,022 s) — migration V1 trên DB trống và chạy lại không sinh migration mới, metadata/index/FK, unique/case sensitivity, CHECK/null/orphan, cascade/cache independence, Unicode/độ dài/microsecond.
- `target/surefire-reports/*.xml` (11 lớp, ghi lúc 2026-09-29 20:46): **143 phần tử `<testcase>`, `failures=0`/`errors=0`/`skipped=0` ở mọi lớp** (cộng thuộc tính `tests` chỉ ra 139 vì `EntityBusinessRulesTest` ghi `tests="24"` ở header trong khi XML có đủ 28 testcase `@Nested` — artifact đã ghi ở Đợt 4).
- `target/site/jacoco/jacoco.csv` cùng lần chạy: `service/AuthService` 31/31, `service.tx` 41/42 (= `RefreshTokenTxService` 31/31 + `RegistrationTxService` 7/8 + `RefreshRotationResult` 3/3) → 97,6%, vượt ngưỡng 80% của `jacoco:check` (goal gắn vào `verify`, build xanh nên check đã đạt).

**Phase 2 đóng về mặt chức năng**: 4 endpoint + rotation chạy đúng trên MySQL thật, 21 IT xanh trên layout package mới (Đợt 6). Tuy nhiên audit đối chiếu DoD §11 Phase 2 (2026-09-29) phát hiện thêm 4 hạng mục chưa đạt — 3 hạng mục đã bù ở **Đợt 8** (BCrypt cost 12, test cho filter/security chain, unit test BCrypt) và 1 hạng mục integration chuyển sang Phase 7.

## Đợt 6 — Chuẩn hóa package theo KE_HOACH §3 (ENUM, PACKAGE VÀ ENTITY)

**Trạng thái: ĐÃ HOÀN TẤT (2026-09-29) — unit test xanh và `clean verify` đầy đủ với MySQL local đạt BUILD SUCCESS (143 testcase + 21 IT, 0 failures / 0 errors).**

### Lý do

`KE_HOACH_TRIEN_KHAI` §3 quy định cây package mục tiêu, nhưng Phase 2 đã đặt code vào ba package không có trong cây đó: `auth/`, `web/`, `security/`. Đợt này chỉ **di chuyển file và sửa `package`/`import`**: không đổi tên lớp, chữ ký, annotation, contract HTTP, migration hay cấu hình.

### Đã làm

| Vị trí cũ | Vị trí mới | Căn cứ §3 |
|---|---|---|
| `auth/AuthController` | `controller/AuthController` | §3: `controller/ AuthController...` |
| `auth/{RegisterForm, LoginForm, LogoutForm, RefreshForm}` + `MaxUtf8Bytes`, `Utf8ByteLengthValidator` | `form/` | §3: `form/ RegisterForm, LoginForm, LogoutForm, RefreshForm...`; §3 không có package validator riêng nên annotation + validator của form ở cùng `form/` |
| `auth/AuthResponse` | `dto/AuthResponse` | §3: `dto/ AuthResponse, ...` |
| `web/{MessageResponse, ValidationErrorResponse}` | `dto/` | §3 liệt kê đúng hai lớp này trong `dto/` |
| `web/{ErrorResponse, FieldErrorDetail}` | `dto/` | cùng nhóm body lỗi của contract (§4/§9.2) với `ValidationErrorResponse` |
| `web/GlobalExceptionHandler` | `exception/GlobalExceptionHandler` | §3: `exception/ Các exception nghiệp vụ + GlobalExceptionHandler` |
| `web/GlobalErrorController` | `controller/GlobalErrorController` | là `@Controller` nên thuộc `controller/` |
| `service/{RefreshTokenTxService, RegistrationTxService}` | `service/tx/` | §3: `service/tx/ RefreshTokenTxService, WordCacheTxService, PhraseTxService` (RegistrationTxService cùng loại) |
| `service/RefreshRotationResult` | `service/tx/RefreshRotationResult` | là kiểu kết quả của `RefreshTokenTxService.attemptRotation` |
| `security/{JwtService, JwtAuthenticationFilter, AppUserDetailsService, UserPrincipal, TokenType, RestAuthenticationEntryPoint, RestAccessDeniedHandler, SecurityErrorResponses}` | `config/` | §3 đặt `SecurityConfig, JwtService, JwtAuthenticationFilter, JwtProperties` trong `config/` và **không có** package `security/` |

- Xóa ba package rỗng `auth/`, `web/`, `security/` (cả main và test). Test đổi package để mirror production: `auth/AuthControllerTest` → `controller/`, `web/GlobalExceptionHandlerTest` → `exception/`, `security/{JwtServiceTest, TokenTypeTest}` → `config/`, `service/{RefreshTokenTxServiceTest, RegistrationTxServiceTest}` → `service/tx/` (test vẫn cùng package với lớp được test nên truy cập package-private không đổi).
- Cập nhật toàn bộ import chéo package; các lớp trước đây cùng package nay tách ra được thêm `import` tường minh (ví dụ [AuthController.java](src/main/java/com/example/english_app_cdcntt/controller/AuthController.java) import `dto.AuthResponse`, `dto.MessageResponse`, `form.*`; [GlobalExceptionHandler.java](src/main/java/com/example/english_app_cdcntt/exception/GlobalExceptionHandler.java) import `dto.ErrorResponse/FieldErrorDetail/ValidationErrorResponse`; [RefreshTokenTxService.java](src/main/java/com/example/english_app_cdcntt/service/tx/RefreshTokenTxService.java) import `config.JwtService`, `config.TokenType`).
- Sửa tham chiếu package cũ còn sót trong javadoc: `{@link GlobalExceptionHandler}` trong `GlobalErrorController`, `{@code com.example.english_app_cdcntt.security}` trong `GlobalExceptionHandlerTest`.
- Grep sau khi sửa: không còn kết quả nào cho `com.example.english_app_cdcntt.(auth|web|security).` trong `src/`.

### Kết quả kiểm tra

- `.\mvnw.cmd clean test` (JDK 24.0.2, compiler release 21): **BUILD SUCCESS — Tests run: 143, Failures: 0, Errors: 0, Skipped: 0** — đúng bằng baseline Phase 2; log surefire đã báo theo package mới (`config.JwtServiceTest` 10, `config.TokenTypeTest` 7, `controller.AuthControllerTest` 10, `exception.GlobalExceptionHandlerTest` 14, `service.AuthServiceTest` 13, `service.tx.RefreshTokenTxServiceTest` 11, `service.tx.RegistrationTxServiceTest` 3, `config.ApplicationPropertiesTest` 30, `entity` 28, `JwtDependenciesTest` 1, `support` 16).
- `.\mvnw.cmd verify -DskipITs`: **BUILD SUCCESS**, `jacoco:check` báo `All coverage checks have been met.` Rule hiện tại `includes com.example.english_app_cdcntt.service*` bao trùm cả package mới `service.tx`. Từ `target/site/jacoco/jacoco.csv`: `service/AuthService` 31/31 = 100%, `service.tx` = `RefreshTokenTxService` 31/31 + `RegistrationTxService` 7/8 + `RefreshRotationResult` (3 record) 3/3 → **41/42 = 97,6%**.
- Phiên harness chưa chạy được 21 IT (thiếu `TEST_DB_USERNAME`/`TEST_DB_PASSWORD`, guard `MySqlTestConfiguration.validateTarget` như Phase 1/2); sau đó người dùng đã export biến và chạy `.\mvnw.cmd clean verify` với MySQL local: **BUILD SUCCESS** — `target/failsafe-reports/failsafe-summary.xml` `completed 21 / errors 0 / failures 0 / skipped 0` (`RepositoryLayerIT` 15/15, `EnglishAppCdcnttApplicationIT` 6/6), unit test 143 testcase 0 lỗi. Đợt 6 coi như đóng: 21 IT này chạy trên layout package mới nên việc di chuyển không phá mapping, schema hay context.

### Sai lệch còn lại so với §3

- `service/impl/` chưa tồn tại: §3 dự kiến interface ở `service/` và implementation ở `service/impl/`, nhưng Phase 2 viết `AuthService` là class cụ thể nên chưa có interface để tách. Sẽ tạo cùng interface đầu tiên (WordService/GradingService/AiClient ở Phase 3–4). **(Đã xử lý ở Đợt 7: `service/AuthService` → interface, `service/impl/AuthServiceImpl` → implementation.)**
- `mapper/` chưa tồn tại: chưa có mapper nào (Phase 3).
- `controller/` hiện có `AuthController` và `GlobalErrorController`; `WordController`/`PhraseController` thuộc Phase 3/5.

### Quyết định vị trí `JwtService`, `AppUserDetailsService`, `TokenType` (2026-09-29)

Giữ nguyên cả ba trong `config/` (cụm JWT/security); **không** chuyển sang `service/` hay `enums/`.

- §3 (dòng 151–153) ghi rõ `config/ SecurityConfig, JwtService, JwtAuthenticationFilter, JwtProperties, ...`; `enums/` (dòng 155) chỉ có `Level, PartOfSpeech`; `service/` và `service/tx/` là service nghiệp vụ và tx bean.
- Chặn kỹ thuật: `SecurityConfig` (config) nhận `JwtService`, `AppUserDetailsService` làm tham số bean và `new JwtAuthenticationFilter(...)` (SecurityConfig.java:29,33,35). Nếu hai lớp đó nằm ở `service/` thì `config → service`, trái quy tắc trong [skills/layered-architecture/SKILL.md](skills/layered-architecture/SKILL.md) (“Configuration never imports service or controller classes”). Hiện `config/` không import lớp `service` nào.
- `AppUserDetailsService` là adapter `UserDetailsService` của Spring Security, chỉ map `User` → `UserPrincipal` cho filter đối chiếu `uid`; grep toàn `src/` cho thấy nó chỉ được dùng bởi `JwtAuthenticationFilter` và `SecurityConfig` — không có chỗ nào ngoài cụm JWT. Nghiệp vụ auth nằm ở `service/AuthService`.
- `TokenType` là giá trị của claim JWT `type=access|refresh`, **không** persist vào cột DB như `Level`/`PartOfSpeech`; chỉ được dùng bởi `JwtService`, `JwtAuthenticationFilter`, `RefreshTokenTxService` (+2 file test). Để cạnh `JwtService` giữ hợp đồng JWT trong một chỗ.
- Nếu đổi: phải sửa §3 (tài liệu nghiệm thu), import ở 3 file main + 2 file test; và chuyển vào `service*` sẽ kéo `JwtService` + `AppUserDetailsService` vào ngưỡng `jacoco:check` 80% của gói `service*` (hiện `AppUserDetailsService` chưa có test). Muốn gom enum về một chỗ thì phải đổi quy ước trước rồi cập nhật §3, không làm lẻ.

## Đợt 7 — Tách interface/implementation theo §3 (`service/` + `service/impl/`)

**Trạng thái: ĐÃ HOÀN TẤT (2026-09-29) — `clean verify -DskipITs` BUILD SUCCESS, 143 test, coverage đạt.**

### Lý do

§3 (dòng 143–145) quy định `service/` chứa **contract** (`AuthService, WordService, PhraseService, GradingService, TokenCleanupService, AiClient`) và `service/impl/` chứa **implementation** (`AuthServiceImpl`, sau này `LlmAiClient` và các impl khác). Phase 2 lại viết `AuthService` là class cụ thể nên `service/impl/` chưa tồn tại; sau Đợt 6 đây là sai lệch duy nhất còn lại của tầng service. Đợt này chỉ **tách interface/impl**, không đổi hành vi, chữ ký hay contract HTTP.

### Đã làm

- `service/AuthService.java` → **interface** (`register`, `login`, `refresh`, `logout`) với javadoc hợp đồng §5.2 trên từng method.
- `service/impl/AuthServiceImpl.java` → **implementation** (`@Service`, `implements AuthService`, `@Override` trên 4 method), giữ nguyên thân code, hằng số `uk_users_username` và helper `isUsernameUniqueViolation`.
- Test đổi theo để mirror production: `service/AuthServiceTest.java` → `service/impl/AuthServiceImplTest.java`, package `...service.impl`, field/`new` chuyển sang `AuthServiceImpl` (13 test giữ nguyên nội dung).
- Không phải sửa gì ở nơi gọi: `controller/AuthController` và `controller/AuthControllerTest` đã phụ thuộc kiểu `com.example.english_app_cdcntt.service.AuthService` (giờ là interface) nên import, constructor injection và `@Mock` giữ nguyên. `service/tx/*` vẫn là class cụ thể như §3 (tx bean), `RefreshRotationResult` ở `service/tx/`.

### Kết quả kiểm tra

- `.\mvnw.cmd clean verify -DskipITs` (JDK 24.0.2, compiler release 21): **BUILD SUCCESS — Tests run: 143, Failures: 0, Errors: 0, Skipped: 0**; log surefire báo `service.impl.AuthServiceImplTest` 13 test.
- `jacoco:check`: `All coverage checks have been met.` — rule `includes com.example.english_app_cdcntt.service*` bao trùm cả package mới `service.impl`; từ `target/site/jacoco/jacoco.csv`: `service.impl/AuthServiceImpl` 31/31, `service/tx` 41/42, `service` chỉ còn interface `AuthService` (0 dòng thực thi).
- Chưa chạy lại 21 IT trong phiên này (thiếu `TEST_DB_*`); thay đổi thuần cấu trúc lớp, không chạm entity/mapping/migration nên IT không bị ảnh hưởng về mặt mã — chạy `.\mvnw.cmd clean verify` khi cần đóng mốc.

### Sai lệch còn lại so với §3

- `mapper/` chưa tồn tại (Phase 3).
- `service/` hiện mới có `AuthService`; `WordService`/`PhraseService`/`GradingService`/`TokenCleanupService`/`AiClient` thuộc Phase 3–6, khi thêm sẽ theo pattern: interface ở `service/`, impl ở `service/impl/`.
- `controller/` hiện có `AuthController` và `GlobalErrorController`; `WordController`/`PhraseController` thuộc Phase 3/5.

## Đợt 8 — Audit đối chiếu KE_HOACH và bù nợ DoD Phase 2 (2026-09-29)

**Trạng thái: hoàn tất các hạng mục bù được trong phiên; còn 1 hạng mục integration chuyển Phase 7.**

### Bối cảnh

Đối chiếu toàn bộ KE_HOACH (§1, §2, §3, §4, §5, §9.2, §10, §11, §12, §14) với code và báo cáo test để xác nhận hướng đi trước khi sang Phase 3. Kết quả: cấu trúc §3 khớp sau Đợt 6–7; deps §10.1 và `application.yml` §10.2 khớp từng dòng; contract 4 endpoint §4.1 khớp status/message. Sai lệch tìm thấy nằm ở tầng test/cấu hình bảo mật như dưới.

### Sai lệch đã sửa

1. **BCrypt cost 10 thay vì 12** — §1.1 yêu cầu `BCryptPasswordEncoder(12)` (skill `spring-security-jwt` cũng vậy), nhưng [SecurityConfig.java](src/main/java/com/example/english_app_cdcntt/config/SecurityConfig.java) dùng constructor mặc định (cost 10). Đã sửa thành `new BCryptPasswordEncoder(12)`. Hash cũ vẫn verify được vì BCrypt ghi cost trong chính hash, không cần migrate dữ liệu.
2. **Không có test nào cho filter/security chain** — DoD Phase 2 yêu cầu “MVC slice xác nhận JSON/status **và filter**”, §12 yêu cầu import security config/filter; trước đợt này `AuthControllerTest` là MockMvc standalone (không có filter). Thêm [AuthControllerSecurityTest.java](src/test/java/com/example/english_app_cdcntt/controller/AuthControllerSecurityTest.java): `@WebMvcTest` + `@Import(SecurityConfig.class, GlobalExceptionHandler.class)` + `@MockitoBean` cho `AuthService`, `JwtService`, `AppUserDetailsService`; 9 test phủ: 401 từ entry point khi thiếu token; permitAll vào được khi không token; token hỏng bị 401 **cả trên permitAll**; token hỏng trên route protected → 401; refresh token làm Bearer → 401; `uid` không khớp tài khoản → 401; tài khoản đã xoá → 401; token hợp lệ đi qua filter (route chưa map → 404, không phải 401); request ngoài `/api/**` → 403 từ access-denied handler.
3. **Không có unit test BCrypt** — DoD yêu cầu “unit test BCrypt/JWT”. Thêm [SecurityConfigTest.java](src/test/java/com/example/english_app_cdcntt/config/SecurityConfigTest.java): hash phải có prefix `$2a$12$` và round-trip `matches` (đúng/sai mật khẩu).
4. **Javadoc sai tham chiếu** — `TokenExpiredException` ghi `(§5.5)` trong khi kế hoạch chỉ có §5.1–5.3 → sửa thành `(§5.2:240)`; `GlobalExceptionHandlerTest` viện dẫn `SecurityErrorResponsesTest` không tồn tại → trỏ về `AuthControllerSecurityTest.denyAllRouteIsForbidden`.

### Kết quả kiểm tra

- `.\mvnw.cmd clean test` (JDK 24.0.2, compiler release 21): **BUILD SUCCESS — Tests run: 154, Failures: 0, Errors: 0, Skipped: 0** (trước đợt: 143; +11 test: `AuthControllerSecurityTest` 9, `SecurityConfigTest` 2).
- `.\mvnw.cmd verify -DskipITs`: **BUILD SUCCESS**, `jacoco:check` báo `All coverage checks have been met.`
- Chưa chạy IT trong phiên này (thiếu `TEST_DB_*`); các thay đổi không chạm entity/repository/migration.

### Nợ DoD còn lại → chuyển Phase 7

DoD §11 Phase 2 mục 3 yêu cầu integration test với DB thật: row refresh bị xoá **thật (đã commit)**, token mới khác token cũ **trong cùng một giây**, token cũ dùng lại thất bại, và **hai refresh đồng thời chỉ một thành công** — §5.2:243 ghi rõ “Test bằng transaction/kết nối độc lập, không chỉ mock repository”. Hiện chỉ có unit test mock (`RefreshTokenTxServiceTest` 11 test), chưa có IT. Cần MySQL local nên không chạy được trong phiên này; đưa vào bộ IT Phase 7 (integration end-to-end) hoặc làm thành Đợt 9 trước Phase 3 nếu muốn đóng DoD Phase 2 trước.

## Đợt 9 — Phase 3: Words CRUD, ownership và due-count (2026-09-30)

**Trạng thái: hoàn tất Phase 3, đủ DoD §11 (unit + MVC + IT DB thật), đã nghiệm thu trong phiên bằng `clean verify` đầy đủ.**

### Phạm vi triển khai

5 endpoint trong nhóm `/api/words` theo §4.1 (GET /api/words, GET /api/words/due-count, POST /api/words 201, PUT /api/words/{id}, DELETE /api/words/{id}); `GET /api/words/generate` (Phase 4) và `POST /api/words/review` (Phase 6) chưa làm theo kế hoạch. Owner id luôn lấy từ principal đã xác thực (§5.3), không nhận từ request.

### File mới

- [WordController.java](src/main/java/com/example/english_app_cdcntt/controller/WordController.java) — mapper mỏng giữa contract và `WordService`; `SuccessResponse<WordDto>` bao phản hồi create/update.
- [WordService.java](src/main/java/com/example/english_app_cdcntt/service/WordService.java) + [WordServiceImpl.java](src/main/java/com/example/english_app_cdcntt/service/impl/WordServiceImpl.java) — list, countDue, create, update, delete; kiểm tra trùng tên từ (chuẩn hoá lowercase/trim trước khi so sánh), ownership qua `findOwnedForUpdate` khoá pessimistic, mọi §6.2 check chạy trước mutation.
- [WordMapper.java](src/main/java/com/example/english_app_cdcntt/mapper/WordMapper.java), [WordForm.java](src/main/java/com/example/english_app_cdcntt/form/WordForm.java), [WordValueForm.java](src/main/java/com/example/english_app_cdcntt/form/WordValueForm.java), [WordDto.java](src/main/java/com/example/english_app_cdcntt/dto/WordDto.java), [WordValueDto.java](src/main/java/com/example/english_app_cdcntt/dto/WordValueDto.java), [DueCountResponse.java](src/main/java/com/example/english_app_cdcntt/dto/DueCountResponse.java), [SuccessResponse.java](src/main/java/com/example/english_app_cdcntt/dto/SuccessResponse.java).
- Exception: [DuplicateWordException.java](src/main/java/com/example/english_app_cdcntt/exception/DuplicateWordException.java), [OwnershipDeniedException.java](src/main/java/com/example/english_app_cdcntt/exception/OwnershipDeniedException.java), [InvalidRequestException.java](src/main/java/com/example/english_app_cdcntt/exception/InvalidRequestException.java) + 3 handler mới trong [GlobalExceptionHandler.java](src/main/java/com/example/english_app_cdcntt/exception/GlobalExceptionHandler.java): trùng từ → 409 CONFLICT, không sở hữu (hoặc word không tồn tại — không lộ sự tồn tại) → 403 FORBIDDEN, meaning id lạ/không thuộc word → 400.

### Sửa file cũ

- [WordValue.java](src/main/java/com/example/english_app_cdcntt/entity/WordValue.java): thêm `updateDetails(...)` — PUT ghi đè nghĩa tại chỗ, row giữ nguyên id để client theo dõi được (§6.2:284).
- [WordRepository.java](src/main/java/com/example/english_app_cdcntt/repository/WordRepository.java): thêm `findByUser_IdOrderByIdAsc` (`@EntityGraph` fetch meanings) cho GET list.

### Kiểm thử (mới: 20 unit + 8 IT)

- [WordServiceImplTest.java](src/test/java/com/example/english_app_cdcntt/service/impl/WordServiceImplTest.java) — 8 unit: chuẩn hoá tên từ, trùng tên, meaning id lạ/không thuộc word bị chặn trước khi đụng DB, rollback khi mutation, due-count đếm `nextReview <= now` (biên `==` được tính).
- [WordControllerTest.java](src/test/java/com/example/english_app_cdcntt/controller/WordControllerTest.java) — 12 MVC: JSON/status của cả 5 endpoint, 401 khi thiếu token, 400 validate, 409 trùng, 403 ownership; owner id luôn từ principal.
- [WordFlowIT.java](src/test/java/com/example/english_app_cdcntt/service/WordFlowIT.java) — 8 IT trên MySQL local (class không `@Transactional`, mỗi lần gọi service commit/rollback trong transaction thật riêng): flow create→update→delete với assert trực tiếp trên `words`/`word_values` (giữ id nghĩa khi sửa, xoá CASCADE sạch nghĩa); từ mới ngay lập tức rơi vào due-count, đếm đúng theo owner; trùng tên (kể cả khác hoa/th whitespace) → 409 và không đổi dữ liệu; meaning id của người khác → 400 và không lưu gì; **rollback toàn bộ khi lỗi DB thật** — cập nhật với nghĩa mới vượt VARCHAR(1000) gây MySQL error 1406, sau rollback từ + nghĩa giữ nguyên trạng thái; update/delete từ của người khác (và từ không tồn tại) → OwnershipDeniedException, dữ liệu còn nguyên.

### Kết quả kiểm tra

- `.\mvnw.cmd clean test`: **BUILD SUCCESS — Tests run: 174, Failures: 0, Errors: 0, Skipped: 0** (trước đợt: 154; +20 unit).
- `.\mvnw.cmd clean verify` với `TEST_DB_*` trỏ MySQL 8.0.43 local (lần đầu chạy IT ngay trong phiên): **BUILD SUCCESS — 174 unit + 29 IT, 0 failures / 0 errors** (15 RepositoryLayerIT + 6 schema IT + 8 WordFlowIT).
- JaCoCo `jacoco:check` đạt; instruction coverage 100% cho `WordServiceImpl`, `WordController`, `WordMapper`.

### Nợ còn lại

Không phát sinh nợ mới cho Phase 3. Nợ IT rotation/concurrency của Phase 2 vẫn ở Phase 7 như Đợt 8 đã ghi.

## Đợt 10 — Phase 4: AI adapter, sinh nghĩa và cache race (2026-09-30)

**Trạng thái: ĐÃ NGHIỆM THU (2026-10-01) — 214 unit (`mvnw test`) + 32 IT trên MySQL local (`clean verify` BUILD SUCCESS do người dùng chạy): 6 schema IT + 15 RepositoryLayerIT + 8 WordFlowIT + 3 WordGenerateFlowIT, 0 failures / 0 errors.**

Phạm vi: `GET /api/words/generate?english=...` theo §4.1/§4.3 — chuẩn hoá + validate tên từ → tra cache global (`word_cache`/`word_cache_values`) → MISS thì gọi LLM (OpenAI-compatible `/v1/chat/completions`, JSON Schema strict trong prompt, response parse chặt) → lưu cache trong transaction mới → trả `GeneratedWordDto`; HIT hoặc thắng race re-read thì không gọi AI.

- `config/AiProperties.java`, `config/AiClientConfig.java` — properties `app.ai.*` (baseUrl/apiKey/model/timeout) validate lúc startup; RestClient kèm Authorization Bearer, header không log.
- `service/AiClient.java` — interface + record `GeneratedMeaning`/`MeaningItem`; `service/impl/LlmAiClient.java` — đọc response dạng String rồi parse bằng Jackson 3 (`tools.jackson`) để không phụ thuộc converter; mọi sai lệch schema (validWord không boolean, level lạ, số nghĩa ≠ 1–3, field rỗng/độ dài vượt, partOfSpeech lạ, content không phải JSON) → `AiServiceException` (502, message cố định "Dịch vụ AI tạm thời không khả dụng", detail chỉ vào log warn §8.2). Jackson 3 exception là unchecked — bắt `tools.jackson.core.JacksonException`, không phải Jackson 2.
- `exception/AiServiceException.java` — `MESSAGE` cố định cho client, `detail` riêng cho log; `exception/GlobalExceptionHandler.java` — thêm 502 AiServiceException + 400 cho `InvalidRequestException`/`MissingServletRequestParameterException` (thiếu `english`).
- `mapper/WordCacheMapper.java` + `service/WordCacheTxService(Impl).java` + `service/GenerateService(Impl).java` — `saveNew` chạy `Propagation.REQUIRES_NEW` trong `try/catch DataIntegrityViolationException` (đuôi rùa `s` chuẩn hoá về "s" trước khi so UNIQUE): rà soát lại bằng `findCached` — chỉ trả kết quả khi re-read thật sự thấy row, không bao giờ tự bịa cache-hit; race vẫn thua → 502.
- `controller/WordController.java` — endpoint thứ 6 theo §4.1: 200, 400 tên từ lỗi/thiếu param, 401, 502 khi AI lỗi. Không thêm message mới ngoài §6.
- Test: `LlmAiClientTest` (16 unit — MockRestServiceServer: happy path, non-word passthrough, 429/500/timeout, envelope lỗi, 9 ca schema strict trong đó biên 1000 ký tự chữ Hán NFD dài gấp đôi byte), `GenerateServiceImplTest` (18 unit — HIT không gọi AI, MISS gọi AI, race, normalize, cụm từ 1–5 từ), `WordControllerTest` (+5 MVC case), `WordGenerateFlowIT` (3 IT DB thật: miss→hit với cụm từ "make up …" chỉ 1 lần gọi AI + đúng 1 row, 8 luồng đồng thời đúng 1 row và mọi caller nhận cùng kết quả — AI được hỏi 1..n lần vì khử trùng ở DB, non-word không đụng AI/cache).

Bài học kỹ thuật: chuỗi builder `putObject(...).put(...)` kết thúc ở node con nên `.toString()` chỉ in node con — gán root ra biến rồi `root.toString()`; `.body(JsonNode.class)` của RestClient phụ thuộc converter nên với classpath lẫn Jackson 2+3 phải đọc `String` rồi tự parse; Boot 4 không tự cấp bean `RestClient.Builder` trong context test (context fail toàn bộ IT) — build `RestClient.builder()` trực tiếp trong `AiClientConfig`; key cache trong IT phải khớp regex từ điển (RUN_ID base-36 chứa chữ số → dùng chữ cái thuần); trong race, số lần gọi AI là 1..n (khử trùng ở DB), đừng assert "đúng 1 lần gọi AI".

## Các đợt tiếp theo

- [x] **Phase 0.2:** cấu hình application, properties được validate, Clock UTC và unit tests cấu hình.
- [x] **Phase 0.3:** migration V1, test profile và schema tests; người dùng xác nhận `clean verify` đạt BUILD SUCCESS trên MySQL local sau sửa assertion CHECK. Kiểm chứng runtime JDK 21 vẫn còn riêng.
- [x] **Phase 1: ĐÃ NGHIỆM THU (2026-09-29)** — entity/repository/enum/auditing, EntityBusinessRulesTest (28 unit test) và RepositoryLayerIT (15 IT) hoàn tất; người dùng chạy `clean verify` local với MySQL đạt BUILD SUCCESS: 75 unit + 21 IT, 0 failures / 0 errors (mục Đợt 4).
- [x] **Phase 2: ĐÃ NGHIỆM THU (2026-09-29 20:47)** — auth/security, 4 endpoint và rotation; 143 unit testcase 0 lỗi, coverage `service` 100% / `service.tx` 97,6%; người dùng chạy `clean verify` đầy đủ trên MySQL local: 21 IT pass (15 RepositoryLayerIT + 6 schema IT), 0 failures / 0 errors. 21 IT này cũng là lần chạy lại trên layout package đã chuẩn hóa của Đợt 6. Audit DoD §11 ở Đợt 8 phát hiện 4 hạng mục còn thiếu: BCrypt cost 12 và unit test BCrypt, test filter/security chain (đã bù, tổng 154 test), IT rotation/concurrency (chuyển Phase 7).
- [x] **Phase 3: ĐÃ NGHIỆM THU (2026-09-30)** — words CRUD, ownership bản ghi con và due-count; 174 unit + 29 IT (8 WordFlowIT trên MySQL local) 0 lỗi, chạy `clean verify` đầy đủ trong phiên (mục Đợt 9).
- [x] **Phase 4: ĐÃ NGHIỆM THU (2026-10-01)** — AI adapter, sinh nghĩa và cache race; 214 unit + 32 IT (trong đó 3 WordGenerateFlowIT trên MySQL local) 0 lỗi, chạy `clean verify` đầy đủ (mục Đợt 10). Nghiệm thu kèm thay đổi yêu cầu: nhận **cụm từ** 1–5 từ (make up, get along with) — prompt + regex + message 400 + docs §4.1/§7 đã cập nhật cùng lượt.
- [ ] **Phase 5:** grading và phrases.
- [ ] **Phase 6:** review SRS và cleanup.
- [ ] **Phase 7:** integration end-to-end, tài liệu API và vận hành backend. Bao gồm nợ DoD Phase 2: IT chứng minh row refresh bị xoá thật (commit), token mới khác token cũ trong cùng giây, token cũ dùng lại thất bại, hai refresh đồng thời chỉ một thành công (§5.2:243).

Không triển khai Android. Đợt 1 chưa tạo endpoint, entity, migration, hoặc cấu hình production.

Lưu ý: workspace hiện **đã là Git repository** (init từ trước, commit gần nhất `8a0ca59` — Phase 2); ghi chú "không phải Git repository" trước đây của Đợt 8 đã lỗi thời, từ Đợt 9 mỗi đợt đóng bằng một commit riêng.
