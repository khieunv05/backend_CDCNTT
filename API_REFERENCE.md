# LEnglish — API Reference (§4.1)

Tài liệu tham chiếu cho **22 endpoint** của backend LEnglish (§4.1 bảng `KE_HOACH_TRIEN_KHAI.md`).
Tất cả request/response body đều là JSON UTF-8.

## 1. Quy ước chung

- **Base URL:** `http://<host>:<port>/api`
- **Xác thực:** mọi endpoint trừ 7 POST công khai `/api/auth/register`, `/api/auth/register/confirm`,
  `/api/auth/forgot-password`, `/api/auth/reset-password`, `/api/auth/login`, `/api/auth/refresh`,
  `/api/auth/logout` đều yêu cầu header
  `Authorization: Bearer <accessToken>` (JWT HS256, claims `sub`, `uid`, `type`, `iat`, `exp`, `jti`).
  Thiếu/sai token → **401**. `/api/auth/logout` được permitAll — gọi được không cần token.
- **Chủ sở hữu dữ liệu luôn lấy từ JWT** (`uid` trong principal), không bao giờ từ request (§5.3).

### 1.1. Bọc phản hồi thành công

| Kiểu | Shape | Dùng cho |
|---|---|---|
| `MessageResponse` | `{ "message": "..." }` | register, register/confirm, forgot-password, reset-password, logout, delete |
| `SuccessResponse<T>` | `{ "message": "...", "data": T }` | create/update từ, thêm từ theo chủ đề, create đoạn văn |
| Response object trần | DTO trực tiếp | danh sách, generate, review, due-count, login/refresh |

### 1.2. Lỗi (GlobalExceptionHandler)

| Shape | Khi nào |
|---|---|
| `{ "message": "..." }` (`ErrorResponse`) | mọi lỗi nghiệp vụ |
| `{ "message": "Thông tin không hợp lệ", "details": [{ "field", "message" }] }` (`ValidationErrorResponse`) | 400 Bean Validation / tham số không hợp lệ |

Mã lỗi chung: **401** chưa xác thực/sai thông tin đăng nhập, **403** không sở hữu tài nguyên,
**409** trùng dữ liệu, **429** vượt hạn mức gửi mã (`Bạn đã yêu cầu quá nhiều lần, vui lòng thử lại sau`),
**502** lỗi dịch vụ AI (`Dịch vụ AI tạm thời không khả dụng`) hoặc lỗi gửi email
(`Không gửi được email xác thực, vui lòng thử lại`), **500** `Lỗi hệ thống`.

## 2. Xác thực — `/api/auth`

### 2.1. Đăng ký — bước 1: yêu cầu mã — `POST /api/auth/register`

> **Breaking change so với bản 1 bước:** endpoint không còn tạo tài khoản và không còn trả `201`.
> Body cũ `{name, username, password}` thiếu `email` sẽ bị **400**; client phải gửi thêm `email`,
> rồi gọi `/api/auth/register/confirm` với mã trong email để hoàn tất (xem §2.2).

Body:
```json
{
  "name": "Nguyễn Văn A",
  "username": "alice",
  "email": "alice@example.com",
  "password": "matkhau_it_nhat_8_ky_tu"
}
```
- Ràng buộc: `name` 1–100 ký tự (bắt buộc, được trùng); `username` 3–50 ký tự `[A-Za-z0-9_.]`
  (phân biệt hoa/thường); `email` hợp lệ, tối đa 254 ký tự (chuẩn hoá trước khi so trùng);
  `password` ≥ 8 ký tự, tối đa 72 byte UTF-8.
- Endpoint **chưa tạo user**: chỉ ghi bản nháp `pending_registrations` (mật khẩu hash BCrypt,
  mã hash SHA-256) rồi gửi mã 6 chữ số qua email. Tài khoản chỉ xuất hiện sau bước 2.
- Chính sách mã: 6 chữ số, hết hạn sau **10 phút**, dùng một lần; mã mới huỷ mã cũ;
  gửi lại = gọi lại chính endpoint này. Cooldown **60 giây**; tối đa **5 mã/giờ/email**.
- Cùng một email gửi lại sẽ làm mới bản nháp **tại chỗ**, giữ nguyên `send_count`/`window_started_at` để hạn mức 5 mã/giờ không bị reset theo TTL; bản nháp hết hạn do scheduler dọn, không xoá ngay khi gặp.
- SMTP lỗi → **502**, **bản nháp được giữ** để caller gọi lại endpoint này.
- Bộ đếm IP trong bộ nhớ (`app.ip-rate-limit`, mặc định 30 request/giờ/IP) chặn spam qua nhiều mailbox: vượt → **429** cùng message. Đây là lớp phụ; hạn mức 5 mã/giờ/email trong DB mới là chuẩn.

**200** `{ "message": "Mã xác thực đã được gửi tới email" }`
**400** validation `Thông tin không hợp lệ` + `details[]`
**409** `{ "message": "Tên đăng nhập đã tồn tại" }` — username thuộc tài khoản khác **hoặc** đang bị
bản nháp của mailbox khác giữ.
**409** `{ "message": "Email này đã được sử dụng" }` — email đã thuộc một tài khoản, hoặc bản nháp đang chờ của mailbox khác vừa thắng UNIQUE `uk_pending_registrations_email`.
**429** `{ "message": "Bạn đã yêu cầu quá nhiều lần, vui lòng thử lại sau" }` — còn trong cooldown
hoặc vượt 5 mã/giờ.
**502** `{ "message": "Không gửi được email xác thực, vui lòng thử lại" }` — bản nháp vẫn còn.

### 2.2. Đăng ký — bước 2: xác thực mã — `POST /api/auth/register/confirm`

Body:
```json
{ "email": "alice@example.com", "code": "123456" }
```
- `code` đúng 6 chữ số; `email` cùng ràng buộc như §2.1.
- Mã đúng → tạo `users` và xoá bản nháp trong **cùng một transaction**. Sau đó đăng nhập bằng
  `username` (email không dùng để đăng nhập).
- Mã sai, hết hạn hoặc không có bản nháp → **cùng một 400**, không tiết lộ email có tồn tại hay không.
  Sai 5 lần → huỷ mã và bản nháp. Mã đã dùng không tái sử dụng được.

**201** `{ "message": "Tạo tài khoản thành công" }`
**400** `{ "message": "Mã xác thực không đúng hoặc đã hết hạn" }`
**400** validation (thiếu email/mã sai định dạng) · **409** `Tên đăng nhập đã tồn tại` hoặc
`Email này đã được sử dụng` nếu tài khoản/bản nháp khác chiếm mất trong lúc chờ.

### 2.3. Đăng nhập — `POST /api/auth/login`

Body: `{ "username": "alice", "password": "matkhau_it_nhat_8_ky_tu" }`. Email **không** dùng để
đăng nhập — email chỉ là kênh khôi phục duy nhất (§2.7–§2.8). **200**:
```json
{ "accessToken": "eyJ...", "refreshToken": "eyJ...", "expiresIn": 1800 }
```
**401** `{ "message": "Đăng nhập thất bại" }` (không tiết lộ trường sai).

### 2.4. Đăng xuất — `POST /api/auth/logout`

Không cần Bearer token (endpoint permitAll, không đọc principal). Body:
```json
{ "refreshToken": "eyJ..." }
```
Xóa row refresh token đúng chuỗi trong transaction; token không còn trong DB vẫn trả 200.
**200** `{ "message": "Đăng xuất thành công" }` · **400** body không hợp lệ.
Access token chưa hết hạn vẫn dùng được đến khi hết hạn (stateless, §5.2).

### 2.5. Làm mới phiên — `POST /api/auth/refresh`

Body: `{ "refreshToken": "eyJ..." }` (không cần Bearer).
**200** `AuthResponse` mới (cặp token mới).
**401** `{ "message": "Đăng nhập thất bại" }` hoặc `{ "message": "Phiên đăng nhập hết hạn" }`
(hết hạn trong DB → row bị xóa trước khi trả lỗi).

### 2.6. Đổi mật khẩu — `POST /api/auth/change-password`

Cần Bearer access token. Tài khoản lấy từ token, không lấy từ body — chỉ đổi được mật khẩu của chính mình.

Body:
```json
{ "currentPassword": "matkhau_cu", "newPassword": "matkhau_moi_it_nhat_8_ky_tu" }
```
- Ràng buộc: `currentPassword` không rỗng, ≤ 72 byte UTF-8; `newPassword` ≥ 8 ký tự, ≤ 72 byte UTF-8 và phải khác mật khẩu hiện tại.
- Thành công: cập nhật hash BCrypt **và** xoá toàn bộ refresh token của tài khoản trong cùng một transaction (các phiên khác bị đăng xuất).

**200** `{ "message": "Đổi mật khẩu thành công" }`
**400** `{ "message": "Mật khẩu hiện tại không đúng" }`
**400** `{ "message": "Mật khẩu mới phải khác mật khẩu hiện tại" }`
**400** validation body · **401** thiếu/sai token.
Access token đang dùng vẫn hợp lệ tới khi hết hạn (30 phút); muốn đăng nhập lại phải dùng mật khẩu mới.

### 2.7. Khôi phục mật khẩu — bước 1: yêu cầu mã — `POST /api/auth/forgot-password`

Body:
```json
{ "email": "alice@example.com" }
```
- Luôn trả **200** với đúng một message, dù email có tài khoản hay không (không cho dò tài khoản).
- Còn trong cooldown **60 giây** hoặc đã dùng đủ **5 mã/giờ/email**: **vẫn 200 im lặng**, không gửi thêm.
- SMTP lỗi ở luồng này cũng chỉ ghi log; endpoint **vẫn 200**.
- Vượt bộ đếm IP (`app.ip-rate-limit`) cũng **vẫn 200** với đúng message đó — không lộ việc bị chặn.
- Mã: 6 chữ số, hết hạn sau **15 phút**, dùng một lần; mã mới huỷ mã cũ; gửi lại = gọi lại endpoint này.

**200** `{ "message": "Nếu email này đã đăng ký, mã xác thực đã được gửi tới email đó" }`
**400** validation `Thông tin không hợp lệ` + `details[]` (email sai định dạng).

### 2.8. Khôi phục mật khẩu — bước 2: đặt mật khẩu mới — `POST /api/auth/reset-password`

Body:
```json
{ "email": "alice@example.com", "code": "123456", "newPassword": "matkhau_moi_8_ky_tu" }
```
- Mã đúng → đổi hash BCrypt **và thu hồi toàn bộ refresh token** của tài khoản trong cùng transaction.
- **Không trả token**: response chỉ có message; client phải gọi `/api/auth/login` bằng mật khẩu mới.
  Access token cũ vẫn dùng được tới khi hết hạn (30 phút, stateless) — giống §2.6.
- Mã được kiểm tra **trước**; chỉ khi mã hợp lệ mới so mật khẩu mới với hash cũ (trùng → 400 `Mật khẩu mới phải khác mật khẩu hiện tại`). Làm ngược lại sẽ biến hai message 400 thành oracle xác nhận email có tài khoản.

**200** `{ "message": "Đặt lại mật khẩu thành công" }` — body **không** có `accessToken`/`refreshToken`.
**400** `{ "message": "Mật khẩu mới phải khác mật khẩu hiện tại" }`
**400** `{ "message": "Mã xác thực không đúng hoặc đã hết hạn" }` — mã sai/hết hạn, hoặc email
không có tài khoản (cùng một message).
**400** validation (mật khẩu mới < 8 ký tự hoặc > 72 byte, mã không đủ 6 chữ số).

### 2.9. Hồ sơ & đổi tên — `GET|PUT /api/users/me`

Cần Bearer access token. Tài khoản luôn lấy từ token (không có id trên URL/body) nên chỉ đọc và sửa được hồ sơ của chính mình.

**`GET /api/users/me`** → **200** `UserProfileDto` (trả trần, không bọc):
```json
{ "id": 1, "username": "alice", "name": "Nguyễn Văn A" }
```

**`PUT /api/users/me`** body:
```json
{ "name": "Tên mới" }
```
- Ràng buộc: `name` không rỗng, tối đa 100 ký tự (khoảng trắng đầu/cuối được trim trước khi lưu); tên **được phép trùng** giữa các tài khoản.
- Chỉ đổi `name`; `username`, mật khẩu và token không bị ảnh hưởng.

**200** `{ "message": "Cập nhật tên thành công", "data": { "id": 1, "username": "alice", "name": "Tên mới" } }`
**400** validation (`details[].field = "name"`) · **401** thiếu/sai token.

## 3. Từ vựng — `/api/words`

### 3.1. Danh sách — `GET /api/words`
**200** `List<WordDto>`, sắp theo `createdAt` giảm dần (mới nhất trước; trùng thời điểm thì `id` giảm dần).

`WordDto`:
```json
{
  "id": 1, "english": "serendipity", "level": "B2",
  "reviewCount": 1, "nextReview": "2026-10-03T00:00:00Z",
  "createdAt": "...", "updatedAt": "...",
  "values": [
    { "id": 1, "vietnamese": "sự tình cờ may mắn",
      "example": "A serendipity brought us together.",
      "exampleTranslation": "Một sự tình cờ may mắn đã đưa chúng tôi đến với nhau.",
      "pronunciation": "/ˌser.ənˈdɪp.ə.ti/", "partOfSpeech": "NOUN" }
  ]
}
```

### 3.2. Số từ đến hạn — `GET /api/words/due-count`
**200** `{ "dueCount": 3 }` — đếm từ có `nextReview <= now`.

### 3.3. Sinh nghĩa bằng AI — `GET /api/words/generate?english=serendipity`
- Chấp nhận 1–5 từ (kể cụm động từ: `make up`, `get along with`); tự lower-case + gộp khoảng trắng.
- **200** `GeneratedWordDto`: chỉ gồm `english`, `level`, `values` (không có `id`, `reviewCount`,
  `nextReview`, `createdAt`, `updatedAt`); `values[].id` là null (chưa lưu).
  **400** `Từ hoặc cụm từ gửi lên không phải một từ tiếng Anh hợp lệ` · **502** lỗi AI.

### 3.4. Thêm từ — `POST /api/words`
Body (từ 1–20 nghĩa, tối thiểu 1):
```json
{
  "english": "serendipity",
  "level": "B2",
  "values": [ { "vietnamese": "sự tình cờ may mắn", "example": "...",
                "exampleTranslation": "...", "pronunciation": "...", "partOfSpeech": "NOUN" } ]
}
```
`partOfSpeech` là tên enum: `NOUN`, `VERB`, `ADJECTIVE`, `ADVERB`, `PREPOSITION`, `CONJUNCTION`, `PRONOUN`, `INTERJECTION`.
Từ mới có `reviewCount = 0` và `nextReview = createdAt` (đến hạn ôn ngay, §6.3).

**201** `SuccessResponse<WordDto>`: `{ "message": "Thêm từ thành công", "data": WordDto }`
**400** validation · **409** `{ "message": "Từ này đã có trong sổ của bạn" }`

### 3.5. Sửa từ — `PUT /api/words/{id}`
Body giống thêm từ. Nghĩa có `id` được cập nhật tại chỗ, nghĩa không có `id` được thêm mới,
nghĩa thiếu trong danh sách bị xóa.
**200** `SuccessResponse<WordDto>`: `{ "message": "Sửa từ thành công", ... }`
**400** · **403** `{ "message": "Không có quyền sửa từ này" }` · **409** trùng từ.

### 3.6. Xóa từ — `DELETE /api/words/{id}`
**200** `{ "message": "Xóa từ thành công" }` (cascade xóa meanings)
**403** `{ "message": "Không có quyền xóa từ này" }`

### 3.7. Ôn tập — `POST /api/words/review`
Body:
```json
{ "wordIds": [1, 2, 2] }
```
- Tối đa 500 phần tử; **id trùng được tự động gộp** (không phải lỗi);
  mọi id phải thuộc sở hữu — nếu không, **toàn batch rollback** (§6.3).
- Ladder SRS (bảng điều chỉnh): sau lần ôn thứ n, `nextReview = now + {1, 3, 7, 14, 30} ngày`
  (n = 1→1d, 2→3d, 3→7d, 4→14d, ≥5→30d).

**200** `{ "reviewedCount": 2 }` — số từ **distinct** đã ghi nhận.
**400** validation · **403** `{ "message": "Không có quyền với từ không thuộc sở hữu" }` (batch bị hủy hoàn toàn).

### 3.8. Sinh từ theo chủ đề — `POST /api/words/generate-topic`

Bước 1 của luồng 2 bước (act-20): **chỉ đề xuất và ghi cache, không đụng sổ từ**.

Body:
```json
{ "topic": "Du lịch" }
```
- Ràng buộc: topic `@NotBlank`, tối đa 255 ký tự (chuẩn hóa: trim + gộp khoảng trắng).
- Flow (§7.1): AI xác nhận chủ đề → đọc từ đã có trong sổ làm exclude → AI sinh 10 từ
  (nghĩa, phiên âm, từ loại, ví dụ, level) → từng từ cache-first (`word_cache`/`word_cache_values`).
- Từ đề xuất sai shape / trùng trong lô / đã có trong sổ **bị bỏ qua, không hủy cả lô**.

**200** `SuccessResponse<List<GeneratedWordDto>>`: `{ "message": "Đã sinh từ theo chủ đề", "data": [GeneratedWordDto] }`
(id của từ là `null` — chưa thuộc sổ của ai)
**400** `{ "message": "Chủ đề không được để trống" }` (validation) hoặc
`{ "message": "Chủ đề không hợp lệ, vui lòng nhập lại" }` (AI từ chối chủ đề) · **502** lỗi AI.

### 3.9. Thêm từ đã sinh vào sổ — `POST /api/words/generate-topic/confirm`

Bước 2 của luồng 2 bước (act-20): người dùng xem màn hình đề xuất, chọn/sửa từ, rồi xác nhận —
**lúc này mới ghi `word`/`word_value`**. Body là danh sách `WordForm` **giống hệt §3.3
(thêm từ thủ công)**: máy chủ tin dữ liệu gửi lên, **không đọc `word_cache` và không gọi AI**.

Body:
```json
{
  "words": [
    { "english": "airport", "level": "B1", "values": [
      { "vietnamese": "sân bay", "example": "I fly.", "exampleTranslation": "Tôi bay.",
        "pronunciation": "/ˈeəpɔːt/", "partOfSpeech": "NOUN" } ] },
    { "english": "hotel", "level": "A2", "values": [
      { "vietnamese": "khách sạn", "example": "A hotel.", "exampleTranslation": "Một khách sạn.",
        "pronunciation": "/həʊˈtel/", "partOfSpeech": "NOUN" } ] }
  ]
}
```
- Ràng buộc: `words` 1–20 phần tử (`@NotEmpty`, `@Size(max = 20)`), mỗi phần tử là một
  `WordForm` hợp lệ theo §3.3 (`@Valid`, `id` trong `values` bị bỏ qua).
- Flow (§13.12): chuẩn hóa + lọc từ sai định dạng/trùng trong lô/đã có trong sổ (bỏ qua, không
  hủy cả lô) rồi **một transaction duy nhất** ghi vào sổ, mỗi từ mới có `reviewCount = 0`
  (đến hạn ôn ngay). `UNIQUE (user_id, english)` là lớp chặn race cuối.
- Từ gửi lên **không phải từ tiếng Anh hợp lệ** (sai định dạng) → **400 toàn request**.

**201** `SuccessResponse<List<WordDto>>`: `{ "message": "Thêm từ theo chủ đề thành công", "data": [WordDto] }`
**400** validation hoặc `{ "message": "Từ hoặc cụm từ gửi lên không phải một từ tiếng Anh hợp lệ" }`.

## 4. Đoạn văn — `/api/phrases`

### 4.1. Danh sách — `GET /api/phrases`
**200** `List<PhraseDto>`, sắp theo `createdAt` giảm dần (mới nhất trước; trùng thời điểm thì `id` giảm dần).

`PhraseDto`:
```json
{
  "id": 5,
  "title": "Luyện viết IELTS",
  "text": "I has went to school yesterday.",
  "correctedText": "I went to school yesterday.",
  "score": 4,
  "createdAt": "...",
  "errors": [
    { "incorrect": "I has went", "correction": "I went",
      "explanation": "Past simple needs 'went', not 'has went'." }
  ]
}
```

### 4.2. Chấm điểm & lưu — `POST /api/phrases`
Body:
```json
{ "title": "Luyện viết IELTS", "text": "I has went to school yesterday." }
```
- Ràng buộc: `title` bắt buộc 1–255 ký tự, `text` 10–5000 ký tự. AI chỉ chấm `text`; `title` được
  lưu nguyên văn để hiển thị trong sổ. Kết quả `text + correctedText + score + errors` được lưu
  **nguyên tố** (một transaction; lỗi AI → không lưu gì).
- `score` là số nguyên 0–10 do AI trả về.

**201** `SuccessResponse<PhraseDto>`: `{ "message": "Thêm đoạn văn thành công", "data": PhraseDto }`
**400** `{ "message": "Đoạn văn gửi lên không hợp lệ" }` · **502** lỗi AI.

### 4.3. Xóa đoạn văn — `DELETE /api/phrases/{id}`
**200** `{ "message": "Xóa đoạn văn thành công" }` (cascade xóa errors)
**403** `{ "message": "Không có quyền xóa đoạn văn này" }`

## 5. Ví dụ luồng đầy đủ

```bash
# 1) Đăng ký 2 bước: yêu cầu mã → đọc mã trong email (hộp thư Brevo) → xác thực → đăng nhập
curl -s -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" \
     -d '{"name":"Nguyễn Văn A","username":"alice","email":"alice@example.com","password":"matkhau_8_ky_tu"}'
# mã 6 số nằm trong email; KHÔNG log mã ra console/CI
read -r CODE
curl -s -X POST http://localhost:8080/api/auth/register/confirm -H "Content-Type: application/json" \
     -d "{\"email\":\"alice@example.com\",\"code\":\"$CODE\"}"
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"alice","password":"matkhau_8_ky_tu"}' | jq -r .accessToken)

# 2) Sinh nghĩa + lưu từ
curl -s "http://localhost:8080/api/words/generate?english=serendipity" -H "Authorization: Bearer $TOKEN"
curl -s -X POST http://localhost:8080/api/words -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"english":"serendipity","level":"B2","values":[{"vietnamese":"sự tình cờ may mắn"}]}'

# 3) Sinh 10 từ theo chủ đề, xem đề xuất rồi xác nhận thêm vào sổ (danh sách WordForm như bước 2)
curl -s -X POST http://localhost:8080/api/words/generate-topic -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" -d '{"topic":"Du lịch"}'
curl -s -X POST http://localhost:8080/api/words/generate-topic/confirm -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"words":[{"english":"airport","level":"B1","values":[{"vietnamese":"sân bay","example":"I fly.","exampleTranslation":"Tôi bay.","pronunciation":"/ˈeəpɔːt/","partOfSpeech":"NOUN"}]}]}'

# 4) Ôn tập
curl -s -X POST http://localhost:8080/api/words/review -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" -d '{"wordIds":[1]}'

# 5) Khôi phục mật khẩu: yêu cầu mã (luôn 200) → đặt mật khẩu mới → đăng nhập lại
curl -s -X POST http://localhost:8080/api/auth/forgot-password -H "Content-Type: application/json" \
     -d '{"email":"alice@example.com"}'
read -r RESET_CODE
curl -s -X POST http://localhost:8080/api/auth/reset-password -H "Content-Type: application/json" \
     -d "{\"email\":\"alice@example.com\",\"code\":\"$RESET_CODE\",\"newPassword\":\"matkhau_moi_8_ky_tu\"}"
# reset-password KHÔNG trả token — đăng nhập lại bằng mật khẩu mới
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"alice","password":"matkhau_moi_8_ky_tu"}' | jq -r .accessToken)
```
