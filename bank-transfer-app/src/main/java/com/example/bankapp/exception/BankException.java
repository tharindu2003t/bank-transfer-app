package com.example.bankapp.exception;

/** Base class for all banking errors. It extends RuntimeException, so Spring rolls back automatically. */
public class BankException extends RuntimeException {

    public BankException(String message) {
        super(message);
    }
}
