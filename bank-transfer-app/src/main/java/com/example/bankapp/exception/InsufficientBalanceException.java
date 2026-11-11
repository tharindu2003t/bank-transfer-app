package com.example.bankapp.exception;

public class InsufficientBalanceException extends BankException {

    public InsufficientBalanceException(String accountNumber) {
        super("Insufficient balance in account " + accountNumber);
    }
}
