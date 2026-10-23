#!/bin/sh
# MySQL may source this file; isolate shell options and credentials.
(
    set -eu
    export MYSQL_PWD="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
    mysql --protocol=socket -uroot < /init-sql/reliability.sql
    unset MYSQL_PWD
)
