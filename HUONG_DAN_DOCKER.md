# Dựng môi trường local bằng Docker Compose

Tài liệu này dành cho **máy mới**: chỉ cần Docker, không phải cài MySQL thủ công,
không phải tạo database/tài khoản bằng tay.

> Đây là môi trường **dev local**. Không dùng các mật khẩu trong `.env` cho
> staging/production.

---

## 1. Docker hoá những gì

| Thành phần | File | Mặc định | Khi nào dùng |
|---|---|---|---|
| `db` — MySQL 8.0.43 | `compose.yaml` | `localhost:3306` | Luôn dùng |
| `app` — Spring Boot trong container | `Dockerfile` + profile `app` | `localhost:8080` | Muốn chạy thử "một phát" không cần JDK/Maven |
| `adminer` — giao diện web xem DB | profile `tools` | `localhost:8081` | Khi muốn xem bảng bằng trình duyệt |

Các file đi kèm:

```
compose.yaml                        # định nghĩa service
Dockerfile                          # build image app (2 tầng: maven -> jre)
.dockerignore                       # giảm build context
.env                                # secret của máy này (KHÔNG commit)
.env.example                        # mẫu, có commit
docker/mysql/init/01-create-databases.sh   # tạo lenglish_test + user test
scripts/dev-env.ps1                 # nạp .env vào PowerShell
```

## 2. Yêu cầu

- **Docker Desktop** (Windows: bật WSL2 backend) hoặc Docker Engine + Compose v2.
- Chỉ khi chạy app **ngoài** container: **JDK 21** và Maven wrapper (đã có trong repo).
- Kiểm tra:

```powershell
docker --version
docker compose version
```

> Máy hiện tại (nơi soạn cấu hình này) **chưa cài Docker**, nên các lệnh dưới đây
> chưa được chạy thực tế ở đây. Cài Docker Desktop rồi chạy theo thứ tự là được.

## 3. Chuẩn bị `.env` (một lần cho mỗi máy)

Repo đã có `.env.example`. Trên máy mới:

```powershell
Copy-Item .env.example .env
```

Rồi thay các giá trị `<...>`:

| Biến | Cách tạo |
|---|---|
| `MYSQL_ROOT_PASSWORD`, `APP_DB_PASSWORD`, `TEST_DB_PASSWORD` | chuỗi ngẫu nhiên bất kỳ, **chỉ dùng chữ + số** để tránh lỗi quoting trong SQL |
| `JWT_SECRET` | Base64 của ≥ 32 byte ngẫu nhiên (xem lệnh dưới) |
| `AI_API_KEY` | key thật của bạn |
| `AI_BASE_URL` | **bắt buộc https**, không có `user:pass@`, không `?query`, không `#fragment` |

Tạo `JWT_SECRET`:

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
```

```bash
openssl rand -base64 48
```

Kiểm tra nhanh ràng buộc: chuỗi Base64 phải dài ≥ 44 ký tự (48 byte → 64 ký tự).
Sai ràng buộc thì app dừng ngay khi khởi động với
`JWT secret must be externally supplied Base64 encoding at least 32 bytes`.

**Mẹo:** `MYSQL_HOST_PORT` phải là `3306` nếu bạn muốn chạy `./mvnw verify`,
vì `MySqlTestConfiguration` chỉ chấp nhận đúng
`jdbc:mysql://localhost:3306/lenglish_test`. Máy đã có MySQL chiếm 3306 thì đổi
sang `3307` và chấp nhận không chạy được integration test.

## 4. Bật MySQL

```powershell
docker compose up -d
docker compose ps
docker compose logs -f db      # đợi dòng "ready for connections", Ctrl+C để thoát
```

Lần đầu, MySQL sẽ:

1. tạo database `lenglish` + user `lenglish_app` (quyền trên `lenglish.*`);
2. chạy `docker/mysql/init/01-create-databases.sh` để tạo `lenglish_test` +
   user `lenglish_test` (quyền DML/DDL, **không** có `DROP`, **không** phải root).

Kiểm tra:

```powershell
. .\scripts\dev-env.ps1
docker compose exec -T -e MYSQL_PWD=$env:MYSQL_ROOT_PASSWORD db mysql -uroot -e 'SHOW DATABASES; SELECT user,host FROM mysql.user;'
```

Phải thấy `lenglish` và `lenglish_test`, cùng hai user `lenglish_app`,
`lenglish_test`.

> Script trong `docker-entrypoint-initdb.d` **chỉ chạy khi volume dữ liệu còn trống**.
> Sửa script xong mà DB không đổi → phải `docker compose down -v` rồi `up -d` lại
> (xoá sạch dữ liệu local).

## 5. Chạy ứng dụng

### Cách A — chạy ngoài container (khuyến nghị khi đang code)

`application.yml` đọc biến `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`,
`AI_*`; `.env` lại đặt tên `APP_DB_*`. Script dưới đây nạp `.env` và suy ra đúng
các biến đó:

```powershell
. .\scripts\dev-env.ps1          # chú ý dấu chấm đầu dòng
.\mvnw.cmd spring-boot:run
```

Mở `http://localhost:8080/api/...` (danh sách endpoint: `API_REFERENCE.md`).

### Cách B — chạy tất cả trong Docker

```powershell
docker compose --profile app up -d --build
docker compose logs -f app
```

Lần đầu sẽ build Maven trong container (vài phút, dependency được cache ở lần sau).
Ứng dụng vẫn ở `http://localhost:8080`.

Dừng riêng app, giữ DB:

```powershell
docker compose --profile app stop app
```

### Cách C — thêm Adminer (xem DB bằng trình duyệt)

```powershell
docker compose --profile tools up -d
```

Mở `http://localhost:8081`:

- System: `MySQL`
- Server: `db`
- Username: `lenglish_app`
- Password: giá trị `APP_DB_PASSWORD`
- Database: `lenglish`

## 6. Chạy integration test (failsafe)

```powershell
. .\scripts\dev-env.ps1
.\mvnw.cmd clean verify
```

Script đặt sẵn `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` đúng như
`HUONG_DAN_TEST_MYSQL.md` yêu cầu. Unit test (`.\mvnw.cmd test`) không cần DB.

## 7. Lệnh hay dùng

| Việc | Lệnh |
|---|---|
| Bật MySQL | `docker compose up -d` |
| Xem trạng thái | `docker compose ps` |
| Xem log DB | `docker compose logs -f db` |
| Vào shell MySQL | `docker compose exec db sh -lc 'mysql -uroot -p$MYSQL_ROOT_PASSWORD'` |
| Chạy một câu SQL từ PowerShell | `docker compose exec -T -e MYSQL_PWD=$env:MYSQL_ROOT_PASSWORD db mysql -uroot -e 'SHOW DATABASES; SELECT user,host FROM mysql.user;'` |
| Đưa file `.sql` vào DB | `Get-Content .\seed.sql \| docker compose exec -T db sh -lc 'mysql -uroot -p$MYSQL_ROOT_PASSWORD lenglish'` |
| Build lại app | `docker compose --profile app up -d --build` |
| Dừng, giữ dữ liệu | `docker compose down` |
| Dừng, **xoá dữ liệu** | `docker compose down -v` |
| Kiểm tra file compose | `docker compose config` |
| Xem biến đã nội suy | `docker compose config --quiet` |

### 7.1 Vì sao lệnh MySQL hay lỗi `syntax error ... near ''` trong PowerShell

PowerShell 5.1 (bản có sẵn của Windows) **bóc mất dấu nháy kép** khi truyền đối số
cho chương trình native như `docker`. Hệ quả với lệnh có hai tầng nháy:

```powershell
# SAI — bash bên trong chỉ nhận được "-e SHOW", phần "DATABASES" bị cắt
docker compose exec db sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW DATABASES"'
```

`mysql -e SHOW` → `ERROR 1064 (42000) ... near '' at line 1`. Đây **không** phải lỗi
mật khẩu. Bạn có thể tự thấy bash nhận gì:

```powershell
docker compose exec -T db sh -lc 'printf "[%s]\n" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW DATABASES"'
# -> [mysql] [-uroot] [-p<matkhau>] [-e] [SHOW]     <- mất "DATABASES"
```

Quy tắc: **đừng lồng nháy kép** trong một đối số. Chọn một trong hai:

```powershell
# Cách 1 — truyền mật khẩu bằng biến môi trường MYSQL_PWD, SQL để trong nháy đơn
. .\scripts\dev-env.ps1
docker compose exec -T -e MYSQL_PWD=$env:MYSQL_ROOT_PASSWORD db mysql -uroot -e 'SHOW DATABASES; SELECT user,host FROM mysql.user;'

# Cách 2 — đẩy SQL qua stdin, không dùng -e
'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE();' |
  docker compose exec -T db sh -lc 'mysql -uroot -p$MYSQL_ROOT_PASSWORD lenglish'
```

Nếu SQL của bạn cần chuỗi trong nháy đơn (ví dụ `WHERE english = 'apple'`), hãy dùng
Cách 2 và đưa SQL qua `Get-Content` từ một file `.sql` — không phải lo quoting nữa.

> Mẹo: PowerShell 7 (`pwsh`) đã sửa hành vi này (`PSNativeCommandArgumentPassing`).
> Nếu bạn dùng `pwsh` thay cho `powershell`, các lệnh lồng nháy kép ở trên chạy đúng.

## 8. Reset toàn bộ

Khi schema lệch, quên mật khẩu, hoặc muốn thử lại migration từ đầu:

```powershell
docker compose down -v
docker compose up -d
```

`-v` xoá volume `mysql-data`; Flyway sẽ chạy lại `V1__init_schema.sql` trên DB trống.

## 9. Sự cố thường gặp

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| `port is already allocated` (3306) | Máy đã có MySQL. Đổi `MYSQL_HOST_PORT=3307` trong `.env`, hoặc tắt service MySQL của Windows |
| `Public Key Retrieval is not allowed` | Connector/J không lấy được public key khi không dùng TLS. Thêm `&allowPublicKeyRetrieval=true` vào `DB_URL` **chỉ cho local**, hoặc bật TLS cho MySQL. Tài liệu dự án (`HUONG_DAN_TEST_MYSQL.md` §5) khuyên không bật tràn lan |
| `Access denied for user` | Sai mật khẩu trong `.env`; hoặc volume cũ được tạo từ `.env` trước đó → `down -v` rồi `up -d` |
| `Unknown database 'lenglish_test'` | Init script chưa chạy (volume không trống) → `down -v`, `up -d` |
| Đã sửa `01-create-databases.sh` mà không có tác dụng | Như trên: init script chỉ chạy một lần |
| `bash: ...\r: command not found` khi chạy script init | File bị CRLF. `.gitattributes` đã ép `*.sh` = LF; nếu vẫn lỗi, chuyển file về LF |
| `./mvnw verify` báo `TEST_DB_URL must use the documented localhost:3306/...` | `MYSQL_HOST_PORT` khác 3306, hoặc đặt `TEST_DB_URL` sai. Guard cố ý từ chối |
| App trong container khởi động rồi thoát | `docker compose logs app` — thường do thiếu `JWT_SECRET` / `AI_*` trong `.env` |
| `docker compose` báo thiếu biến | `.env` thiếu key; so lại với `.env.example` |
| Build image lỗi ở `RUN --mount=type=cache` | Docker cũ không bật BuildKit. Bật BuildKit hoặc bỏ tuỳ chọn `--mount` trong `Dockerfile` |
| `ERROR 1045 ... (using password: YES)` dù mật khẩu đúng | Khi viết `docker compose exec db mysql -uroot -p<matkhau>`, docker nuốt `-p` thành cờ `--privileged` của chính nó nên `mysql` không nhận được mật khẩu. Dùng `MYSQL_PWD` như dòng cuối §7, hoặc bọc trong `sh -lc 'mysql -uroot -p$MYSQL_ROOT_PASSWORD'` |
| `ERROR 1064 (42000) ... near '' at line 1` khi chạy SQL | PowerShell 5.1 bóc mất nháy kép lồng, `-e "SHOW DATABASES"` chỉ còn `-e SHOW`. Xem §7.1 — bỏ nháy kép, dùng `MYSQL_PWD` + nháy đơn, hoặc đẩy SQL qua stdin |
| `ports are not available: ... 0.0.0.0:8080` khi `--profile app` | Đang có tiến trình khác giữ 8080 (thường là `.\mvnw.cmd spring-boot:run`). Tắt nó, hoặc đặt `APP_HOST_PORT=8082` trong `.env` |
| `mvnw verify` báo `Duplicate entry '1' for key 'word_cache.PRIMARY'` | Rác `word_cache` do một lần chạy trước để lại. Đã xử lý gốc trong `ApiEndToEndIT` (dọn `word_cache` ở `@AfterAll`). Nếu vẫn gặp, xoá tay: `docker compose exec -T -e MYSQL_PWD=$env:MYSQL_ROOT_PASSWORD db mysql -uroot -e "DELETE FROM lenglish_test.word_cache"` |

## 10. Bảo mật

- `.env` đã nằm trong `.gitignore` — **không** commit, không gửi qua chat.
- Mật khẩu trong `.env` chỉ dùng cho container local; DB không mở ra ngoài LAN
  nếu bạn không sửa `ports`.
- `lenglish_test` **không** phải root và **không** có quyền `DROP`, đúng nguyên tắc
  trong `HUONG_DAN_TEST_MYSQL.md` §2.
- `docker compose down -v` xoá toàn bộ dữ liệu local, không thể hoàn tác.
