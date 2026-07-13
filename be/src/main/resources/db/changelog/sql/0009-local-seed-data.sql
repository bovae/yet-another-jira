-- Local-only demo seed (Liquibase context: local)
-- Demo login: demo@yaj.local / Demo1234!
--
-- Timestamps are hardcoded but staggered so the seed reads like real activity and the board's
-- modified-first ordering is meaningful: user/teams mid-June 2026, epics a few days later, tickets
-- spread over the following two weeks (distinct created_at each), modified_at bumped forward by
-- workflow progress (further-along states were touched more recently), comments an hour after their
-- ticket. All values are ≤ the current demo date; do not switch to now()-interval — the seed must
-- stay deterministic.

INSERT INTO users (id, email, password_hash, email_verified, created_at, modified_at)
VALUES ('00000000-0000-0000-0000-000000000001',
        'demo@yaj.local',
        '$argon2id$v=19$m=16384,t=2,p=1$POAl7vWV1KrkP5n+0wDD5g$1+6SQ/71NaII+gaut62KffyDSw6m+DsPbOjsME66Uvg',
        true,
        '2026-06-15 09:00:00+00',
        '2026-06-15 09:00:00+00');

INSERT INTO teams (id, name, created_at, modified_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'Web Platform', '2026-06-15 10:00:00+00', '2026-06-15 10:00:00+00'),
       ('22222222-2222-2222-2222-222222222222', 'Mobile App', '2026-06-16 11:00:00+00', '2026-06-16 11:00:00+00'),
       ('33333333-3333-3333-3333-333333333333', 'Payments', '2026-06-17 14:00:00+00', '2026-06-17 14:00:00+00');

INSERT INTO epics (id, team_id, title, description, created_at, modified_at)
VALUES ('a1000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'User Onboarding', 'Guide new users through their first session.', '2026-06-18 09:00:00+00', '2026-06-18 09:00:00+00'),
       ('a1000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Search & Discovery', 'Make tickets easy to find across the workspace.', '2026-06-19 10:00:00+00', '2026-06-19 10:00:00+00'),
       ('a2000000-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Offline Mode', 'Keep the app usable without a connection.', '2026-06-20 09:00:00+00', '2026-06-20 09:00:00+00'),
       ('a2000000-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'Push Notifications', 'Timely alerts for assignments and mentions.', '2026-06-21 10:00:00+00', '2026-06-21 10:00:00+00'),
       ('a3000000-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333', 'Checkout Flow', 'Smooth, fast, and reliable payment checkout.', '2026-06-22 09:00:00+00', '2026-06-22 09:00:00+00'),
       ('a3000000-0000-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333', 'Fraud Detection', 'Catch fraudulent transactions before capture.', '2026-06-23 10:00:00+00', '2026-06-23 10:00:00+00');

-- Tickets: every team gets one ticket in each of the five board states, mixed types, some epic-linked, some not.
-- created_at is distinct per row; modified_at = created_at + one day per workflow step, so more-advanced
-- states read as more recently touched (and the modified-first board order is non-trivial).
INSERT INTO tickets (id, team_id, epic_id, type, state, title, body, created_by, created_at, modified_at)
VALUES
  -- Web Platform
  ('b1000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'a1000000-0000-0000-0000-000000000001', 'feature', 'new', 'Add welcome tour for new users', 'Interactive first-run tour highlighting the board, teams, and ticket creation.', '00000000-0000-0000-0000-000000000001', '2026-06-24 09:15:00+00', '2026-06-24 09:15:00+00'),
  ('b1000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', NULL, 'bug', 'ready_for_implementation', 'Login button misaligned on Safari', 'The primary login button overflows its container on Safari 17.', '00000000-0000-0000-0000-000000000001', '2026-06-25 10:30:00+00', '2026-06-26 10:30:00+00'),
  ('b1000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'a1000000-0000-0000-0000-000000000002', 'feature', 'in_progress', 'Autocomplete in global search', 'Suggest tickets and epics as the user types in the global search box.', '00000000-0000-0000-0000-000000000001', '2026-06-26 11:45:00+00', '2026-06-28 11:45:00+00'),
  ('b1000000-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'a1000000-0000-0000-0000-000000000001', 'fix', 'ready_for_acceptance', 'Persist onboarding progress across sessions', 'Onboarding steps completed should survive a page reload.', '00000000-0000-0000-0000-000000000001', '2026-06-27 14:20:00+00', '2026-06-30 14:20:00+00'),
  ('b1000000-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', NULL, 'bug', 'done', 'Fix 500 on empty search query', 'An empty search term returned a 500 instead of the full board.', '00000000-0000-0000-0000-000000000001', '2026-06-28 16:05:00+00', '2026-07-02 16:05:00+00'),
  -- Mobile App
  ('b2000000-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'a2000000-0000-0000-0000-000000000001', 'feature', 'new', 'Cache board data for offline viewing', 'Persist the last-seen board so it renders without a connection.', '00000000-0000-0000-0000-000000000001', '2026-06-29 09:10:00+00', '2026-06-29 09:10:00+00'),
  ('b2000000-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'a2000000-0000-0000-0000-000000000002', 'feature', 'ready_for_implementation', 'Notify on ticket assignment', 'Send a push notification when a ticket is assigned to the user.', '00000000-0000-0000-0000-000000000001', '2026-06-30 10:25:00+00', '2026-07-01 10:25:00+00'),
  ('b2000000-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', NULL, 'bug', 'in_progress', 'App crashes on rotate in board view', 'Rotating the device on the board screen crashes the app.', '00000000-0000-0000-0000-000000000001', '2026-07-01 11:40:00+00', '2026-07-03 11:40:00+00'),
  ('b2000000-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222', 'a2000000-0000-0000-0000-000000000001', 'fix', 'ready_for_acceptance', 'Sync queued edits when back online', 'Edits made offline should replay in order once connectivity returns.', '00000000-0000-0000-0000-000000000001', '2026-07-02 13:15:00+00', '2026-07-05 13:15:00+00'),
  ('b2000000-0000-0000-0000-000000000005', '22222222-2222-2222-2222-222222222222', NULL, 'fix', 'done', 'Reduce cold-start time', 'Trimmed startup work to cut cold start from 3.2s to 1.4s.', '00000000-0000-0000-0000-000000000001', '2026-07-03 15:50:00+00', '2026-07-07 15:50:00+00'),
  -- Payments
  ('b3000000-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333', 'a3000000-0000-0000-0000-000000000001', 'feature', 'new', 'Support Apple Pay at checkout', 'Add Apple Pay as a one-tap payment option at checkout.', '00000000-0000-0000-0000-000000000001', '2026-07-04 09:05:00+00', '2026-07-04 09:05:00+00'),
  ('b3000000-0000-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333', 'a3000000-0000-0000-0000-000000000002', 'bug', 'ready_for_implementation', 'False positive on repeated small charges', 'Legitimate repeated micro-charges are wrongly flagged as fraud.', '00000000-0000-0000-0000-000000000001', '2026-07-05 10:20:00+00', '2026-07-06 10:20:00+00'),
  ('b3000000-0000-0000-0000-000000000003', '33333333-3333-3333-3333-333333333333', 'a3000000-0000-0000-0000-000000000002', 'feature', 'in_progress', 'Score transactions with risk model', 'Assign a risk score to each transaction before capture.', '00000000-0000-0000-0000-000000000001', '2026-07-06 11:35:00+00', '2026-07-08 11:35:00+00'),
  ('b3000000-0000-0000-0000-000000000004', '33333333-3333-3333-3333-333333333333', 'a3000000-0000-0000-0000-000000000001', 'fix', 'ready_for_acceptance', 'Handle expired card mid-checkout', 'Prompt for a new card instead of failing the whole checkout.', '00000000-0000-0000-0000-000000000001', '2026-07-07 13:30:00+00', '2026-07-10 13:30:00+00'),
  ('b3000000-0000-0000-0000-000000000005', '33333333-3333-3333-3333-333333333333', NULL, 'bug', 'done', 'Rounding error in tax calculation', 'Tax was rounded per line item instead of on the order total.', '00000000-0000-0000-0000-000000000001', '2026-07-08 15:45:00+00', '2026-07-12 15:45:00+00');

-- One comment per ticket, authored by the demo user, an hour after the ticket was created.
INSERT INTO comments (id, ticket_id, author_id, body, created_at)
VALUES
  ('c1000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'Let''s keep the tour to three steps max.', '2026-06-24 10:15:00+00'),
  ('c1000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'Reproduced on Safari 17.4, fine on Chrome.', '2026-06-25 11:30:00+00'),
  ('c1000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'Debounce the suggest query by 200ms.', '2026-06-26 12:45:00+00'),
  ('c1000000-0000-0000-0000-000000000004', 'b1000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'Verified progress survives reload — ready for QA.', '2026-06-27 15:20:00+00'),
  ('c1000000-0000-0000-0000-000000000005', 'b1000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001', 'Shipped in last release; added a regression test.', '2026-06-28 17:05:00+00'),
  ('c2000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'Cap the cache at the 20 most recent boards.', '2026-06-29 10:10:00+00'),
  ('c2000000-0000-0000-0000-000000000002', 'b2000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'Needs the notification-permission prompt first.', '2026-06-30 11:25:00+00'),
  ('c2000000-0000-0000-0000-000000000003', 'b2000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'Crash is in the layout pass on config change.', '2026-07-01 12:40:00+00'),
  ('c2000000-0000-0000-0000-000000000004', 'b2000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'Replay must be idempotent — dedupe by edit id.', '2026-07-02 14:15:00+00'),
  ('c2000000-0000-0000-0000-000000000005', 'b2000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001', 'Deferred heavy work off the startup path.', '2026-07-03 16:50:00+00'),
  ('c3000000-0000-0000-0000-000000000001', 'b3000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'Coordinate the merchant ID with the ops team.', '2026-07-04 10:05:00+00'),
  ('c3000000-0000-0000-0000-000000000002', 'b3000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'Whitelist recurring merchants under a threshold.', '2026-07-05 11:20:00+00'),
  ('c3000000-0000-0000-0000-000000000003', 'b3000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'Start with a simple rules model, iterate later.', '2026-07-06 12:35:00+00'),
  ('c3000000-0000-0000-0000-000000000004', 'b3000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'Keep the cart intact while re-prompting.', '2026-07-07 14:30:00+00'),
  ('c3000000-0000-0000-0000-000000000005', 'b3000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001', 'Fixed by rounding once on the order total.', '2026-07-08 16:45:00+00');
