#!/usr/bin/env bash
#
# Chạy MỘT LẦN khi volume mysql-data còn trống (MySQL entrypoint quét
# /docker-entrypoint-initdb.d). Database/ user của ứng dụng (`lenglish` /
# `lenglish_app`) do image tạo sẵn qua MYSQL_DATABASE/MYSQL_USER/MYSQL_PASSWORD;
# script này bổ sung database + tài khoản riêng cho tầng integration test.
#
# Muốn chạy lại: docker compose down -v  (XOÁ toàn bộ dữ liệu local).
set -euo pipefail

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD chưa được đặt}"
: "${TEST_DB_NAME:=lenglish_test}"
: "${TEST_DB_USERNAME:=lenglish_test}"
: "${TEST_DB_PASSWORD:?TEST_DB_PASSWORD chưa được đặt}"

# Tài khoản test chỉ có quyền DML/DDL trên đúng database test — không cấp DROP,
# không cấp quyền toàn server, không dùng root (theo HUONG_DAN_TEST_MYSQL.md §2).
mysql --user=root --password="${MYSQL_ROOT_PASSWORD}" --protocol=socket <<SQL
CREATE DATABASE IF NOT EXISTS ${TEST_DB_NAME}
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS '${TEST_DB_USERNAME}'@'%' IDENTIFIED BY '${TEST_DB_PASSWORD}';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON ${TEST_DB_NAME}.* TO '${TEST_DB_USERNAME}'@'%';

FLUSH PRIVILEGES;
SQL

echo "[init] Đã tạo database '${TEST_DB_NAME}' và user '${TEST_DB_USERNAME}'."
