-- Remove IP storage from marketing consents (privacy; also avoids INET/varchar mismatch).
ALTER TABLE marketing_consents DROP COLUMN IF EXISTS ip_address;
