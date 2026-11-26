--liquibase formatted sql

--changeset time-deposit-kata:data-load-test context:loadtest
--comment: Realistic volume for load testing (10,000 deposits, 30,000 withdrawals). Only runs with the "loadtest" context.
INSERT INTO time_deposits (plan_type, days, balance)
SELECT (ARRAY['basic', 'student', 'premium'])[1 + (g % 3)],
       1 + (g % 500),
       round((1000 + random() * 99000)::numeric, 2)
FROM generate_series(1, 10000) AS g;

INSERT INTO withdrawals (time_deposit_id, amount, date)
SELECT t.id,
       round((1 + random() * 499)::numeric, 2),
       DATE '2026-01-01' + (w * 30)
FROM time_deposits t
CROSS JOIN generate_series(1, 3) AS w;
--rollback DELETE FROM withdrawals; DELETE FROM time_deposits;
