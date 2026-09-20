CREATE TABLE marketing_consents (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    consented        BOOLEAN NOT NULL,
    consent_version  VARCHAR(20) NOT NULL DEFAULT '1.0',
    ip_address       INET,
    user_agent       TEXT,
    consented_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    withdrawn_at     TIMESTAMPTZ
);

CREATE INDEX idx_consents_user_id ON marketing_consents(user_id);
