-- citext: case-insensitive text type (used by users.email, teams.name)
-- pgcrypto: provides gen_random_uuid() for server-generated UUID primary keys
CREATE EXTENSION IF NOT EXISTS citext;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
