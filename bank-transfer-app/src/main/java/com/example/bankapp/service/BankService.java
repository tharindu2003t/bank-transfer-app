package com.example.bankapp.service;

/*import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;*/
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;*/

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)          // default for every method: read-only transaction
public class BankService {
//
}
