package org.ikigaidigital;

/**
 * Shared domain model. Its public API (package, constructor, accessors and the {@code Double} balance)
 * is intentionally identical to the original kata class: the README forbids breaking changes to it,
 * because other consumers depend on it. Persistence and API concerns live in adapters instead.
 */
public class TimeDeposit {
    private int id;
    private String planType;
    private Double balance;
    private int days;

    public TimeDeposit(int id, String planType, Double balance, int days) {
        this.id = id;
        this.planType = planType;
        this.balance = balance;
        this.days = days;
    }

    public int getId() { return id; }

    public String getPlanType() {
        return planType;
    }

    public Double getBalance() {
        return balance;
    }

    public int getDays() {
        return days;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }
}
