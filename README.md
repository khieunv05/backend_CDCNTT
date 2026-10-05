# LEnglish — english_app_cdcntt

Backend **sổ tay từ vựng tiếng Anh có chấm điểm đoạn văn bằng AI**, triển khai theo đặc tả
trong `KE_HOACH_TRIEN_KHAI.md` (§4 API, §5 Bảo mật, §6 Nghiệp vụ, §7 Tích hợp AI,
§13 Kiến trúc, §14 Bảo mật vận hành). API chi tiết: xem [`API_REFERENCE.md`](API_REFERENCE.md).

## 1. Tính năng

- **Từ vựng:** thêm/sửa/xóa từ với nhiều nghĩa, sinh nghĩa tự động bằng AI (1–5 từ/lần, gồm cụm động từ),
  tra cứu từ điển nội bộ trước khi gọi AI; sổ từ theo từng user.
- **Ôn tập:** lịch SRS bậc thang `{1, 3, 7, 14, 30}` ngày; ôn theo batch (tối đa 500, gộp id trùng,
  rollback toàn batch nếu có từ không thuộc sở hữu); đếm từ đến hạn.
- **Đoạn văn:** gửi đoạn 10–5000 ký tự, AI chấm điểm 0–10 + trả danh sách lỗi
  (câu sai → câu đúng → giải thích); lưu nguyên tố trong một transaction.
- **Bảo mật:** JWT access 30 phút + refresh 7 ngày lưu DB (băm? không — chuỗi raw trong `refresh_tokens`,
  single-use, xóa khi logout), mật khẩu BCrypt, mọi truy vấn luôn scope theo user lấy từ JWT (§5.3).
- **Vận hành:** job dọn token hết hạn chạy 03:00 UTC hằng ngày (§12), Flyway quản lý schema,
  `ddl-auto: validate` không cho Hibernate đụng vào schema.

## 2. Công nghệ

| Thành phần | Phiên bản |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 (Spring Framework 7) |
| MySQL | 8.x (`mysql-connector-j`) |
| Flyway | migration `src/main/resources/db/migration` |
| JJWT | 0.12.6 (`jjwt-api`/`jjwt-impl`/`jjwt-jackson`) |
| Test | JUnit 5, Mockito, AssertJ, RestTestClient; surefire (unit) + failsafe (`*IT`, jacoco 0.8.13) |

## 3. Chạy dự án

### 3.1. Chuẩn bị MySQL

```sql
CREATE DATABASE lenglish CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'lenglish_app'@'localhost' IDENTIFIED BY '<mật khẩu app>';
GRANT ALL PRIVILEGES ON lenglish.* TO 'lenglish_app'@'localhost';
```

### 3.2. Biến môi trường bắt buộc

| Biến | Ý nghĩa |
|---|---|
| `DB_URL` | ví dụ `jdbc:mysql://localhost:3306/lenglish?serverTimezone=UTC&connectionTimeZone=UTC` |
| `DB_USERNAME` / `DB_PASSWORD` | tài khoản app (không dùng root) |
| `JWT_SECRET` | khóa HS256, **≥ 64 ký tự** (§14) |
| `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL` | endpoint chat-completions tương thích OpenAI |

### 3.3. Khởi động

```bash
./mvnw spring-boot:run   # hoặc: ./mvnw clean package && java -jar target/*.jar
```

Ứng dụng chạy tại `http://localhost:8080`, mọi endpoint dưới tiền tố `/api`
(xem [`API_REFERENCE.md`](API_REFERENCE.md)). Flyway tự tạo/bản vá schema lúc khởi động.

## 4. Kiểm thử

Hai tầng, tách theo §13.8:

- **Surefire (unit):** `./mvnw test` — toàn bộ test thường (`*Test`), chạy mà **không cần MySQL**.
- **Failsafe (integration):** `./mvnw verify` — các `*IT` (`*FlowIT`, `RepositoryLayerIT`, `ApiEndToEndIT`)
  chạy trên MySQL thật. Đặt biến môi trường trước (chi tiết: [`HUONG_DAN_TEST_MYSQL.md`](HUONG_DAN_TEST_MYSQL.md)):

  | Biến | Giá trị |
  |---|---|
  | `TEST_DB_URL` | `jdbc:mysql://localhost:3306/lenglish_test?serverTimezone=UTC&connectionTimeZone=UTC` |
  | `TEST_DB_USERNAME` | `lenglish_test` (bắt buộc, không được là `root`) |
  | `TEST_DB_PASSWORD` | mật khẩu tài khoản test |

  `support/MySqlTestConfiguration` tự xác thực các biến trên và hủy chạy nếu sai/missing.
  `ApiEndToEndIT` khởi động toàn bộ app trên port ngẫu nhiên rồi bắn HTTP request theo đúng
  thứ tự nghiệp vụ (đăng ký → từ → ôn → đoạn văn → logout), stub `AiClient` bằng Mockito.

Báo cáo coverage JaCoCo: `target/site/jacoco/index.html` (unit) và
`target/site/jacoco-it/index.html` (integration).

## 5. Cấu trúc mã nguồn

```
src/main/java/com/example/english_app_cdcntt/
  config/      SecurityConfig, JwtAuthenticationFilter, JwtService, JwtProperties,
               AiProperties, AiClientConfig, CleanupProperties, AppUserDetailsService,
               UserPrincipal, TokenType, RestAuthenticationEntryPoint, RestAccessDeniedHandler,
               SecurityErrorResponses, ApplicationPropertiesConfig, ClockConfig,
               JpaAuditingConfig, SchedulingConfig, SecretValue
  controller/  AuthController, WordController, PhraseController, GlobalErrorController (/error)
  service/     AuthService, WordService, PhraseService, GenerateService, GradingService,
               AiClient (giao diện tích hợp AI), WordCacheTxService, PhraseTxService,
               TokenCleanupService, SrsIntervals
               + impl/ (AuthServiceImpl, WordServiceImpl, PhraseServiceImpl,
               GenerateServiceImpl, GradingServiceImpl, LlmAiClient,
               WordCacheTxServiceImpl, PhraseTxServiceImpl)
               + tx/ (RegistrationTxService, RefreshTokenTxService — transaction biên)
  repository/  UserRepository, WordRepository, WordValueRepository, WordCacheRepository,
               WordCacheValueRepository, PhraseRepository, GrammarErrorRepository,
               RefreshTokenRepository
  entity/      User, Word, WordValue, WordCache, WordCacheValue, Phrase, GrammarError,
               RefreshToken
  dto/ form/ mapper/ enums/ exception/ (GlobalExceptionHandler, ErrorResponse)
src/test/java/com/example/english_app_cdcntt/
  unit: *Test          — service/logic thuần
  IT:  *FlowIT, RepositoryLayerIT, ApiEndToEndIT — MySQL thật qua failsafe
  support/MySqlTestConfiguration — kiểm tra env TEST_DB_*
```

## 6. Tài liệu liên quan

- [`KE_HOACH_TRIEN_KHAI.md`](KE_HOACH_TRIEN_KHAI.md) — đặc tả gốc 14 mục.
- [`API_REFERENCE.md`](API_REFERENCE.md) — 14 endpoint: body, response, mã lỗi, ví dụ curl.
- [`TIEN_DO_TRIEN_KHAI.md`](TIEN_DO_TRIEN_KHAI.md) — nhật ký tiến độ từng đợt.
- [`HUONG_DAN_TEST_MYSQL.md`](HUONG_DAN_TEST_MYSQL.md) — dựng MySQL cho tầng IT.
