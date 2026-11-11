package com.example.bankapp.repository;

import com.example.bankapp.model.BankTransaction;
//import com.example.bankapp.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;

//import java.math.BigDecimal;
//import java.util.List;

public interface BankTransactionRepository
        extends JpaRepository<BankTransaction, Long> {

}