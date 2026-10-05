-- Idempotent winner notification audit (unique place_rank prevents double-send)
CREATE TABLE campaign_winner_notifications (
    id             BIGSERIAL PRIMARY KEY,
    place_rank     INTEGER NOT NULL UNIQUE,
    user_id        BIGINT REFERENCES users (id),
    prize_name     VARCHAR(255) NOT NULL,
    email_sent_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_campaign_winner_notifications_user_id
    ON campaign_winner_notifications (user_id);
