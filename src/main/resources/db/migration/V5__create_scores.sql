CREATE TABLE scores (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE UNIQUE,
    best_score     INTEGER NOT NULL DEFAULT 0,
    total_plays    INTEGER NOT NULL DEFAULT 0,
    last_played_at TIMESTAMPTZ
);

CREATE INDEX idx_scores_best ON scores(best_score DESC);
