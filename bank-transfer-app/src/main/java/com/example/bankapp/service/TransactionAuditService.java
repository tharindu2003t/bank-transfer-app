package com.example.bankapp.service;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Saves a FAILED record after a transfer was rolled back.
 * REQUIRES_NEW = always start a brand-new, independent transaction,
 * so this record is committed even though the outer transfer transaction itself was rolled back.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionAuditService {

    private final BankTransactionRepository bankTransactionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String fromAccount, String toAccount, BigDecimal amount, String reason) {
        log.info("Recording failed transaction in isolated REQUIRES_NEW transaction: from={}, to={}, amount={}, reason={}",
                fromAccount, toAccount, amount, reason);

        BankTransaction failedTx = new BankTransaction(
                fromAccount,
                toAccount,
                amount != null ? amount : BigDecimal.ZERO,
                TransactionStatus.FAILED,
                reason != null ? reason : "Transaction failed",
                LocalDateTime.now()
        );
        bankTransactionRepository.save(failedTx);
    }
}
