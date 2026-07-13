CREATE TABLE verification_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  text NOT NULL,
    purpose     text NOT NULL,
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_verification_tokens_user_id ON verification_tokens (user_id);
CREATE INDEX idx_verification_tokens_token_hash ON verification_tokens (token_hash);
