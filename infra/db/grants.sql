-- Per-service database users (WP8, agreed at the 10/11 meeting).
--
--   infra/scripts/run-sql.sh staging infra/db/grants.sql
--
-- Run it after init.sql: MySQL refuses a table grant on a table that does not
-- exist. Passwords are placeholders that the db-admin task fills in from
-- Secrets Manager, so none is stored here. The script can be re-run: each user
-- is reset to exactly the grants below, so removing a line here removes the
-- privilege on the next run.
--
-- All three services share the trade schema. No service user gets DDL; the
-- tables are created by init.sql as the master user.

-- ---------------------------------------------------------------- core_svc
-- Owns every table except chat_message, which belongs to Chat: Core has no
-- privilege on it.
CREATE USER IF NOT EXISTS 'core_svc'@'%' IDENTIFIED BY '@@core_svc_password@@';
ALTER USER 'core_svc'@'%' IDENTIFIED BY '@@core_svc_password@@';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'core_svc'@'%';

GRANT SELECT, INSERT, UPDATE, DELETE ON trade.users            TO 'core_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON trade.roles            TO 'core_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON trade.user_roles       TO 'core_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON trade.pms_product      TO 'core_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON trade.pms_product_media TO 'core_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON trade.pms_report       TO 'core_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON trade.shopping_cart    TO 'core_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON trade.user_favorites   TO 'core_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON trade.orders           TO 'core_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON trade.order_items      TO 'core_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON trade.transactions     TO 'core_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON trade.bargain_activity TO 'core_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON trade.bargain_help     TO 'core_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON trade.review           TO 'core_svc'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON trade.credit_score     TO 'core_svc'@'%';

-- -------------------------------------------------------------- search_svc
-- Reads products for the full index rebuild; day-to-day indexing comes from
-- product events and does not touch the database.
CREATE USER IF NOT EXISTS 'search_svc'@'%' IDENTIFIED BY '@@search_svc_password@@';
ALTER USER 'search_svc'@'%' IDENTIFIED BY '@@search_svc_password@@';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'search_svc'@'%';

GRANT SELECT ON trade.pms_product TO 'search_svc'@'%';

-- ---------------------------------------------------------------- chat_svc
-- Owns chat_message (no DELETE: messages are never removed). Reads the user
-- tables to authenticate the WebSocket handshake and to show usernames.
CREATE USER IF NOT EXISTS 'chat_svc'@'%' IDENTIFIED BY '@@chat_svc_password@@';
ALTER USER 'chat_svc'@'%' IDENTIFIED BY '@@chat_svc_password@@';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'chat_svc'@'%';

GRANT SELECT, INSERT, UPDATE ON trade.chat_message TO 'chat_svc'@'%';
GRANT SELECT ON trade.users      TO 'chat_svc'@'%';
GRANT SELECT ON trade.roles      TO 'chat_svc'@'%';
GRANT SELECT ON trade.user_roles TO 'chat_svc'@'%';
