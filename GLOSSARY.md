# LEnglish

Backend học từ vựng tiếng Anh. Ngữ cảnh này quản lý **Tài khoản**, việc xác thực email khi đăng ký và các **Phiên** đăng nhập.

## Language

**Tài khoản**:
Một người dùng đã xác thực email, sở hữu sổ từ, đoạn văn và các **Phiên**. Một **Tài khoản** chỉ tồn tại sau khi **Mã xác thực** của một **Đăng ký chờ** được chấp nhận.
_Avoid_: người dùng, user, tài khoản chưa kích hoạt

**Đăng ký chờ**:
Bản nháp đăng ký giữ tên, **Tên đăng nhập**, **Email tài khoản**, mật khẩu và **Mã xác thực** hiện hành; tự hết hạn và không bao giờ dùng để đăng nhập. Không phải **Tài khoản**.
_Avoid_: tài khoản tạm, tài khoản chưa xác thực, pending user

**Tên đăng nhập**:
Định danh duy nhất dùng để đăng nhập, phân biệt hoa thường.
_Avoid_: username (trong văn bản tiếng Việt), tên tài khoản

**Email tài khoản**:
Email duy nhất, đã xác thực, thuộc về một **Tài khoản**; là kênh duy nhất để **Khôi phục mật khẩu**.
_Avoid_: email khôi phục, email dự phòng, email liên hệ

**Mã xác thực**:
Mã sáu chữ số dùng một lần, gắn với đúng một mục đích: xác nhận một **Đăng ký chờ** hoặc **Khôi phục mật khẩu**.
_Avoid_: OTP, token, link xác thực

**Khôi phục mật khẩu**:
Quy trình chứng minh quyền sở hữu **Email tài khoản** bằng **Mã xác thực**, rồi đặt mật khẩu mới mà không cấp **Phiên**.
_Avoid_: quên mật khẩu (đó là tên màn hình, không phải khái niệm), reset password

**Phiên**:
Một cặp access token và refresh token gắn với một **Tài khoản**. Mật khẩu bị đổi hoặc được khôi phục thì mọi **Phiên** của **Tài khoản** đó bị thu hồi.
_Avoid_: session, đăng nhập
