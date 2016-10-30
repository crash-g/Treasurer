package com.muciaccia.plugins.exceptions;

/**
 * This exception is thrown when the total star modifier of an Expense is zero when it should not.
 */
public class PhantomMoneyException extends ExpenseNotFinalizedException {
    public PhantomMoneyException() {
        super();
    }
}
