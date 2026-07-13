CREATE TABLE users (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email          citext NOT NULL UNIQUE,
    password_hash  text NOT NULL,
    email_verified boolean NOT NULL DEFAULT false,
    deleted_at     timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    modified_at    timestamptz NOT NULL DEFAULT now()
);
