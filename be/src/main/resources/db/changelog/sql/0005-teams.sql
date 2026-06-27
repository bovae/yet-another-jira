CREATE TABLE teams (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        citext NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    modified_at timestamptz NOT NULL DEFAULT now()
);
