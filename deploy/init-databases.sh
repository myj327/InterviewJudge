#!/bin/bash
set -e

export MYSQL_PWD="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"

mysql --protocol=socket -uroot <<'EOSQL'
CREATE DATABASE IF NOT EXISTS doj_user CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS doj_problem CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS doj_submission CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
EOSQL

mysql --protocol=socket -uroot --database=doj_user < /init-sql/doj_user.sql
mysql --protocol=socket -uroot --database=doj_problem < /init-sql/doj_problem.sql
mysql --protocol=socket -uroot --database=doj_submission < /init-sql/doj_submission.sql

unset MYSQL_PWD
