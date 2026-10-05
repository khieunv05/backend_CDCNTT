# KẾ HOẠCH TRIỂN KHAI BACKEND — LEnglish

> **Phạm vi:** chỉ triển khai backend trong repository `english_app_cdcntt`.
> Tài liệu dựa trên bộ diagram tại `D:\bao_cao_LEnglish\docs\` và các quyết định kỹ thuật được ghi rõ bên dưới.
> Không triển khai Android, giao diện, widget, thông báo trên thiết bị, xử lý văn bản được chọn hoặc OCR trong giai đoạn này.
> Backend được phát triển và nghiệm thu độc lập bằng HTTP client và automated tests; không phụ thuộc việc có ứng dụng client.
> Đây là kế hoạch, không phải mô tả các chức năng đã được viết hoặc đã chạy kiểm thử thành công.

---

## 1. PHẠM VI VÀ KIẾN TRÚC BACKEND

### 1.1. Công nghệ và hiện trạng

- Spring Boot **4.1.1**, Java **21**, Spring Security **7**, Spring Data JPA / Hibernate **7**, MySQL **8.0.43** (Windows/local, theo môi trường đã xác nhận).
- Flyway quản lý schema; `spring.jpa.hibernate.ddl-auto=validate`; không dùng Hibernate để cập nhật schema tự động.
- JWT dùng jjwt **0.12.6**; mật khẩu hash bằng `BCryptPasswordEncoder(12)`.
- AI bên ngoài được gọi qua HTTPS từ backend; secret lấy từ environment.
- Base package: `com.example.english_app_cdcntt`.
- Hiện trạng khi lập kế hoạch: skeleton Spring Boot, đã có starter JPA/security/validation/webmvc, MySQL driver, Lombok và test starters; chưa có nghiệp vụ.

### 1.2. Trong phạm vi

1. Tài khoản: đăng ký, đăng nhập, đăng xuất, refresh token rotation.
2. Sổ từ: xem/thêm/sửa/xóa từ và nhiều nghĩa; kiểm tra quyền sở hữu.
3. Sinh nghĩa: AI cache-first, cache dùng chung nhưng tách biệt sổ từ cá nhân.
4. Ôn tập: API đếm từ đến hạn và xác nhận ôn theo lịch SRS.
5. Đoạn văn: xem/thêm/xóa; chấm AI trước khi lưu; lưu điểm, câu sửa và lỗi ngữ pháp.
6. Scheduler dọn refresh token hết hạn.
7. Migration, bảo mật, cấu hình, kiểm thử và hướng dẫn chạy/triển khai backend.
8. Thêm từ theo chủ đề (act-20): AI xác nhận chủ đề, sinh 10 từ mới tái dùng word_cache và lưu thẳng vào sổ từ của user.

### 1.3. Ngoài phạm vi

- Mọi công việc triển khai Android/client, UI, lưu token phía thiết bị, refresh interceptor, thông báo, widget, OCR, camera và quyền thiết bị.
- Không có endpoint upload ảnh/OCR, đăng ký thiết bị hoặc gửi push notification.
- Không triển khai quên mật khẩu, đăng nhập mạng xã hội, vai trò quản trị hoặc cập nhật đoạn văn vì chưa thuộc thiết kế hiện tại.
- `GET /api/words/due-count` vẫn thuộc backend: đây là API dữ liệu độc lập, không phải công việc triển khai widget.

### 1.4. Quy tắc kiến trúc

- Controller mỏng → Service → Repository; constructor injection; không trả entity ra HTTP.
- Form/DTO dùng `record`; mapper là class tĩnh; enum lưu bằng `EnumType.STRING`.
- Transaction đặt ở service xử lý DB. **Không đặt transaction bao trùm cuộc gọi AI/HTTP**; service điều phối có thể không có `@Transactional`.
- Mapping entity → DTO và truy cập collection LAZY phải hoàn tất trong transaction đọc; `open-in-view=false`.
- Dùng skills tương ứng khi triển khai: `layered-architecture`, `spring-security-jwt`, `spring-data-jpa`, `flyway-migrations`, `transactional-patterns`, `configuration-properties`, `rest-api-conventions`, `testing-pyramid`.
- Contract trong §4–§9 là chuẩn nghiệm thu backend. Những chỗ cụ thể hóa/khác diagram được ghi tại §13, không ngầm coi diagram đã quy định.

---

## 2. CƠ SỞ DỮ LIỆU — 8 BẢNG

### 2.1. Quy ước chung

- MySQL 8.0.43 / InnoDB; charset `utf8mb4`; ID `BIGINT AUTO_INCREMENT`.
- Các cột thời gian dùng `DATETIME(6)`, lưu UTC; entity/DTO dùng `Instant`; JSON ISO-8601 có hậu tố `Z`.
- Inject một `Clock` UTC; mỗi thao tác lấy một mốc `now`, chuẩn hóa đến microsecond trước khi tính/lưu/so sánh. Test dùng clock cố định.
- JPA Auditing dùng cùng nguồn thời gian; không trộn `NOW()` của DB với thời gian ứng dụng trong logic SRS.
- `english` dùng `strip().toLowerCase(Locale.ROOT)` thống nhất trước lookup, kiểm tra trùng và lưu ở cả `words` và `word_cache`; không lưu phiên bản chữ hoa riêng trong giai đoạn này.
- `username` phân biệt hoa/thường, không tự trim hoặc lowercase. Cột `username` và `english` dùng collation `utf8mb4_0900_bin` để khóa unique có hành vi xác định.

### 2.2. Schema mục tiêu

```text
users(
  id BIGINT PK,
  username VARCHAR(50) UNIQUE NOT NULL,
  password VARCHAR(60) NOT NULL
)

words(
  id BIGINT PK, user_id BIGINT FK→users NOT NULL,
  english VARCHAR(255) NOT NULL, level VARCHAR(2) NULL,
  review_count INT NOT NULL DEFAULT 0 CHECK(review_count >= 0),
  next_review DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  UNIQUE(user_id, english)
)

word_values(
  id BIGINT PK, word_id BIGINT FK→words NOT NULL ON DELETE CASCADE,
  vietnamese VARCHAR(1000) NOT NULL,
  example VARCHAR(2000) NULL, example_translation VARCHAR(2000) NULL,
  pronunciation VARCHAR(255) NULL, part_of_speech VARCHAR(20) NULL
)

word_cache(
  id BIGINT PK, english VARCHAR(255) UNIQUE NOT NULL,
  level VARCHAR(2) NOT NULL,
  created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL
)

word_cache_values(
  id BIGINT PK, word_cache_id BIGINT FK→word_cache NOT NULL ON DELETE CASCADE,
  vietnamese VARCHAR(1000) NOT NULL,
  example VARCHAR(2000) NOT NULL, example_translation VARCHAR(2000) NOT NULL,
  pronunciation VARCHAR(255) NOT NULL, part_of_speech VARCHAR(20) NOT NULL
)

phrases(
  id BIGINT PK, user_id BIGINT FK→users NOT NULL,
  text TEXT NOT NULL, corrected_text TEXT NOT NULL,
  score INT NOT NULL CHECK(score BETWEEN 0 AND 10),
  created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL
)

grammar_errors(
  id BIGINT PK, phrase_id BIGINT FK→phrases NOT NULL ON DELETE CASCADE,
  incorrect TEXT NOT NULL, correction TEXT NOT NULL, explanation TEXT NOT NULL
)

refresh_tokens(
  id BIGINT PK, user_id BIGINT FK→users NOT NULL,
  token VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin UNIQUE NOT NULL,
  expiry_date DATETIME(6) NOT NULL, created_at DATETIME(6) NOT NULL
)
```

- Mọi PK ở trên là AUTO_INCREMENT; tên constraint/index phải rõ ràng trong migration.
- `word_cache` không có `user_id`. Không có FK giữa cache và sổ từ cá nhân. Cache là kho dữ liệu do AI sinh ra, dùng chung và tích lũy vĩnh viễn; không có job dọn cache theo thời gian.
- `word_values` suy ra quyền sở hữu qua `words.user_id`; `grammar_errors` qua `phrases.user_id`.
- Giới hạn đầu vào/đầu ra: phrase `text` 10–5000 ký tự; `correctedText` tối đa 10000; mỗi trường grammar error tối đa 5000; tối đa 100 lỗi/phrase. Bảo đảm kích thước UTF-8 vẫn phù hợp TEXT.
- Word có 1–20 nghĩa; AI sinh 1–3 nghĩa. Các trường chuỗi trong form/result không vượt kích thước cột tương ứng.
- Tạo index `(user_id, next_review)` trên words, `expiry_date` trên refresh_tokens, FK index trên các bảng con và `phrases.user_id`.
- Không tạo index trùng với index do UNIQUE/FK đã cung cấp; UNIQUE `(user_id, english)` đã hỗ trợ truy vấn theo `user_id`.

---

## 3. ENUM, PACKAGE VÀ ENTITY

```java
public enum Level { A1, A2, B1, B2, C1, C2 }
public enum PartOfSpeech { NOUN, VERB, ADJECTIVE, ADVERB, PREPOSITION, CONJUNCTION, PRONOUN, INTERJECTION }
```

```text
com.example.english_app_cdcntt
├── controller/    AuthController, WordController, PhraseController, GlobalErrorController (/error)
├── form/          RegisterForm, LoginForm, LogoutForm, RefreshForm,
│                  WordForm, WordValueForm, ReviewForm, PhraseForm
├── dto/           AuthResponse, MessageResponse, ValidationErrorResponse,
│                  SuccessResponse<T>, ErrorResponse, FieldErrorDetail,
│                  WordDto, WordValueDto, GeneratedWordDto,
│                  DueCountResponse, ReviewResultResponse, PhraseDto, GrammarErrorDto
├── service/       AuthService, WordService, PhraseService, GradingService,
│                  GenerateService, TokenCleanupService, SrsIntervals,
│                  AiClient, WordCacheTxService, PhraseTxService
├── service/impl/  AuthServiceImpl, WordServiceImpl, PhraseServiceImpl,
│                  GenerateServiceImpl, GradingServiceImpl, LlmAiClient,
│                  WordCacheTxServiceImpl, PhraseTxServiceImpl
├── service/tx/    RegistrationTxService, RefreshTokenTxService, RefreshRotationResult
├── mapper/        WordMapper, WordCacheMapper, PhraseMapper
├── repository/    8 JpaRepository tương ứng 8 entity
├── entity/        User, Word, WordValue, WordCache, WordCacheValue,
│                  Phrase, GrammarError, RefreshToken
├── config/        SecurityConfig, JwtService, JwtAuthenticationFilter,
│                  JwtProperties, AiProperties, CleanupProperties,
│                  AiClientConfig, JpaAuditingConfig, SchedulingConfig, ClockConfig,
│                  ApplicationPropertiesConfig, AppUserDetailsService, UserPrincipal,
│                  TokenType, SecretValue, RestAuthenticationEntryPoint,
│                  RestAccessDeniedHandler, SecurityErrorResponses
├── exception/     Các exception nghiệp vụ + GlobalExceptionHandler
└── enums/         Level, PartOfSpeech
```

*(Cập nhật sau đợt audit nghiệm thu để phản ánh bố cục thực tế: `WordCacheTxService`/`PhraseTxService` là interface trong `service/` + impl trong `service/impl/`; `service/tx/` giữ transaction biên đăng ký và refresh-rotation.)*

- `ManyToOne(fetch=LAZY)` cho quan hệ đến cha. `OneToMany(mappedBy=..., cascade=ALL, orphanRemoval=true)` cho Word→WordValue, WordCache→WordCacheValue, Phrase→GrammarError.
- Helper add/remove đồng bộ cả hai chiều quan hệ; khóa ngoại nằm trên bảng con, không tạo join table ngoài ERD.
- Khai báo length/nullability/columnDefinition phù hợp §2; `ddl-auto=validate` phải khớp migration.
- Không dùng Lombok `@Data` trên entity; ưu tiên `@Getter`, constructor protected và method nghiệp vụ.
- Fetch collection cần trả qua `@EntityGraph(attributePaths=...)` hoặc JOIN FETCH; tránh N+1.
- Service transaction tách bean để proxy hoạt động; không dựa vào self-invocation để mở transaction mới.

---

## 4. REST API — CONTRACT CHUNG

- Prefix `/api`, JSON UTF-8, không thêm `/v1` trong giai đoạn này.
- Protected endpoint dùng `Authorization: Bearer <accessToken>`; lấy `userId` từ principal, không nhận quyền sở hữu từ request body.
- Auth endpoint không yêu cầu access token. Nếu request chủ động gửi Bearer sai/hết hạn, filter vẫn trả 401; khi gọi auth độc lập phải bỏ header Bearer không hợp lệ.
- Lỗi bảo vệ endpoint: **401** `{"message":"Token không hợp lệ hoặc hết hạn"}`.
- Response chỉ có thông báo dùng `MessageResponse(String message)`.
- POST/PUT word và POST phrase dùng **`SuccessResponse<T>(String message, T data)`**; không thêm message vào DTO dữ liệu.
- GET danh sách trả JSON array trực tiếp; auth/generate/due-count/review trả DTO trực tiếp, không bọc envelope.
- Lỗi validation chung: `{"message":"Thông tin không hợp lệ","details":[{"field":"...","message":"..."}]}`. Không trả rejected value của password/token.
- JSON sai cú pháp/sai kiểu, thiếu body, enum không hợp lệ → 400 validation chung. Bean Validation của login và phrase có mapping riêng ở §5/§8.
- Tài nguyên word/phrase **không tồn tại hoặc không thuộc sở hữu đều trả 403** theo sequence. Không thêm nhánh 404 nghiệp vụ cho sửa/xóa các tài nguyên này.
- `expiresIn` là số giây sống của access token, không phải timestamp.

Ví dụ response tạo từ:

```json
{
  "message": "Thêm từ thành công",
  "data": {
    "id": 1,
    "english": "hello",
    "level": "A1",
    "reviewCount": 0,
    "nextReview": "2026-01-01T00:00:00Z",
    "createdAt": "2026-01-01T00:00:00Z",
    "updatedAt": "2026-01-01T00:00:00Z",
    "values": [{"id": 1, "vietnamese": "xin chào", "example": null, "exampleTranslation": null, "pronunciation": null, "partOfSpeech": null}]
  }
}
```

### 4.1. Danh sách 15 endpoint

| # | Endpoint | Thành công | Lỗi nghiệp vụ chính |
|---|---|---|---|
| 1 | POST `/api/auth/register` | 201 MessageResponse: `Tạo tài khoản thành công` | 400 validation; 409 `Tên đăng nhập đã tồn tại` |
| 2 | POST `/api/auth/login` | 200 AuthResponse | 401 `Đăng nhập thất bại` |
| 3 | POST `/api/auth/logout` | 200 `Đăng xuất thành công` | 400 request không hợp lệ |
| 4 | POST `/api/auth/refresh` | 200 AuthResponse mới | 401 `Đăng nhập thất bại` hoặc `Phiên đăng nhập hết hạn` |
| 5 | GET `/api/words` | 200 List<WordDto> | 401 |
| 6 | GET `/api/words/due-count` | 200 DueCountResponse | 401 |
| 7 | GET `/api/words/generate?english=...` | 200 GeneratedWordDto | 400 `Từ hoặc cụm từ gửi lên không phải một từ tiếng Anh hợp lệ` — nhận 1–5 từ (make up, get along with), lower-case + gộp khoảng trắng; 502 lỗi AI |
| 8 | POST `/api/words` | 201 SuccessResponse<WordDto>: `Thêm từ thành công` | 400; 409 `Từ này đã có trong sổ của bạn` |
| 9 | PUT `/api/words/{id}` | 200 SuccessResponse<WordDto>: `Sửa từ thành công` | 400; 403 `Không có quyền sửa từ này`; 409 trùng từ |
| 10 | DELETE `/api/words/{id}` | 200 `Xóa từ thành công` | 403 `Không có quyền xóa từ này` |
| 11 | POST `/api/words/review` | 200 ReviewResultResponse | 400; 403 `Không có quyền với từ không thuộc sở hữu` |
| 12 | GET `/api/phrases` | 200 List<PhraseDto> | 401 |
| 13 | POST `/api/phrases` | 201 SuccessResponse<PhraseDto>: `Thêm đoạn văn thành công` | 400 `Đoạn văn gửi lên không hợp lệ`; 502 lỗi AI |
| 14 | DELETE `/api/phrases/{id}` | 200 `Xóa đoạn văn thành công` | 403 `Không có quyền xóa đoạn văn này` |
| 15 | POST `/api/words/generate-topic` | 201 SuccessResponse<List<WordDto>>: `Thêm từ theo chủ đề thành công` — data là các từ vừa thêm vào sổ | 400 `Chủ đề không hợp lệ, vui lòng nhập lại`; 502 lỗi AI |

Tất cả endpoint protected còn có nhánh 401 chung. Lỗi AI luôn dùng message `Dịch vụ AI tạm thời không khả dụng`.

---

## 5. AUTH VÀ SECURITY — act/seq-01..04

### 5.1. Form và validation

- Register/Login: `{username, password}`. Username 3–50 ký tự, regex `[A-Za-z0-9_.]+`, không blank.
- Password không blank, ít nhất 8 ký tự theo Bean Validation và **tối đa 72 byte UTF-8**; cần custom validator, không coi `@Size(max=72)` là kiểm tra đủ. Không trim/normalize password.
- Register constraint fail → 400 validation chung. Login constraint fail hoặc sai username/password → **401 `Đăng nhập thất bại`**, không tiết lộ trường sai.
- Logout/Refresh: `{refreshToken}` không blank, tối đa 2048 ký tự ASCII. Logout form sai → 400; refresh constraint fail → 401 `Đăng nhập thất bại`. JSON hỏng vẫn áp dụng 400 chung.
- `AuthResponse(String accessToken, String refreshToken, long expiresIn)`.

### 5.2. Luồng nghiệp vụ và transaction

- **Register:** kiểm tra username → BCrypt → lưu trong transaction ngắn. UNIQUE là lớp chặn cuối; race trùng username cũng trả 409. Catch lỗi unique bên ngoài transaction ghi đã rollback, chỉ ánh xạ đúng constraint.
- **Login:** xác thực mật khẩu → phát token → lưu refresh token; chỉ trả thành công sau commit.
- **JWT:** HS256; claims `sub=username`, `uid=userId`, `type=access|refresh`, `iat`, `exp`, **`jti` ngẫu nhiên mỗi token**. Secret Base64 decode được ít nhất 32 byte; fail startup nếu thiếu/yếu.
- **Logout:** xóa refresh token đúng chuỗi trong transaction; token không còn trong DB vẫn 200. Không thu hồi access token stateless; token này còn hiệu lực tối đa đến expiry.
- **Refresh:** dùng bean `RefreshTokenTxService` và transaction ghi:
  1. Tìm theo chuỗi token chính xác bằng locking query `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`). Không có row → kết quả INVALID.
  2. Nếu `expiryDate <= now`: xóa row, trả kết quả EXPIRED từ transaction để **commit xóa**. Service điều phối chỉ ném `TokenExpiredException` sau khi transaction kết thúc.
  3. Với row còn hạn: kiểm tra chữ ký, `type=refresh`, subject/uid khớp user trong DB, expiry JWT và DB nhất quán. Sai → INVALID, không phát token.
  4. Hợp lệ: xóa cũ, tạo cặp mới có jti khác, lưu refresh mới trong cùng transaction; trả kết quả SUCCESS sau commit.
  5. Hai request đồng thời dùng cùng token: chỉ một request thành công, request còn lại nhận 401. Test bằng transaction/kết nối độc lập, không chỉ mock repository.
- Không sửa lỗi rollback bằng cách catch rồi tiếp tục dùng transaction đã hỏng; không blanket `noRollbackFor` cho mọi lỗi auth.

### 5.3. Security filter chain

- Security 7 lambda DSL, stateless; tắt CSRF cho API bearer thuần, không dùng cookie phiên đăng nhập.
- `permitAll` đúng 4 POST auth; các `/api/**` còn lại yêu cầu authenticated; đường dẫn khác mặc định deny trừ health endpoint tối thiểu được cấu hình khi triển khai.
- Filter chỉ chấp nhận `type=access`; xác thực token trước khi tin claims, load user và đối chiếu `uid` với DB.
- Filter không chuyển lỗi hạ tầng/exception từ controller thành lỗi credential.
- `AuthenticationEntryPoint`/`AccessDeniedHandler` trả JSON đúng contract; disable servlet registration trùng của filter bean.
- Không log password, JWT, Authorization hoặc AI API key. Không bật CORS wildcard mặc định; chỉ cấu hình origin nếu có nhu cầu riêng.

---

## 6. WORDS, DUE-COUNT VÀ REVIEW — act/seq-05..08, 19

### 6.1. Form/DTO

```java
record WordForm(String english, Level level, List<WordValueForm> values) {}
record WordValueForm(Long id, String vietnamese, String example,
                     String exampleTranslation, String pronunciation, PartOfSpeech partOfSpeech) {}
record WordDto(Long id, String english, Level level, Integer reviewCount,
               Instant nextReview, Instant createdAt, Instant updatedAt, List<WordValueDto> values) {}
record WordValueDto(Long id, String vietnamese, String example,
                    String exampleTranslation, String pronunciation, PartOfSpeech partOfSpeech) {}
record DueCountResponse(long dueCount) {}
record ReviewForm(List<Long> wordIds) {}
record ReviewResultResponse(int reviewedCount) {}
```

Các record trên mô tả shape; khi triển khai bổ sung `@Valid`, `@NotNull`, `@NotBlank`, `@Size` và constraint phần tử theo §2. ID phải dương. Reject phần tử null. English sau chuẩn hóa phải không rỗng, tối đa 255 ký tự; values có 1–20 phần tử, vietnamese không blank. Không nhận reviewCount/nextReview/userId từ form tạo/sửa.

### 6.2. Nghiệp vụ

- **GET list:** chỉ của user hiện tại, fetch values, map trong transaction đọc. Chốt thứ tự words theo `id ASC`, values theo `id ASC`. Chưa phân trang để giữ contract array hiện có.
- **Due-count:** `countByUserIdAndNextReviewLessThanEqual(userId, now)`; bằng `now` vẫn tính đến hạn.
- **POST:** reject mọi `values.id != null` bằng 400; kiểm tra trùng theo khóa chuẩn hóa → 409. Tạo `reviewCount=0`, `nextReview=createdAt=updatedAt=now`; lưu Word + toàn bộ values trong cùng transaction.
- **PUT:** lấy word trong transaction ghi với khóa hàng; không tồn tại/không thuộc user → 403 trước kiểm tra trùng từ.
  - Kiểm tra `english` trùng với word khác của user → 409.
  - Chỉ update ID nghĩa đã thuộc chính word này; ID không tồn tại/thuộc word khác hoặc lặp trong form → 400 validation chung, không trả thông tin chủ sở hữu của ID đó.
  - `id=null` → tạo mới; ID cũ bị bỏ → orphanRemoval. Validate toàn bộ trước khi thay đổi collection; cả thao tác atomic.
  - Giữ nguyên reviewCount và nextReview.
- **DELETE:** khóa/lấy word → không tồn tại/không thuộc user trả 403; xóa word và values cùng transaction.
- UNIQUE `(user_id, english)` xử lý cả race tạo/sửa; lỗi constraint đúng khóa trùng → 409 sau rollback, không chuyển mọi lỗi DB thành lỗi trùng.

### 6.3. Review SRS

- `wordIds` không rỗng, tối đa 500 phần tử trước distinct; ID dương, không null. Distinct trước truy vấn và đếm.
- Khóa các word của user theo tập ID, theo thứ tự ID tăng dần; dùng `PESSIMISTIC_WRITE` để tránh lost update. Cùng quy tắc khóa với PUT/DELETE.
- Số row tìm được khác số ID distinct → 403, rollback toàn bộ, không cập nhật một phần.
- Với mỗi word: tăng `reviewCount` một lần; `nextReview = now + interval`; `updatedAt=now`; lưu batch trong một transaction.

| reviewCount sau tăng | 1 | 2 | 3 | 4 | ≥5 |
|---|---|---|---|---|---|
| interval (ngày, mỗi ngày 24h) | 1 | 3 | 7 | 14 | 30 |
*(Bảng điều chỉnh theo yêu cầu user 2026-10-02: 1→3→7→14→30, thay bảng gốc 1/2/4/7/15/30.)*

API này **không idempotent**: hai request hợp lệ tuần tự là hai lần xác nhận ôn. Không tự động retry POST review khi không biết request trước đã commit hay chưa; idempotency key chưa thuộc giai đoạn này. Không bắt buộc word đang due mới được xác nhận.

---

## 7. SINH NGHĨA AI CACHE-FIRST — act/seq-09

- `GeneratedWordDto(String english, Level level, List<WordValueDto> values)`; values.id luôn null để không lộ/nhầm ID cache với ID sổ từ.
- Điều phối không có transaction dài:
  1. Chuẩn hóa english như §2: strip + **gộp khoảng trắng lặp thành 1** + lower-case. Thiếu/rỗng/quá 255 ký tự hoặc sai dạng → 400 `Từ hoặc cụm từ gửi lên không phải một từ tiếng Anh hợp lệ`.
  2. Kiểm tra hình thức bằng regex `^[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*){0,4}$` — nhận **1–5 từ** (cụm động từ/collocation: `make up`, `get along with`); dấu nối/nháy đơn nội bộ; đây không phải bằng chứng từ thực tồn tại.
  3. Đọc cache trong transaction đọc ngắn, fetch values và map DTO. HIT → trả ngay, không gọi AI.
  4. MISS → gọi AI ngoài transaction. `validWord=false` → 400; AI lỗi/schema sai → 502; cả hai không ghi cache.
  5. Dữ liệu hợp lệ → transaction mới lưu WordCache + values atomic; trả DTO sau commit. Key lấy từ request chuẩn hóa, không để AI tự đổi key.
- Race cùng key: coordinator bắt lỗi UNIQUE english **sau khi transaction ghi đã rollback**, rồi đọc bằng transaction mới. Không đọc lại trong persistence context đã lỗi. Nếu không có row hợp lệ để đọc lại thì báo lỗi hạ tầng, không giả thành cache-hit.
- Không cần khóa phân tán/single-flight ở giai đoạn này; hai MISS có thể cùng gọi AI nhưng chỉ một bản cache được lưu.
- Sinh nghĩa **không ghi** words/word_values. POST word chỉ lưu WordForm đã validate; không tự ghi đè form bằng cache.
- Cache chưa có TTL; việc bổ sung chính sách hết hạn là công việc sau.

### 7.1. Sinh 10 từ theo chủ đề — act-20

- `POST /api/words/generate-topic` (protected). Form `TopicForm(String topic)` — chuẩn hóa như §2 (strip + gộp khoảng trắng), không rỗng, tối đa 255 ký tự; sai ràng buộc → 400 validation chung.
- Điều phối không có transaction dài, theo thứ tự:
  1. Gọi AI xác nhận topic có phải một chủ đề có nghĩa. Sai → 400 `Chủ đề không hợp lệ, vui lòng nhập lại`, không gọi AI lần hai, không ghi dữ liệu.
  2. Đọc danh sách từ hiện có của user (khóa chuẩn hóa) trong transaction đọc ngắn, gửi kèm prompt để AI tránh trùng.
  3. Gọi AI sinh 10 từ theo chủ đề, mỗi từ đủ english, level, values (nghĩa, phiên âm, từ loại, ví dụ) theo shape `GeneratedWordDto`; AI lỗi/schema sai → 502, không ghi gì.
  4. Lọc trùng theo khóa chuẩn hóa — trong kết quả AI và với sổ từ hiện có: từ trùng bị bỏ qua, không hủy cả lô (chi tiết §13.12).
  5. Một transaction ghi duy nhất: với mỗi từ — MISS thì lưu WordCache + word_cache_values (HIT dùng lại cache sẵn có, không ghi đè), sau đó lưu Word vào sổ (`reviewCount=0`, `nextReview=createdAt=updatedAt=now` như §6.2 POST) + word_values; UNIQUE `(user_id, english)` vẫn là lớp chặn race cuối.
- 201 sau commit: `SuccessResponse<List<WordDto>>` với message `Thêm từ theo chủ đề thành công`, data là các từ thực sự thêm mới (≤10) theo shape WordDto của sổ từ — không phải GeneratedWordDto.
- Nhánh 400/502 không tạo cache cũng không tạo word. Khác seq-09, luồng này ghi thẳng words/word_values; client không cần gọi POST /api/words tiếp.

---

## 8. PHRASES VÀ TÍCH HỢP AI — act/seq-10..13

### 8.1. Form/DTO và lưu dữ liệu

```java
record PhraseForm(String text) {}
record PhraseDto(Long id, String text, String correctedText, Integer score,
                 Instant createdAt, List<GrammarErrorDto> errors) {}
record GrammarErrorDto(String incorrect, String correction, String explanation) {}
```

- `PhraseForm.text`: không blank, 10–5000 ký tự; lỗi constraint → 400 `Đoạn văn gửi lên không hợp lệ` (không bị handler chung đổi message).
- GET list chỉ của user, theo `id ASC`; fetch grammarErrors và map trong transaction đọc; errors theo `id ASC`.
- POST: validate input → chấm ngoài transaction → validate kết quả AI → `PhraseTxService` lưu phrase + errors trong transaction ngắn → trả 201 envelope sau commit.
- Nếu AI fail/timeout/schema sai thì không có row phrase hoặc grammar_error được lưu. Nếu DB fail thì rollback cả phrase và errors.
- DELETE: không tồn tại hoặc không thuộc sở hữu → 403 đúng message; hợp lệ thì xóa cascade.
- Không có PUT hoặc endpoint chấm riêng; chấm lại là một POST tạo bản ghi mới.

### 8.2. Contract nội bộ AI

```java
public interface AiClient {
    GeneratedMeaning generateWordMeaning(String english);
    GradingResult gradePhrase(String text);
}
record GeneratedMeaning(boolean validWord, Level level, List<MeaningItem> values) {}
record MeaningItem(String vietnamese, String example, String exampleTranslation,
                   String pronunciation, PartOfSpeech partOfSpeech) {}
record GradingResult(int score, String correctedText, List<GradingError> errors) {}
record GradingError(String incorrect, String correction, String explanation) {}
```

- `LlmAiClient` dùng `RestClient`; base-url/model/API key từ properties; connect/read timeout mặc định 10s/30s.
- Input người dùng là dữ liệu cần phân tích, không phải chỉ thị thay thế system prompt; giới hạn output size và số phần tử theo §2.
- Sinh nghĩa: LLM xác định `validWord`; false chỉ cần cờ này, không bắt buộc meaning fields. True yêu cầu level CEFR hợp lệ và 1–3 nghĩa đầy đủ, đúng enum/giới hạn cột.
- Chấm văn: backend kiểm tra độ dài/nội dung rỗng trước. Không dùng regex chỉ cho chữ cái để loại số, dấu câu, tên riêng trong câu hợp lệ. AI adapter dùng cờ `validPhrase` ở schema provider để nhận diện nội dung không phải đoạn tiếng Anh; false → `InvalidPhraseException` 400. Cờ này không cần xuất hiện trong DTO trả ra HTTP.
- AI trả JSON đúng schema; kiểm tra sự có mặt của field trước mapping primitive (không để score thiếu thành 0 mặc định); không coercion chuỗi thành số.
- Score phải là số nguyên 0..10; **không clamp**. Sai score/type/enum, thiếu field bắt buộc hoặc vượt giới hạn → 502, không lưu dữ liệu.
- CorrectedText không blank; errors có thể rỗng; incorrect/correction được phép chuỗi rỗng cho lỗi thiếu/thừa, explanation phải có nội dung tiếng Việt.
- Lỗi mạng/timeout/provider 429/5xx/JSON parse → `AiServiceException` 502; log loại lỗi/status/correlation ID, không log secret hoặc nguyên đoạn văn mặc định.
- Mặc định **không tự retry** cuộc gọi AI để tránh nhân chi phí. Không cache/lưu kết quả lỗi.
- Automated tests dùng mock/stub HTTP, không gọi AI thật hoặc cần API key thật. Gọi thử provider thật là smoke test thủ công riêng khi có cấu hình.

---

## 9. SCHEDULER VÀ EXCEPTION HANDLING

### 9.1. Dọn refresh token — act/seq-18

- `@Scheduled(cron="${app.cleanup.cron}", zone="${app.cleanup.zone}")`.
- Mặc định 03:00 UTC hằng ngày; dùng cùng Clock UTC như auth.
- Bulk delete `expiryDate <= now` bằng JPQL `@Modifying` trong transaction; trả/log số row bị xóa, không log chuỗi token.
- Chỉ xóa token hết hạn; task idempotent; không liên quan scheduler/notification trên thiết bị.

### 9.2. Exception mapping

| Exception/trường hợp | HTTP | Message |
|---|---|---|
| DuplicateUsernameException | 409 | Tên đăng nhập đã tồn tại |
| DuplicateWordException | 409 | Từ này đã có trong sổ của bạn |
| InvalidCredentialsException / refresh không hợp lệ | 401 | Đăng nhập thất bại |
| Access token thiếu/sai/hết hạn | 401 | Token không hợp lệ hoặc hết hạn |
| TokenExpiredException của refresh đã lưu | 401 | Phiên đăng nhập hết hạn |
| AccessDeniedException của word/phrase/review | 403 | Message riêng theo §4.1 |
| InvalidWordException | 400 | Từ hoặc cụm từ gửi lên không phải một từ tiếng Anh hợp lệ |
| InvalidPhraseException | 400 | Đoạn văn gửi lên không hợp lệ |
| AiServiceException | 502 | Dịch vụ AI tạm thời không khả dụng |
| Validation chung | 400 | Thông tin không hợp lệ + details[] |
| Lỗi không dự kiến | 500 | Lỗi hệ thống |

- `GlobalExceptionHandler` xử lý controller/service exceptions; lỗi filter do entry point/filter handler xử lý cùng JSON contract.
- Bean Validation login/refresh → 401 auth, phrase → 400 phrase, generate → 400 word; các form khác → 400 chung. Bao phủ cả validation method/query khi dùng Spring MVC method validation.
- JSON malformed/type mismatch dùng 400 chung. Framework errors như 404 route không tồn tại, 405, 415 phải giữ đúng status, không bị catch-all đổi thành 500.
- Không trả stack trace, SQL hoặc nội dung token ra HTTP. Không catch exception trong controller để điều khiển transaction.

---

## 10. DEPENDENCIES VÀ CẤU HÌNH

### 10.1. Dependencies cần thêm

- `io.jsonwebtoken:jjwt-api:0.12.6`; `jjwt-impl` và `jjwt-jackson` cùng version, scope runtime.
- `org.springframework.boot:spring-boot-starter-flyway`; `org.flywaydb:flyway-mysql`.
- Test dùng MySQL local 8.0.43, không cần Docker/Testcontainers dependencies. Import cấu hình datasource test có guard trước khi Flyway chạy.
- Database và tài khoản riêng `lenglish_test` tại localhost:3306; credentials qua `TEST_DB_*`, không dùng tài khoản root hoặc fallback credentials ứng dụng. Xem [hướng dẫn test local](HUONG_DAN_TEST_MYSQL.md).
- Dùng version quản lý bởi Boot BOM khi có; không sao chép version dependency Boot 3. Jackson của ứng dụng Boot 4 và module Jackson do jjwt dùng phải được kiểm tra bằng dependency tree và smoke test parse/sign JWT.
- Cấu hình JaCoCo plugin cho Java 21, report + check service line coverage ≥80% trong `verify`; không chỉ ghi mục tiêu mà thiếu plugin.
- Nếu đặt integration tests là `*IT`, cấu hình Maven Failsafe `integration-test` + `verify`; Surefire chạy unit/slice `*Test`.

### 10.2. application.yml mục tiêu

```yaml
spring:
  application.name: english_app_cdcntt
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate.ddl-auto: validate
    open-in-view: false
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
app:
  jwt:
    secret: ${JWT_SECRET}
    access-token-expiration: 30m
    refresh-token-expiration: 7d
  ai:
    base-url: ${AI_BASE_URL}
    api-key: ${AI_API_KEY}
    model: ${AI_MODEL}
    connect-timeout: 10s
    read-timeout: 30s
  cleanup:
    cron: "0 0 3 * * *"
    zone: UTC
logging.level.com.example.english_app_cdcntt: INFO
```

- Nhóm cấu hình bằng record `@ConfigurationProperties` + `@Validated`; Duration có đơn vị, timeout/expiry dương.
- Chạy JVM/DB session UTC; JDBC URL phải cấu hình múi giờ kết nối UTC tương ứng driver, ví dụ `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`.
- DB/schema được provision trước; không bật createDatabaseIfNotExist hoặc root/password rỗng làm default chung.
- Profile dev có thể dùng MySQL local, tài khoản riêng và TLS tắt chỉ trên máy phát triển; không đưa cấu hình đó vào production.
- Test DB dùng MySQL local 8.0.43 và `MySqlTestConfiguration`, JWT secret giả đủ mạnh, AiClient mock/stub và disable scheduler.
- Test nhanh không cần DB/credentials; integration test cần tài khoản test riêng qua `TEST_DB_*`. Không tự drop/clean schema; lần đầu provision database trống, các lần sau giữ Flyway history và rollback fixture DML. Không chạy song song nhiều bộ test trên cùng database.
- `V1__init_schema.sql` tạo đủ 8 bảng, FK/cascade/unique/check/index theo §2; không sửa migration đã áp dụng. Với DB cũ phải có kế hoạch baseline riêng, không tự bật baseline-on-migrate.

---

## 11. GIAI ĐOẠN TRIỂN KHAI BACKEND

> Thực hiện tuần tự; sửa lỗi test trước khi chuyển phase. Windows dùng `./mvnw.cmd`, Unix dùng `./mvnw`.
> Chạy `test` sau mỗi phase; chạy `verify` nếu phase có integration tests. Không chỉ dựa vào test mock để khẳng định DB đã commit đúng.

### Phase 0 — Nền tảng và test harness

1. Dependencies, properties, Clock UTC, migration V1 và môi trường MySQL local.
2. MySQL local riêng cho test, test profile, secret giả, AI stub; cấu hình Surefire/Failsafe/JaCoCo ngay từ đầu.
3. **DoD:** migration chạy trên MySQL test trống; test nhanh không cần DB/secret, contextLoads dùng tài khoản DB test riêng và không gọi AI thật. `ddl-auto=validate` được giữ và xác nhận đầy đủ sau Phase 1.

### Phase 1 — Entity và repository

1. 8 entity, enum, auditing, mapping/cascade theo §2–§3.
2. Repository: query ownership, fetch collection, due-count LessThanEqual, locking refresh/word và bulk delete refresh token hết hạn (word_cache không dọn theo thời gian).
3. **DoD:** `@DataJpaTest` với MySQL local trong database test riêng chứng minh schema/mapping khớp, unique/collation, FK/cascade, độ dài và due boundary đúng.

### Phase 2 — Auth và security

1. JwtService/filter/security chain, forms/DTO, handler và 4 auth endpoint.
2. Rotation atomic, expired-token deletion commit, logout idempotent.
3. **DoD:** MVC slice xác nhận JSON/status và filter; unit test BCrypt/JWT; integration test xác nhận row bị xóa thật, token mới khác trong cùng giây, token cũ reuse thất bại và hai refresh đồng thời chỉ một thành công.

### Phase 3 — Words CRUD và due-count

1. Mapper, forms/DTO, service và 5 endpoint: list, due-count, create, update, delete.
2. Ownership cha/con, chuẩn hóa key, sync nghĩa, unique race.
3. **DoD:** unit + MVC + DB integration flow tạo→sửa→xóa, ID nghĩa ngoại lai bị chặn, rollback toàn bộ khi có lỗi, nextReview bằng now vẫn được đếm.

### Phase 4 — AI sinh nghĩa và cache

1. AiClient, RestClient adapter, schema/prompt, cache transaction helper.
2. Endpoint generate với cache-first và race recovery sau rollback.
3. **DoD:** HIT không gọi AI; MISS tạo đầy đủ cache; lỗi không tạo row; concurrent same-key trả dữ liệu hợp lệ và chỉ một cache; HTTP stub kiểm tra timeout/status/schema sai.

### Phase 5 — Phrases và chấm văn

1. GradingService, PhraseTxService, mapper/forms và 3 endpoint.
2. Validate trước/sau AI; không clamp; chỉ lưu sau grading thành công.
3. **DoD:** mock/stub AI fail hoặc score/schema sai → không có dữ liệu; phrase + errors atomic; 403 với missing/foreign ID; response envelope đúng.

### Phase 6 — Review và cleanup

1. Review endpoint + locking + lịch SRS; TokenCleanupService + config cron/zone.
2. **DoD:** distinct IDs, rollback nếu lẫn ID không thuộc user, concurrent review không lost update; cleanup chỉ xóa expiry <= now bằng clock cố định.

### Phase 7 — Nghiệm thu và vận hành backend

1. Integration HTTP end-to-end bằng JWT thật, MySQL test local, AI stub: register→login→generate→tạo từ→review→due-count→phrase→logout→refresh bị từ chối.
2. API reference với request/response mẫu cho đủ 14 endpoint; README hướng dẫn biến môi trường, DB, migrate, test và chạy JAR. OpenAPI tự sinh là tùy chọn, chỉ thêm thư viện tương thích Boot 4 đã kiểm chứng.
3. Rà soát log/secrets, cấu hình production và checklist §14.
4. **DoD:** `./mvnw.cmd verify` xanh, integration tests thực sự chạy, coverage service ≥80%, backend chạy độc lập không cần client.

### Phase 8 — Sinh từ theo chủ đề (act-20) ✅ đã hoàn thành (2026-10-05)

1. Form/DTO `TopicForm`, prompt xác nhận chủ đề + sinh 10 từ, tái dùng AiClient/RestClient adapter và cache transaction helper của Phase 4. — xong: `AiClient.checkTopic`/`generateTopicWords` + adapter `LlmAiClient`, prompt dùng topic chuẩn hóa và exclude list từ đã có.
2. Endpoint generate-topic: lọc trùng, cache-first từng từ và lưu batch vào sổ trong một transaction ghi. — xong: `POST /api/words/generate-topic` (WordController), `TopicGenerateServiceImpl` (chuẩn hóa → checkTopic → exclude → AI → lọc trùng → tx), `TopicTxServiceImpl` (1 tx: cache-first + `Word` mới `reviewCount=0`, due ngay), `InvalidTopicException` → 400.
3. **DoD:** chủ đề sai → 400 và DB không đổi; AI fail/schema sai → 502 và DB không đổi; từ trùng bị bỏ qua nhưng các từ mới vẫn lưu; từ mới có `reviewCount=0` và due ngay; 201 trả `SuccessResponse<List<WordDto>>`; race trùng từ không hủy cả lô. — đạt: unit + controller test cho đầy đủ nhánh trên, bộ 286 test xanh (`./mvnw test`).

**Ước lượng sơ bộ:** 8–12 ngày làm việc cho một người đã quen stack, cộng dự phòng 20–30% cho tích hợp/provider và xử lý concurrency. Chỉ tính backend; cần hiệu chỉnh sau Phase 0, không coi đây là cam kết tiến độ.

---

## 12. CHIẾN LƯỢC KIỂM THỬ

- **Unit:** Mockito + AssertJ; mock repository/AiClient; ưu tiên nghiệp vụ, mapping và nhánh lỗi. Không suy luận tính nguyên tử DB từ mock.
- **MVC slice:** `@WebMvcTest` + `@MockitoBean` service; import security config/filter và mock các dependency cần thiết. Kiểm tra JSON, validation, 401/403/409/502.
- **JPA slice:** `@DataJpaTest` + MySQL local, `@ActiveProfiles("test")`, `@Import(MySqlTestConfiguration.class)`, `@AutoConfigureTestDatabase(replace=NONE)`; không dùng H2 thay cho test đặc thù MySQL.
- **Integration:** `@SpringBootTest(RANDOM_PORT)` + `@AutoConfigureRestTestClient` + profile/config test local; test concurrency dùng kết nối/transaction riêng và latch/barrier, không dùng sleep làm đồng bộ.
- Test commit/rollback phải quan sát DB sau request từ transaction mới; tránh outer test transaction che việc commit thật.
- Tỷ lệ 70/20/10 chỉ định hướng test pyramid, không là tiêu chí phần trăm cứng. Ưu tiên bao phủ rủi ro; JaCoCo chỉ hỗ trợ, không thay thế test assertion.

**Test biên bắt buộc:**
- Username trùng kể cả race; password Unicode vượt 72 byte; login constraint fail trả 401.
- Refresh thiếu/sai type/tampered/expired/reuse; xóa expired thực sự commit; concurrent rotation; token phát hành cùng giây vẫn khác.
- Scope user cho list/due-count; missing/foreign parent ID trả 403; foreign/duplicate child ID trả 400 và DB không thay đổi.
- Chuẩn hóa `Hello`/` hello ` về cùng key; unique cache và unique sổ từ đúng phạm vi.
- Due `nextReview == now`, UTC/precision; SRS đủ 5 mốc theo bảng điều chỉnh 1→3→7→14→30 (user 2026-10-02, thay mốc 6 cũ); review rỗng/null/phần tử null/quá giới hạn/ID lặp/mixed ownership/concurrent.
- AI cache-hit không call; validWord false; validPhrase false; thiếu score/null/wrong type/out-of-range; timeout/429/5xx; không có row lỗi hoặc bản ghi mồ côi.
- Cascade và orphanRemoval; phrase 5000 ký tự, correctedText dài hơn 255; field vượt giới hạn bị từ chối trước ghi DB.
- Cleanup trước/bằng/sau expiry; protected API thiếu JWT; refresh token không dùng thay access token.

---

## 13. QUYẾT ĐỊNH THIẾT KẾ VÀ ĐỐI CHIẾU DIAGRAM

1. **Chỉ backend:** không nghiệm thu công việc Android. Các diagram thiết bị chỉ giúp xác định dữ liệu API, không sinh task triển khai thiết bị.
2. **403 cho missing/foreign resource:** giữ đúng seq-07, seq-08, seq-13; bỏ lựa chọn 404 nghiệp vụ của bản kế hoạch trước. Route HTTP không tồn tại vẫn có thể trả 404.
3. **Success envelope:** cụ thể hóa mô tả “message + DTO” thành `{message,data}` cho create/update word và create phrase; không đổi shape GET/auth.
4. **AI validation:** diagram chưa xác định thuật toán nhận diện từ/đoạn văn. Chọn validation hình thức cục bộ, validation ngữ nghĩa bằng AI; nội dung vô nghĩa vẫn có thể tốn lượt gọi, hoặc nhận 502 nếu provider lỗi trước khi phân loại.
5. **Nguồn dữ liệu sổ từ:** chọn lưu WordForm đã validate, không âm thầm copy đè từ cache; phù hợp ghi chú seq-16 và giải quyết mô tả chưa thống nhất với seq-09.
6. **Token:** access 30m, refresh 7d, logout idempotent; lưu chuỗi refresh gốc theo ERD. Hash-token/token-family/replay revocation nâng cao để giai đoạn sau; hạn chế quyền DB và bảo vệ backup.
7. **SRS:** từ mới due ngay (`nextReview=createdAt`), due dùng <=, từ lần 6 giữ 30 ngày. Đây là cụ thể hóa những điểm chưa chi tiết trong diagram.
8. **UTC/Instant, chiều dài cột, giới hạn payload, chuẩn hóa key và locking:** bổ sung kỹ thuật để triển khai nhất quán, không phải thuộc tính đã được tất cả diagram mô tả.
9. **Score:** chỉ số nguyên 0..10; AI sai → 502, không sửa điểm bằng clamp.
10. **Message:** chuỗi trong §4/§9 là contract kiểm thử backend; không giả định client phải suy luận nghiệp vụ từ text tiếng Việt.
11. **Không tự mở rộng:** chưa thêm phân trang, cache TTL, idempotency key, admin, OCR/server push; khi mở rộng cần cập nhật contract và tests.
12. **Thêm từ theo chủ đề (act-20, bổ sung theo yêu cầu user 2026-10-05):** diagram mô tả "tránh trùng danh sách từ đã gửi" ở mức ý định AI; chốt lọc trùng theo khóa chuẩn hóa trước khi lưu — từ trùng bị bỏ qua thay vì hủy cả lô, UNIQUE `(user_id, english)` là lớp chặn cuối. Gọi AI (xác nhận chủ đề và sinh từ) ngoài transaction; cả lô lưu trong một transaction ghi. Message thành công và shape 201 do kế hoạch cụ thể hóa: `Thêm từ theo chủ đề thành công` + `SuccessResponse<List<WordDto>>`; endpoint đưa tổng số lên 15.

---

## 14. CHECKLIST NGHIỆM THU VÀ TRIỂN KHAI BACKEND

### 14.1. Chức năng và chất lượng

- [ ] MySQL trống migrate đủ 8 bảng; schema validate khớp entity; không có bảng phụ ngoài dự kiến.
- [ ] Đủ 15 endpoint (gồm generate-topic) đúng status/JSON/message, tài liệu request/response cập nhật.
- [ ] Không lộ/sửa dữ liệu chéo user; kiểm tra cả ID nghĩa và phrase/review.
- [ ] Rotation atomic, expired deletion commit, reuse/concurrent token test đạt.
- [ ] Cache race không hỏng transaction; không lưu kết quả AI lỗi; phrase/errors atomic.
- [ ] SRS/due-count/cleanup dùng cùng UTC Clock và đúng boundary.
- [ ] `./mvnw.cmd verify` chạy cả unit/slice/integration và JaCoCo check ≥80% service line coverage.
- [ ] Automated tests không cần AI thật, client hoặc credentials cá nhân.
- [ ] Không secret trong source/log; không log nguyên password/token/đoạn văn mặc định.

### 14.2. Triển khai backend

- [ ] Build JAR bằng Java 21; ghi rõ command chạy và danh sách biến môi trường bắt buộc.
- [ ] Reverse proxy/ingress HTTPS; chỉ mở port cần thiết; MySQL không public internet.
- [ ] DB account quyền tối thiểu; tách migration credentials khỏi runtime ở production; kết nối DB ngoài máy dùng TLS phù hợp.
- [ ] Secret cấp qua môi trường/secret manager; không dùng root rỗng hoặc secret dev; có quy trình đổi JWT secret và thông báo ảnh hưởng phiên đăng nhập.
- [ ] Health/readiness check tối thiểu (Actuator hoặc endpoint nội bộ); không public chi tiết cấu hình/DB/secrets.
- [ ] Cấu hình request size/rate limit ở proxy cho auth và AI để hạn chế brute force và chi phí; ghi rõ ngưỡng vận hành, response 413/429 do proxy nằm ngoài bảng lỗi nghiệp vụ.
- [ ] Log có correlation ID, theo dõi lỗi/độ trễ AI và DB; backup MySQL và thử restore trước khi dùng dữ liệu quan trọng.
- [ ] Smoke test qua HTTPS trên backend đã deploy; không khởi chạy client để thay thế kiểm thử API.

---

## PHỤ LỤC — BẢN ĐỒ DIAGRAM ĐỐI VỚI BACKEND

Nguồn: `D:\bao_cao_LEnglish\docs\`; activity tại `activity\act-XX-*.puml`, sequence tại `sequence\seq-XX-*.puml`.

| Luồng | Activity / Sequence | Phần backend trong phạm vi |
|---|---|---|
| Tạo tài khoản | 01 | POST /api/auth/register |
| Đăng nhập | 02 | POST /api/auth/login |
| Đăng xuất | 03 | POST /api/auth/logout |
| Làm mới token | 04 | POST /api/auth/refresh |
| Xem từ | 05 | GET /api/words |
| Thêm từ | 06 | POST /api/words |
| Sửa từ | 07 | PUT /api/words/{id} |
| Xóa từ | 08 | DELETE /api/words/{id} |
| Sinh nghĩa từ | 09 | GET /api/words/generate |
| Xem đoạn văn | 10 | GET /api/phrases |
| Thêm đoạn văn | 11 | POST /api/phrases |
| Chấm đoạn văn | 12 | GradingService nội bộ, không có endpoint riêng |
| Xóa đoạn văn | 13 | DELETE /api/phrases/{id} |
| Nhắc ôn / thống kê số từ | 14–15 | Chỉ cung cấp GET /api/words và GET /api/words/due-count; không triển khai phần thiết bị |
| Thêm từ từ vùng chọn / OCR | 16–17 | Tái sử dụng POST /api/words, GET /api/words/generate, POST /api/phrases; không có API thiết bị/OCR mới |
| Dọn refresh token | 18 | @Scheduled server |
| Xác nhận ôn tập | 19 | POST /api/words/review |
| Thêm từ theo chủ đề | 20 | POST /api/words/generate-topic |

Diagram cấu trúc: `class-diagram.puml` cho entity, `erd-diagram.puml` cho 8 bảng, `package-diagram.puml` cho phân tầng, `deployment-diagram.puml` cho phần backend–MySQL–AI, `use-case-diagram.puml` cho chức năng. Các kỹ thuật bổ sung trong kế hoạch được ghi tại §13; sửa tài liệu này không đồng nghĩa các diagram nguồn đã được cập nhật.
