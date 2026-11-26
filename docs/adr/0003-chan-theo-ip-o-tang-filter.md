# Chặn theo IP ở tầng filter cho hai endpoint gửi mail

`POST /api/auth/register` và `POST /api/auth/forgot-password` là hai endpoint công khai duy nhất có thể làm hệ thống gửi email. Hạn mức 5 mã/giờ nằm trên từng email, nên kẻ spam có thể rải sang nhiều hộp thư; đồng thời phản hồi khi bị chặn khác nhau theo endpoint (429 cho register, 200 im lặng cho forgot-password) nên không thể đặt chung trong service mà không lặp lại quyết định HTTP ở controller.

Chúng tôi chọn `IpRateLimitFilter` (`OncePerRequestFilter`, chạy sau security chain): đọc `getRemoteAddr()`, fixed window 30 request/giờ/IP cấu hình qua `app.ip-rate-limit`, vượt thì register nhận 429 theo contract còn forgot-password vẫn nhận đúng 200 với message của happy path. Bộ đếm nằm trong bộ nhớ, mỗi instance một xô; service không biết gì về throttle nên vẫn giữ đúng một lời gọi service mỗi endpoint.

## Consequences

- Sau reverse proxy cần bật `ForwardedHeaderFilter` ở tầng triển khai (ứng dụng cố ý không tự đăng ký) để `getRemoteAddr()` trả IP thật; nếu không mọi client dùng chung một xô 30 request/giờ và có thể chặn oan toàn hệ thống.
- Bộ đếm mất khi restart và không chia sẻ giữa các instance: đây là lớp phụ, rate limit ở proxy/ingress vẫn là lớp chính khi triển khai nhiều instance.
- `forgot-password` bị chặn vẫn trả 200 im lặng (không lộ email tồn tại), còn `register` bị chặn trả 429 để client biết lùi lại — hai hành vi này là contract, test ở `IpRateLimitFilterTest`.

## Considered Options

- **Đặt throttle trong service**: service phải trả về hai loại kết quả HTTP khác nhau và controller phải đọc request, phá vỡ ranh giới "controller chỉ gọi một service method" của kiến trúc phân tầng.
- **Chỉ dựa vào rate limit ở proxy**: không có tác dụng khi chạy trực tiếp hoặc trong môi trường dev; lớp trong ứng dụng vẫn cần để bảo vệ endpoint gửi mail.
- **Bucket4j/Redis**: chính xác hơn nhưng thêm dependency và hạ tầng cho một lớp phụ; có thể thay sau nếu triển khai nhiều instance.