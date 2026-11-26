--liquibase formatted sql

--changeset time-deposit-kata:data-demo context:demo
--comment: Sample data covering each plan and the interest boundaries (30, 45, 365 days). Only runs with the "demo" context.
INSERT INTO time_deposits (plan_type, days, balance) VALUES
    ('basic',   30,   10000.00),
    ('basic',   45, 1234567.00),
    ('student', 200,  5000.00),
    ('student', 400,  5000.00),
    ('premium', 45,  20000.00),
    ('premium', 90,  20000.00);

INSERT INTO withdrawals (time_deposit_id, amount, date)
SELECT id, 250.00, DATE '2026-01-15' FROM time_deposits WHERE plan_type = 'basic' AND days = 45;

INSERT INTO withdrawals (time_deposit_id, amount, date)
SELECT id, 100.00, DATE '2026-02-01' FROM time_deposits WHERE plan_type = 'premium' AND days = 90;

INSERT INTO withdrawals (time_deposit_id, amount, date)
SELECT id, 50.00, DATE '2026-03-10' FROM time_deposits WHERE plan_type = 'premium' AND days = 90;
--rollback DELETE FROM withdrawals; DELETE FROM time_deposits;
