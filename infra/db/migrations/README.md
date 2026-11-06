# Database migrations

## Remove Chat user foreign keys

Report item #7 removes the two cross-service foreign keys from Chat-owned
`chat_message` to Core-owned `users`. Fresh databases use the updated `init.sql`.
Existing databases use `20261106-drop-chat-user-foreign-keys.sql` as a one-time
administrator migration. It is safe to repeat and also handles renamed foreign
keys. It does not change message rows, ID columns, indexes or other tables.

Do not re-run `init.sql` on an existing database: it drops and recreates tables.
The migration requires administrator `ALTER` access. Service accounts retain
their existing grants; no new DDL privilege is given to `chat_svc`.

For an already running, owner-approved AWS environment, the existing runner is:

```sh
infra/scripts/run-sql.sh staging infra/db/migrations/20261106-drop-chat-user-foreign-keys.sql
```

The AWS environments are currently shut down. Preparing or merging this change
does not apply it to a database; do not restart infrastructure for report work.
If the environments are restored, apply and verify the migration before marking
the deployed schema as updated. Take the normal database backup and schedule the
DDL outside active transactions because ALTER TABLE needs a metadata lock.

After application, this query must return zero rows:

```sql
SELECT CONSTRAINT_NAME
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = 'trade'
  AND TABLE_NAME = 'chat_message'
  AND REFERENCED_TABLE_NAME = 'users';
```

Validate locally without a cloud connection:

```sh
bash tests/db/chat-user-foreign-keys.sh
```

Removing the constraints also removes their former delete/update cascades.
Chat keeps the original participant IDs when Core deletes a user. History uses
LEFT JOIN for profile names, so a deleted user's name may be null; the current
conversation-partner query uses an inner join and will omit deleted users.
Retention and profile display policy remain application concerns. Chat still
has read-only profile joins and shares the `trade` schema under separate service
grants; this change does not claim full database-per-service isolation.
