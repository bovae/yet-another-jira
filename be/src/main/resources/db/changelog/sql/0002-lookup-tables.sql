CREATE TABLE ticket_types (
    code text PRIMARY KEY
);

INSERT INTO ticket_types (code)
VALUES ('bug'),
       ('feature'),
       ('fix');

CREATE TABLE ticket_states (
    code     text PRIMARY KEY,
    position integer NOT NULL UNIQUE
);

INSERT INTO ticket_states (code, position)
VALUES ('new', 1),
       ('ready_for_implementation', 2),
       ('in_progress', 3),
       ('ready_for_acceptance', 4),
       ('done', 5);
