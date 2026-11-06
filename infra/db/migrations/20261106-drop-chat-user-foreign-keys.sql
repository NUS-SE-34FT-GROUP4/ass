-- Report item #7: remove Chat-to-Core foreign keys without deleting messages.
-- Run as the database administrator, NOT as chat_svc (which has no DDL grant).
-- Existing installations: run this migration, never re-run destructive init.sql.
USE trade;

-- Discover names so deployments with renamed constraints are also supported.
-- Leave every other table, index, column and foreign key unchanged.
SET @chat_fk_drops = (
    SELECT GROUP_CONCAT(
        CONCAT('DROP FOREIGN KEY `', REPLACE(CONSTRAINT_NAME, '`', '``'), '`')
        ORDER BY CONSTRAINT_NAME SEPARATOR ', '
    )
    FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'chat_message'
      AND REFERENCED_TABLE_NAME = 'users'
      AND UNIQUE_CONSTRAINT_SCHEMA = DATABASE()
);
SET @chat_fk_sql = IF(
    @chat_fk_drops IS NULL,
    'SELECT ''Chat user foreign keys already absent'' AS migration_status',
    CONCAT('ALTER TABLE `chat_message` ', @chat_fk_drops)
);
PREPARE chat_fk_stmt FROM @chat_fk_sql;
EXECUTE chat_fk_stmt;
DEALLOCATE PREPARE chat_fk_stmt;
