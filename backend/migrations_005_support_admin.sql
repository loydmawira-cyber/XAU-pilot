-- XAU Pilot support messaging and platform administration additions.
-- Safe to run on the existing Neon PostgreSQL database; all operations are idempotent.
CREATE TABLE IF NOT EXISTS support_conversations (
    id VARCHAR(36) PRIMARY KEY,
    owner_user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject VARCHAR(180) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assigned_admin_id VARCHAR(36) NULL REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_support_conversations_owner_user_id ON support_conversations(owner_user_id);
CREATE INDEX IF NOT EXISTS ix_support_conversations_status ON support_conversations(status);
CREATE INDEX IF NOT EXISTS ix_support_conversations_assigned_admin_id ON support_conversations(assigned_admin_id);
CREATE TABLE IF NOT EXISTS support_messages (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL REFERENCES support_conversations(id) ON DELETE CASCADE,
    sender_user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_support_messages_conversation_id ON support_messages(conversation_id);
CREATE INDEX IF NOT EXISTS ix_support_messages_sender_user_id ON support_messages(sender_user_id);
