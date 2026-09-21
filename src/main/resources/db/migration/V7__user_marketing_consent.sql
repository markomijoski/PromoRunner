ALTER TABLE users
    ADD COLUMN IF NOT EXISTS marketing_consent BOOLEAN NOT NULL DEFAULT false;

UPDATE users u
SET marketing_consent = true
WHERE EXISTS (
    SELECT 1
    FROM marketing_consents c
    WHERE c.user_id = u.id
      AND c.consented = true
      AND c.withdrawn_at IS NULL
      AND c.consented_at = (
          SELECT max(c2.consented_at)
          FROM marketing_consents c2
          WHERE c2.user_id = c.user_id
      )
);

DROP TABLE IF EXISTS marketing_consents;
