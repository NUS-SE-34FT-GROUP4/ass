#!/usr/bin/env bash
# Regression test in a disposable local MySQL container; never uses a live DB.
set -euo pipefail
repo=$(cd "$(dirname "$0")/../.." && pwd)
container="ass-chat-fk-test-$$"
tmp_dir=$(mktemp -d)
cleanup() {
  docker rm -f "$container" >/dev/null 2>&1 || true
  rm -rf "$tmp_dir"
}
trap cleanup EXIT
docker run -d --name "$container" --network none --tmpfs /var/lib/mysql \
  -e MYSQL_ALLOW_EMPTY_PASSWORD=yes mysql:8.0.36 >/dev/null
ready=false
for ((attempt=0; attempt<90; attempt++)); do
  if docker exec "$container" mysqladmin ping --silent >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
[[ $ready == true ]] || { docker logs "$container"; exit 1; }
sql() { docker exec -i "$container" mysql -uroot --batch --skip-column-names "$@"; }
assert_sql() {
  local expected=$1 query=$2 actual
  actual=$(sql trade -e "$query")
  [[ $actual == "$expected" ]] || {
    printf 'FAIL: expected %s, got %s\nQuery: %s\n' "$expected" "$actual" "$query" >&2
    exit 1
  }
}
fk_count="SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='trade' AND TABLE_NAME='chat_message' AND REFERENCED_TABLE_NAME='users'"
sql < "$repo/init.sql" >/dev/null
assert_sql 0 "$fk_count"
assert_sql 2 "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='trade' AND TABLE_NAME='chat_message' AND INDEX_NAME IN ('idx_sender_recipient','idx_recipient_read')"
printf 'PASS fresh initialization has no Chat-to-Core foreign keys and keeps both indexes\n'

# Emulate an existing installation with non-default constraint names.
sql trade <<'SQL'
ALTER TABLE chat_message
  ADD CONSTRAINT legacy_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT legacy_recipient FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE;
INSERT INTO chat_message (sender_id,recipient_id,content,is_system_message) VALUES
  (101,102,'Buyer seller history',false),(NULL,102,'System notification',true);
SQL
assert_sql 2 "$fk_count"
sql trade -e 'SELECT * FROM chat_message ORDER BY id' > "$tmp_dir/before"
sql trade -e "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='trade' AND TABLE_NAME<>'chat_message'" > "$tmp_dir/other-fks"
sql < "$repo/infra/db/migrations/20261106-drop-chat-user-foreign-keys.sql" >/dev/null
assert_sql 0 "$fk_count"
sql trade -e 'SELECT * FROM chat_message ORDER BY id' > "$tmp_dir/after"
cmp "$tmp_dir/before" "$tmp_dir/after"
printf 'PASS migration removes legacy names without changing existing message rows\n'

sql < "$repo/infra/db/migrations/20261106-drop-chat-user-foreign-keys.sql" >/dev/null
sql trade -e 'SELECT * FROM chat_message ORDER BY id' > "$tmp_dir/repeated"
cmp "$tmp_dir/before" "$tmp_dir/repeated"
sql trade -e "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='trade' AND TABLE_NAME<>'chat_message'" > "$tmp_dir/other-fks-after"
cmp "$tmp_dir/other-fks" "$tmp_dir/other-fks-after"
printf 'PASS repeat migration is harmless and other tables retain their foreign keys\n'

# Deleting Core-owned users no longer cascades into Chat-owned history.
sql trade -e 'DELETE FROM users WHERE id IN (101,102)'
assert_sql 2 'SELECT COUNT(*) FROM chat_message'
assert_sql 1 'SELECT COUNT(*) FROM chat_message WHERE sender_id=101 AND recipient_id=102'
assert_sql 1 'SELECT COUNT(*) FROM chat_message WHERE sender_id IS NULL AND recipient_id=102 AND is_system_message=true'
assert_sql 2 "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='trade' AND TABLE_NAME='chat_message' AND INDEX_NAME IN ('idx_sender_recipient','idx_recipient_read')"
printf 'PASS user deletion preserves message IDs, system messages and indexes\n'
