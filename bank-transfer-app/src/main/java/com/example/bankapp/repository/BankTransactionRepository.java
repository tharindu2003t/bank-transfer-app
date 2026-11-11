package com.example.bankapp.repository;

import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    // 1. All transactions, newest first
    @Query("SELECT t FROM BankTransaction t ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findAllLatest();

    // 2. Transactions where the account is sender or receiver (OR condition)
    @Query("SELECT t FROM BankTransaction t WHERE t.fromAccount = :accountNumber OR t.toAccount = :accountNumber ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findHistory(@Param("accountNumber") String accountNumber);

    // 3. Filters by status; enum parameter
    @Query("SELECT t FROM BankTransaction t WHERE t.status = :status ORDER BY t.createdAt DESC, t.id DESC")
    List<BankTransaction> findByStatusLatest(@Param("status") TransactionStatus status);

    // 4. Count by status (SUCCESS or FAILED)
    @Query("SELECT COUNT(t) FROM BankTransaction t WHERE t.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);

    // 5. Task 12: Transfers above a given amount (filtered by status)
    @Query("SELECT t FROM BankTransaction t WHERE t.status = :status AND t.amount >= :min ORDER BY t.amount DESC, t.createdAt DESC")
    List<BankTransaction> findTransfersAboveAmount(@Param("min") BigDecimal min,
            @Param("status") TransactionStatus status);

    // 6. Task 12: Aggregate query computing total SUCCESS amount sent by an account
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM BankTransaction t WHERE t.fromAccount = :accountNumber AND t.status = :status")
    BigDecimal getTotalSentByAccount(@Param("accountNumber") String accountNumber,
            @Param("status") TransactionStatus status);
}
