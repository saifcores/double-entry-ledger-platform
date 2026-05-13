INSERT INTO roles (id, name) VALUES
    ('a0000001-0000-4000-8000-000000000001', 'ADMIN'),
    ('a0000001-0000-4000-8000-000000000002', 'USER'),
    ('a0000001-0000-4000-8000-000000000003', 'MERCHANT'),
    ('a0000001-0000-4000-8000-000000000004', 'OPERATIONS'),
    ('a0000001-0000-4000-8000-000000000005', 'COMPLIANCE')
ON CONFLICT (name) DO NOTHING;

INSERT INTO accounts (id, code, name, type, currency, frozen, balance_minor)
VALUES
    ('b0000001-0000-4000-8000-000000000001', 'PROVIDER_CLEARING_USD',
        'Provider clearing (in-flight incoming)', 'ASSET', 'USD', FALSE, 0),
    ('b0000001-0000-4000-8000-000000000002', 'PROVIDER_SETTLEMENT_USD',
        'Provider settlement (outgoing withdrawals)', 'ASSET', 'USD', FALSE, 0),
    ('b0000001-0000-4000-8000-000000000003', 'PLATFORM_FEE_REVENUE_USD',
        'Platform fee revenue', 'REVENUE', 'USD', FALSE, 0),
    ('b0000001-0000-4000-8000-000000000004', 'SUSPENSE_USD',
        'Suspense / reconciliation buffer', 'ASSET', 'USD', FALSE, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO accounts (id, code, name, type, currency, frozen, balance_minor)
VALUES
    ('b0000002-0000-4000-8000-000000000001', 'PROVIDER_CLEARING_EUR',
        'Provider clearing (in-flight incoming)', 'ASSET', 'EUR', FALSE, 0),
    ('b0000002-0000-4000-8000-000000000002', 'PROVIDER_SETTLEMENT_EUR',
        'Provider settlement (outgoing withdrawals)', 'ASSET', 'EUR', FALSE, 0),
    ('b0000002-0000-4000-8000-000000000003', 'PLATFORM_FEE_REVENUE_EUR',
        'Platform fee revenue', 'REVENUE', 'EUR', FALSE, 0),
    ('b0000002-0000-4000-8000-000000000004', 'SUSPENSE_EUR',
        'Suspense / reconciliation buffer', 'ASSET', 'EUR', FALSE, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO wallets (id, user_id, account_id, external_ref, currency, label,
    frozen, fraud_locked)
VALUES
    ('c0000001-0000-4000-8000-000000000001', NULL,
        'b0000001-0000-4000-8000-000000000001', 'SYSTEM_CLEARING_USD', 'USD',
        'System clearing wallet', FALSE, FALSE),
    ('c0000001-0000-4000-8000-000000000002', NULL,
        'b0000001-0000-4000-8000-000000000002', 'SYSTEM_SETTLEMENT_USD', 'USD',
        'System settlement wallet', FALSE, FALSE),
    ('c0000002-0000-4000-8000-000000000001', NULL,
        'b0000002-0000-4000-8000-000000000001', 'SYSTEM_CLEARING_EUR', 'EUR',
        'System clearing wallet', FALSE, FALSE),
    ('c0000002-0000-4000-8000-000000000002', NULL,
        'b0000002-0000-4000-8000-000000000002', 'SYSTEM_SETTLEMENT_EUR', 'EUR',
        'System settlement wallet', FALSE, FALSE)
ON CONFLICT (id) DO NOTHING;
