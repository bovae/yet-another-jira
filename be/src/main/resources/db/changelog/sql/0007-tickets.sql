CREATE TABLE tickets (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id     uuid NOT NULL REFERENCES teams (id) ON DELETE RESTRICT,
    epic_id     uuid REFERENCES epics (id) ON DELETE RESTRICT,
    type        text NOT NULL REFERENCES ticket_types (code) ON DELETE RESTRICT,
    state       text NOT NULL REFERENCES ticket_states (code) ON DELETE RESTRICT,
    title       text NOT NULL,
    body        text NOT NULL,
    created_by  uuid NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at  timestamptz NOT NULL DEFAULT now(),
    modified_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_tickets_team_state ON tickets (team_id, state);
CREATE INDEX idx_tickets_epic_id ON tickets (epic_id);
CREATE INDEX idx_tickets_created_by ON tickets (created_by);
