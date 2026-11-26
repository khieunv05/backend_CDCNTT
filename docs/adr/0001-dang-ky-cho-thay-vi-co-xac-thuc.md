# Tài khoản chỉ xuất hiện sau khi xác thực email

Đăng ký hai bước cần một chỗ để giữ lời khai của người chưa xác thực. Chúng tôi chọn bảng riêng `pending_registrations` (bản nháp) thay vì tạo ngay dòng `users` kèm cờ "đã xác thực": nhờ vậy mọi dòng trong `users` luôn là tài khoản dùng được, đăng nhập và các truy vấn khác không phải mang thêm điều kiện `verified = true`, và một bản nháp bị bỏ dở chỉ để lại rác trong bảng nháp — bản nháp bị scheduler xoá khi hết hạn, còn gửi lại thì được làm mới tại chỗ, thay vì để lại vĩnh viễn một tài khoản nửa vời.

## Consequences

- Ràng buộc duy nhất của `Tên đăng nhập` và `Email tài khoản` phải được kiểm tra ở cả hai bảng; xung đột với bản nháp của hộp thư khác trả 409 ngay ở bước 1 thay vì sau khi người dùng gõ mã.
- Bản nháp chứa BCrypt hash của mật khẩu, nên vòng đời của nó bị buộc vào vòng đời của `Mã xác thực` (10 phút) — không có bản nháp sống lâu hơn mã. Gửi lại làm mới bản nháp tại chỗ thay vì xoá rồi tạo lại, để không phải chịu lỗi UNIQUE do INSERT chạy ngay khi persist còn DELETE chỉ chạy lúc flush.
- `POST /api/auth/register` đổi từ tạo tài khoản ngay sang gửi mã: đây là breaking change có chủ ý với client cũ.
- Chấp nhận lộ thông tin ở bước 1 (409 khi username/email đã tồn tại) để người dùng biết ngay mình không thể dùng giá trị đó; chỉ luồng khôi phục mật khẩu mới trả lời mù.

## Considered Options

- **Tạo `users` ngay + cờ `verified`**: ít bảng hơn, nhưng mọi truy vấn đọc tài khoản phải lọc thêm, dữ liệu rác nằm lại trong bảng thật, và UNIQUE username/email bị chiếm bởi người chưa chứng minh được quyền sở hữu hộp thư.
- **Bảng nháp (đã chọn)**: thêm một bảng và một migration, đổi lại `users` giữ được nghĩa "tài khoản đã xác thực".
