package com.muciaccia.plugins.exceptions;

/**
 * This exception is thrown when an Expense is being finalized but it has no participants.
 */
public class EmptyExpenseException extends ExpenseNotFinalizedException {
    public EmptyExpenseException() {
        super();
    }
}
