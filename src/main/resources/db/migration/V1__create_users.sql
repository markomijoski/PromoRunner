CREATE TABLE users (
    id                    BIGSERIAL PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL UNIQUE,
    display_name          VARCHAR(255),
    is_email_verified     BOOLEAN NOT NULL DEFAULT false,
    free_plays_remaining  INTEGER DEFAULT 3,
    role                  VARCHAR(20) NOT NULL DEFAULT 'PLAYER',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at          TIMESTAMPTZ
);
