CREATE TABLE game_sessions (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at     TIMESTAMPTZ,
    final_score  INTEGER,
    duration_ms  INTEGER,
    device_type  VARCHAR(20),
    is_completed BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_sessions_user_id ON game_sessions(user_id);
