package com.muciaccia.plugins.backend.pojo;

import com.muciaccia.plugins.Treasurer;

import java.math.BigDecimal;

/**
 * A POJO class which models a money exchange between two users. Each solution to the balance problem consists
 * of a list of BalanceStatement.
 */
public class BalanceStatement {
    private final User debtor;
    private final User creditor;
    // the amount is a positive number which corresponds to the amount of money that the debtor should give to the
    // creditor
    private final BigDecimal amount;

    public BalanceStatement(final User debtor, final User creditor, final BigDecimal amount) {
        assert amount.compareTo(Treasurer.ZERO) > 0;

        this.debtor = debtor;
        this.creditor = creditor;
        this.amount = amount;
    }

    public User getDebtor() {
        return debtor;
    }

    public User getCreditor() {
        return creditor;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
