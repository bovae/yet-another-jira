CREATE TABLE epics (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id     uuid NOT NULL REFERENCES teams (id) ON DELETE RESTRICT,
    title       text NOT NULL,
    description text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    modified_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_epics_team_id ON epics (team_id);
