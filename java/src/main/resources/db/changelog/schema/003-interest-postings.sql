--liquibase formatted sql

--changeset time-deposit-kata:schema-003-interest-postings
--comment: Append-only audit trail (journal) of interest postings: one row per deposit per month, written in the same
--comment: transaction as the balance change. The unique (deposit, period) key also makes the balance update idempotent per month.

-- Ids are reserved 500 at a time (must match allocationSize = 500 in InterestPostingJpaEntity), so the inserts of an
-- interest run can be JDBC-batched with few sequence round trips. Measured on 40,000 deposits: 80 round trips instead
-- of 800 with steps of 50. Gaps in the ids are fine for a surrogate key.
CREATE SEQUENCE interest_postings_seq START WITH 1 INCREMENT BY 500;

CREATE TABLE interest_postings (
    id              BIGINT         PRIMARY KEY,
    time_deposit_id INTEGER        NOT NULL REFERENCES time_deposits (id),
    period          DATE           NOT NULL, -- first day of the month the interest is for
    balance_before  NUMERIC(19, 2) NOT NULL,
    balance_after   NUMERIC(19, 2) NOT NULL,
    interest        NUMERIC(19, 2) NOT NULL,
    posted_at       TIMESTAMPTZ    NOT NULL,

    CONSTRAINT uq_interest_postings_deposit_period UNIQUE (time_deposit_id, period),
    CONSTRAINT chk_interest_postings_period_first_day CHECK (EXTRACT(DAY FROM period) = 1),
    CONSTRAINT chk_interest_postings_interest CHECK (interest = balance_after - balance_before)
);

-- The balance update looks up "which deposits are already posted for this month" by period alone.
CREATE INDEX idx_interest_postings_period ON interest_postings (period);
--rollback DROP TABLE interest_postings; DROP SEQUENCE interest_postings_seq;
