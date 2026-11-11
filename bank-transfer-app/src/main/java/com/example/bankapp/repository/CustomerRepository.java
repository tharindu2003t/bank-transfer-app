package com.example.bankapp.repository;

import com.example.bankapp.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // 1. JPQL + @Param: Finds one customer by account number
    @Query("SELECT c FROM Customer c WHERE c.accountNumber = :accountNumber")
    Optional<Customer> getByAccountNumber(@Param("accountNumber") String accountNumber);

    // 2a. JPQL COUNT: Checks whether an account already exists
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.accountNumber = :accountNumber")
    long countAccounts(@Param("accountNumber") String accountNumber);

    // 2b. JPQL COUNT: Checks whether an email already exists
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.email = :email")
    long countEmails(@Param("email") String email);

    // 3. JPQL ORDER BY: All customers sorted by name
    @Query("SELECT c FROM Customer c ORDER BY c.fullName ASC")
    List<Customer> findAllOrdered();

    // 4. JPQL LIKE: Case-insensitive name search
    @Query("SELECT c FROM Customer c WHERE LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY c.fullName ASC")
    List<Customer> searchByName(@Param("keyword") String keyword);

    // 5. JPQL aggregate: Sum of all balances (null if no customers)
    @Query("SELECT SUM(c.balance) FROM Customer c")
    BigDecimal getTotalBalance();

    // 6. Native SQL: Plain MySQL count
    @Query(value = "SELECT COUNT(*) FROM customers", nativeQuery = true)
    long countCustomersNative();

    // 7. UPDATE + @Modifying: Subtracts money only if balance >= :amount; returns rows changed
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Customer c SET c.balance = c.balance - :amount WHERE c.accountNumber = :accountNumber AND c.balance >= :amount")
    int debit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);

    // 8. UPDATE + @Modifying: Adds money to an account
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Customer c SET c.balance = c.balance + :amount WHERE c.accountNumber = :accountNumber")
    int credit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);
}
