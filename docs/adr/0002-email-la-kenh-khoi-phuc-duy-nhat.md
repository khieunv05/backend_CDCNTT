# Email là kênh khôi phục mật khẩu duy nhất

Ứng dụng hướng tới người dùng điện thoại, nhưng `Tên đăng nhập` vẫn là định danh đăng nhập và không phải kênh liên lạc. Chúng tôi chọn `Email tài khoản` làm kênh khôi phục duy nhất: chỉ email mới chứng minh được người yêu cầu đang kiểm soát một hộp thư mà hệ thống đã biết, còn username thì ai biết cũng gõ được, và số điện thoại thì chưa có nhà cung cấp SMS, chưa có luồng xác thực số, kèm chi phí gửi tin.

## Consequences

- Người dùng quên mất email đã đăng ký thì mất đường khôi phục; tài liệu dành cho client ghi rõ trường hợp này chỉ còn cách liên hệ hỗ trợ. Đây là cái giá đã được chấp nhận, không phải thiếu sót cần "sửa" bằng cách thêm username vào form.
- `POST /api/auth/forgot-password` luôn trả 200 với cùng một câu, kể cả khi vượt hạn mức gửi, để không ai dùng endpoint này dò xem email nào đã đăng ký.
- `users.email` trở thành cột bắt buộc và duy nhất (so sánh không phân biệt hoa thường), trong khi `username` vẫn phân biệt hoa thường như trước.
- Khôi phục xong phải thu hồi toàn bộ `Phiên` và không trả token: client tự gọi `/login` lại bằng mật khẩu mới.

## Considered Options

- **Cho phép nhập username hoặc số điện thoại**: không chứng minh được quyền sở hữu kênh nhận mã, và SĐT cần thêm hạ tầng SMS.
- **Magic link thay vì mã 6 số**: đường liên kết dễ bị quét bởi trình quét email, khó nhập lại trên điện thoại khi mở mail ở thiết bị khác; mã 6 số dùng chung một cơ chế cho cả đăng ký và khôi phục.
- **Trả token ngay sau khi đặt lại mật khẩu**: tiện cho client nhưng mâu thuẫn với yêu cầu thu hồi mọi phiên cũ.
