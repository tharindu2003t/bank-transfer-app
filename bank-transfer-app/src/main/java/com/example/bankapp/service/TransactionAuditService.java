package com.example.bankapp.service;

//import com.example.bankapp.model.BankTransaction;
//import com.example.bankapp.model.TransactionStatus;
//import com.example.bankapp.repository.BankTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Propagation;
//import org.springframework.transaction.annotation.Transactional;

//import java.math.BigDecimal;

/**
 * Saves a FAILED record after a transfer was rolled back.
 * REQUIRES_NEW = always start a brand-new, independent transaction,
 * so this record is committed even though the transfer itself was rolled back.
 */
@Service
@RequiredArgsConstructor
public class TransactionAuditService {
//
}
