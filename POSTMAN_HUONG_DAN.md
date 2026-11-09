# Hướng dẫn test API bằng Postman

File cần gửi cho tester: **`LEnglish.postman_collection.json`** (chỉ 1 file, không cần environment).

## 1. Import

1. Mở Postman → **Import** (góc trên trái) → **Files** → chọn `LEnglish.postman_collection.json`.
2. Collection **LEnglish — API Backend** xuất hiện ở sidebar, gồm 6 folder và 38 request.

## 2. Chạy

- **Chạy toàn bộ:** chuột phải collection → **Run collection** → **Run LEnglish — API Backend**. Không cần nhập gì thêm, biến mặc định đã đủ.
- **Chạy từng request:** mở folder, chọn request → **Send**. Nên chạy theo đúng thứ tự từ trên xuống (các request sau dùng `wordId`, `phraseId`... do request trước lưu lại).

Kết quả mong đợi: **38/38 request pass, 90 assert pass** (Newman đếm thêm các request do script tự gửi khi refresh/login/tạo user B, nên chỉ số "requests" hiển thị 46 — không phải lỗi).

## 3. Biến trong collection (Collection → Variables)

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | Đổi thành IP/host server nếu backend chạy máy khác, ví dụ `http://192.168.1.5:8080` |
| `username` / `password` | `tester01` / `Password123` | Request Register tự sinh `username` mới mỗi lần chạy |
| `accessToken`, `refreshToken`, `tokenExpiresAt` | (trống) | Request Login/Refresh tự lưu; collection tự refresh khi còn <60 giây hoặc tự login khi chưa có token |
| `englishWord` | `serendipity` | Từ dùng cho Generate / Thêm từ |
| `topic` | `Du lịch` | Chủ đề cho Generate-topic |
| `wordId`, `wordValueId`, `phraseId`, `topicWordId` | (trống) | Do request tạo mới tự lưu, dùng lại ở các bước sau |
| `dupWordId` | (trống) | Từ tạo ở folder 04 để test trùng từ (409), tự xoá sau khi kiểm tra |
| `intruderUsername`, `intruderToken` | (trống) | User B tự tạo ở folder 05 để test thao tác chéo |
| `crossWordId`, `crossWordValueId`, `crossPhraseId` | (trống) | Tài nguyên của user A dùng riêng cho ca 403 ở folder 05 |

## 3.1. Folder 05 — kiểm tra phân quyền chéo (403)

Đây là phần kiểm tra bảo mật quan trọng nhất: **user B không được sửa/xoá dữ liệu của user A**.

Khi chạy, folder này tự làm 2 việc (không cần nhập tay):
1. Tạo tài khoản thứ 2 (`intruder_xxxxxx`) và đăng nhập → lưu `intruderToken`.
2. Tạo một từ (`zephyr`) và một đoạn văn thuộc **user A** → lưu `crossWordId` / `crossPhraseId`.

Sau đó gửi 3 request bằng token **user B** lên tài nguyên của A:

| Request | Mong đợi |
|---|---|
| `PUT /api/words/{id}` | `403` + `"Không có quyền sửa từ này"` |
| `DELETE /api/words/{id}` | `403` + `"Không có quyền xóa từ này"` |
| `DELETE /api/phrases/{id}` | `403` + `"Không có quyền xóa đoạn văn này"` |

Cuối mỗi request có bước **dọn dẹp**: user A tự xoá tài nguyên của mình, phải trả `200`. Câu assert `200` này quan trọng — nó chứng minh tài nguyên **có thật** và 403 phía trên là do sai chủ sở hữu, không phải vì tài nguyên không tồn tại (nếu chỉ thấy 403 mà không kiểm tra 200 thì một backend "chặn tất cả" cũng pass).

## 4. Lưu ý khi test

- **Không gửi token** ở 4 endpoint `/api/auth/*` (Register, Login, Refresh, Logout): đã đặt sẵn `No Auth`, đúng thiết kế backend.
- **3 endpoint gọi AI** (`GET /api/words/generate`, `POST /api/words/generate-topic`, `POST /api/phrases`) chạy 5–15 giây là bình thường — đừng bấm Cancel. Nếu AI hết hạn mức/key sẽ trả **502** `"Dịch vụ AI tạm thời không khả dụng"`, không phải lỗi API.
- **Ca lỗi 403 ở folder 04 là đúng:** xoá lại từ/đoạn văn vừa xoá phải trả 403 (backend không tiết lộ tài nguyên có tồn tại hay không). Folder 05 kiểm tra 403 ở góc độ khác: tài nguyên còn sống nhưng do user khác sở hữu.
- **Ca 400 của đoạn văn (folder 04):** `POST /api/phrases` thiếu/để trống `title`, `title` dài quá 255 ký tự, `text` quá ngắn hoặc toàn khoảng trắng → `400` `"Đoạn văn gửi lên không hợp lệ"`. Riêng endpoint này backend trả **một câu lỗi cố định, chỉ có `message`** (không có `details[]` như `/api/words`), nên đừng thắc mắc khi không thấy chi tiết field.
- **`title` của đoạn văn:** bắt buộc, 1–255 ký tự, là nhãn người dùng tự đặt (vd `"Luyện viết IELTS"`). Title **không** được gửi cho AI — chỉ `text` được chấm; response trả lại `title` nguyên văn.
- **Thứ tự danh sách đoạn văn:** `GET /api/phrases` trả **mới nhất trước** (`createdAt` giảm dần; trùng thời điểm thì `id` giảm dần), nên request "Danh sách chứa đoạn văn vừa tạo" còn assert đoạn văn vừa tạo nằm ở `data[0]`.
- **Ca 409 trùng từ (folder 04):** request gửi `POST /api/words` lần hai với đúng từ đã có → phải trả `409` `"Từ này đã có trong sổ của bạn"`. Pre-request tự tạo từ trước rồi test tự xoá, nên chạy lại nhiều lần vẫn đúng.
- Mỗi lần **Run collection** sẽ tạo 1 user mới (`tester_xxxxxx`) và tự dọn dữ liệu đã tạo, nên chạy lại bao nhiêu lần cũng được.
- Nếu muốn giữ dữ liệu để xem trong DB: chạy từng request thay vì Run collection, và bỏ qua 2 request `Xoá ...` ở cuối folder 02 và 03.

## 5. Lỗi thường gặp

| Hiện tượng | Nguyên nhân |
|---|---|
| Tất cả request 401 `"Token không hợp lệ hoặc hết hạn"` | Backend chưa chạy, hoặc `baseUrl` sai cổng (mặc định 8080) |
| `Could not get any response` / ECONNREFUSED | Sai `baseUrl` hoặc server chưa bật |
| Register trả 409 `"Tên đăng nhập đã tồn tại"` | Chỉ xảy ra khi sửa tay `username`; cứ chạy lại request, script tự đổi username |
| Generate-topic trả 400 `"Chủ đề không hợp lệ..."` | Sửa biến `topic` thành chủ đề khác (tiếng Việt hoặc Anh đều được) |
| Assert `dueCount` fail | Có từ khác trong sổ đang đến hạn — chạy lại collection với user mới (Register tự tạo) |
