package com.muciaccia.plugins.exceptions;

/**
 * This exception is thrown when the total plus modifier of an Expense is larger than the amount of the expense.
 */
public class PlusModTooLargeException extends ExpenseNotFinalizedException {
    public PlusModTooLargeException() {
        super();
    }
}
