-- Public username (display_name) uniqueness + leaderboard opt-out + session cascade on user delete

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS leaderboard_visible BOOLEAN NOT NULL DEFAULT true;

-- Case-insensitive unique usernames when set
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_display_name_lower
    ON users (lower(display_name))
    WHERE display_name IS NOT NULL;

-- Allow deleting a user to remove their game sessions
ALTER TABLE game_sessions
    DROP CONSTRAINT IF EXISTS game_sessions_user_id_fkey;

ALTER TABLE game_sessions
    ADD CONSTRAINT game_sessions_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
