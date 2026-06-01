CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    first_name VARCHAR(120),
    last_name VARCHAR(120),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    frozen BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_currency CHAR(3) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_status ON users (status);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    parent_id UUID REFERENCES accounts (id),
    frozen BOOLEAN NOT NULL DEFAULT FALSE,
    balance_minor BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_accounts_type CHECK (type IN (
        'ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'
    ))
);

CREATE INDEX idx_accounts_currency ON accounts (currency);
CREATE INDEX idx_accounts_type ON accounts (type);

CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users (id) ON DELETE RESTRICT,
    account_id UUID NOT NULL UNIQUE REFERENCES accounts (id) ON DELETE RESTRICT,
    external_ref VARCHAR(128),
    currency VARCHAR(3) NOT NULL,
    label VARCHAR(255),
    frozen BOOLEAN NOT NULL DEFAULT FALSE,
    fraud_locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_wallet_user_or_system CHECK (user_id IS NOT NULL OR external_ref IS NOT NULL)
);

CREATE UNIQUE INDEX uq_wallets_user_currency ON wallets (user_id, currency)
    WHERE user_id IS NOT NULL;

CREATE INDEX idx_wallets_account ON wallets (account_id);

CREATE UNIQUE INDEX uq_wallets_external_ref ON wallets (external_ref)
    WHERE external_ref IS NOT NULL;

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id VARCHAR(64) NOT NULL UNIQUE,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    idempotency_scope VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(128),
    related_transaction_id UUID REFERENCES transactions (id),
    description TEXT,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_transactions_idempotency UNIQUE (idempotency_scope, idempotency_key),
    CONSTRAINT chk_transactions_type CHECK (type IN (
        'DEPOSIT', 'TRANSFER', 'MERCHANT_PAYMENT', 'WITHDRAWAL',
        'REVERSAL', 'ADJUSTMENT'
    )),
    CONSTRAINT chk_transactions_status CHECK (status IN (
        'PENDING', 'POSTED', 'FAILED', 'REVERSED'
    ))
);

CREATE INDEX idx_transactions_created ON transactions (created_at DESC);
CREATE INDEX idx_transactions_correlation ON transactions (correlation_id);

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions (id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts (id) ON DELETE RESTRICT,
    direction VARCHAR(8) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL,
    memo TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_journal_direction CHECK (direction IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX idx_journal_txn ON journal_entries (transaction_id);
CREATE INDEX idx_journal_account ON journal_entries (account_id);

CREATE OR REPLACE FUNCTION prevent_journal_entry_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'journal_entries are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_journal_entries_no_update
    BEFORE UPDATE ON journal_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_journal_entry_mutation();

CREATE TRIGGER tr_journal_entries_no_delete
    BEFORE DELETE ON journal_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_journal_entry_mutation();

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID REFERENCES users (id),
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    payload JSONB,
    trace_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_resource ON audit_logs (resource_type, resource_id);
CREATE INDEX idx_audit_created ON audit_logs (created_at DESC);

CREATE TABLE provider_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(64) NOT NULL,
    provider_ref VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payload_encrypted BYTEA,
    internal_transaction_id UUID REFERENCES transactions (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_provider_ref UNIQUE (provider, provider_ref)
);

CREATE INDEX idx_provider_txn_internal ON provider_transactions (internal_transaction_id);

CREATE TABLE reconciliation_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(64) NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    mismatch_count INT NOT NULL DEFAULT 0,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recon_provider_period ON reconciliation_reports (
    provider, period_start, period_end
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope VARCHAR(64) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    fingerprint VARCHAR(128),
    response_body TEXT,
    http_status INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_idempotency UNIQUE (scope, key_hash)
);

CREATE TABLE outbox_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic VARCHAR(128) NOT NULL,
    message_key VARCHAR(128),
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    attempts INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_unpublished ON outbox_messages (published_at)
    WHERE published_at IS NULL;

CREATE TABLE ledger_balance_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    as_of TIMESTAMPTZ NOT NULL,
    balance_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_snapshots_account ON ledger_balance_snapshots (account_id);

CREATE TABLE fraud_flags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type VARCHAR(32) NOT NULL,
    subject_id UUID NOT NULL,
    reason VARCHAR(255) NOT NULL,
    severity VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fraud_subject ON fraud_flags (subject_type, subject_id);

CREATE TABLE approval_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    maker_user_id UUID NOT NULL REFERENCES users (id),
    checker_user_id UUID REFERENCES users (id),
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_approval_status CHECK (status IN (
        'PENDING', 'APPROVED', 'REJECTED'
    ))
);

CREATE INDEX idx_approval_status ON approval_requests (status, created_at DESC);
