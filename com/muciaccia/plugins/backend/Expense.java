package com.muciaccia.plugins.backend;

import com.muciaccia.plugins.Treasurer;
import com.muciaccia.plugins.backend.pojo.User;
import com.muciaccia.plugins.exceptions.EmptyExpenseException;
import com.muciaccia.plugins.exceptions.ExpenseNotFinalizedException;
import com.muciaccia.plugins.exceptions.PhantomMoneyException;
import com.muciaccia.plugins.exceptions.PlusModTooLargeException;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * This class contains all the information relative to a single expense submitted by a user.
 */
public class Expense {
    // custom BigDecimal scale which is used for divisions
    private final static int DIVISION_SCALE = 3;

    // POJO class containing the information about a single participant to the expense.
    private class ExpenseDetail {
        // plus modifier
        private final BigDecimal plusMod;
        // star modifier
        private final BigDecimal starMod;
        // calculated share of a participant
        private BigDecimal share;

        ExpenseDetail(final BigDecimal plusMod, final BigDecimal starMod) {
            this.plusMod = plusMod;
            this.starMod = starMod;
        }
    }

    // date of the expense
    private final Date date;
    // description of the expense (as inserted by the user)
    private final String description;
    // total amount of the expense (as inserted by the user)
    private final BigDecimal amount;

    // user that submitted the expense
    private final User payer;
    // plus modifier of the payer
    private BigDecimal payerPlus;
    // star modifier of the payer (may be ZERO)
    private BigDecimal payerStar;
    // total amount due to the payer
    private BigDecimal payerCredit;

    // map containing all participants of the expense and their relative details
    private Map<User, ExpenseDetail> details;

    // total plus modifier
    private BigDecimal totalPlus;
    // total star modifier
    private BigDecimal totalStar;
    // true if the expense has been processed
    private boolean finalized;

    /**
     * Public constructor.
     *
     * @param date        the date of the expense
     * @param description the description of the expense
     * @param amount      the total amount of the expense
     * @param payer       the user that submitted the expense
     */
    public Expense(final Date date, final String description, final BigDecimal amount, final User payer) {
        this.date = date;
        this.description = description;
        this.amount = amount;
        details = new HashMap<>();

        this.payer = payer;
        payerCredit = BigDecimal.valueOf(0).setScale(Treasurer.NORMAL_SCALE, Treasurer.NORMAL_ROUNDING_MODE);

        totalPlus = Treasurer.ZERO;
        totalStar = Treasurer.ZERO;
        finalized = false;
    }

    /**
     * Adds a participant to the expense.
     *
     * @param user    the user to add
     * @param plusMod the plus modifier associated to the user
     * @param starMod the star modifier associated to the user
     */
    public void addParticipant(final User user, final BigDecimal plusMod, final BigDecimal starMod) {
        assert (!details.containsKey(user));
        if(finalized) {
            return;
        }

        if (user.equals(payer)) {
            payerPlus = plusMod;
            payerStar = starMod;
        } else {
            details.put(user, new ExpenseDetail(plusMod, starMod));
        }
        totalPlus = totalPlus.add(plusMod);
        totalStar = totalStar.add(starMod);
    }

    /**
     * Calculates single shares and total amount due to the payer, then updates the global balance. No participant
     * can be added to the expense after calling this method.
     *
     * @param balance the balance to update
     * @throws EmptyExpenseException if the expense has no participants
     * @throws PlusModTooLargeException if totalPlus is larger than amount
     */
    public void finalizeExpense(final Balance balance) throws ExpenseNotFinalizedException {
        if(details.isEmpty()) {
            throw new EmptyExpenseException();
        }
        calculateSingleShares();
        updateBalance(balance);
        finalized = true;
    }

    /**
     * Calculates single shares for all participants and total amount due to the payer.
     * @throws PlusModTooLargeException if totalPlus is larger than amount
     */
    private void calculateSingleShares() throws PlusModTooLargeException, PhantomMoneyException {
        BigDecimal commonFraction;
        int compare = amount.compareTo(totalPlus);
        if(compare < 0) {
            throw new PlusModTooLargeException();
        } else if(0 == compare) {
            commonFraction = Treasurer.ZERO;
        } else {
            if(totalStar.equals(Treasurer.ZERO)) {
                throw new PhantomMoneyException();
            }
            commonFraction = amount.subtract(totalPlus).divide(totalStar, DIVISION_SCALE, Treasurer
                    .NORMAL_ROUNDING_MODE);
        }
        for (ExpenseDetail detail : details.values()) {
            detail.share = commonFraction.multiply(detail.starMod).add(detail.plusMod).setScale(Treasurer
                    .NORMAL_SCALE, Treasurer.NORMAL_ROUNDING_MODE);
            payerCredit = payerCredit.add(detail.share);
        }
    }

    /**
     * Updates the balance.
     * @param balance the balance to update
     */
    private void updateBalance(final Balance balance) {
        balance.updateUserStatus(payer,payerCredit);
        for (Map.Entry<User, ExpenseDetail> entry : details.entrySet()) {
            balance.updateUserStatus(entry.getKey(),entry.getValue().share.negate());
        }
    }

    /********************* GETTERS AND SETTERS ************************/

    public boolean isPayer(final User user) {
        return payer.equals(user);
    }

    public BigDecimal getPayerCredit() {
        return payerCredit;
    }

    public boolean isDebtor(final User user) {
        return details.containsKey(user);
    }

    public BigDecimal getDebtorDebt(final User user) {
        return details.get(user).share;
    }

    public Date getDate() {
        return date;
    }

    public String getDescription() {
        return description;
    }
}
