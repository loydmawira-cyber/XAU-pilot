-- Enforce a single platform Super Admin. Resolve existing duplicates before applying.
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_single_super_administrator
    ON users (role)
    WHERE role = 'SUPER_ADMINISTRATOR';
