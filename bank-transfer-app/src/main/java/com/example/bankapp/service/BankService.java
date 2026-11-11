package com.example.bankapp.service;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true) // default for every method: read-only transaction
public class BankService {

    private final CustomerRepository customerRepository;
    private final BankTransactionRepository bankTransactionRepository;

    public List<Customer> getAllCustomers() {
        return customerRepository.findAllOrdered();
    }

    public List<Customer> searchCustomers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return customerRepository.findAllOrdered();
        }
        return customerRepository.searchByName(keyword.trim());
    }

    public Customer getCustomer(String accountNumber) {
        return customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    public BigDecimal getTotalBalance() {
        return Optional.ofNullable(customerRepository.getTotalBalance())
                .orElse(BigDecimal.ZERO);
    }

    public long getCustomerCount() {
        return customerRepository.countCustomersNative();
    }

    public long getTransactionCount(TransactionStatus status) {
        return bankTransactionRepository.countByStatus(status);
    }

    public List<BankTransaction> getRecentTransactions() {
        return bankTransactionRepository.findAllLatest();
    }

    public List<BankTransaction> getAccountHistory(String accountNumber) {
        // Verify customer exists first
        getCustomer(accountNumber);
        return bankTransactionRepository.findHistory(accountNumber);
    }

    public BigDecimal getTotalSent(String accountNumber) {
        return Optional
                .ofNullable(bankTransactionRepository.getTotalSentByAccount(accountNumber, TransactionStatus.SUCCESS))
                .orElse(BigDecimal.ZERO);
    }

    public List<BankTransaction> getLargeTransfers(BigDecimal min) {
        BigDecimal threshold = min != null ? min : new BigDecimal("10000.00");
        return bankTransactionRepository.findTransfersAboveAmount(threshold, TransactionStatus.SUCCESS);
    }

    @Transactional
    public Customer createCustomer(Customer customer) {
        log.info("Creating customer: account={}, name={}", customer.getAccountNumber(), customer.getFullName());
        if (customerRepository.countAccounts(customer.getAccountNumber()) > 0) {
            throw new BankException("Account number " + customer.getAccountNumber() + " already exists");
        }
        if (customerRepository.countEmails(customer.getEmail()) > 0) {
            throw new BankException("Email " + customer.getEmail() + " already exists");
        }
        return customerRepository.save(customer);
    }

    /**
     * Executes an atomic bank transfer between two customers.
     * All operations (sender debit, receiver credit, fee routing, and audit
     * persistence)
     * execute within a single transactional boundary and roll back on any
     * exception.
     *
     * Note for Task 10 experiment:
     * Change this annotation to @Transactional(propagation = Propagation.SUPPORTS)
     * to observe atomicity breakdown where withdrawal commits but deposit fails.
     */
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction transfer(TransferRequest request) {
        String fromAccount = request.getFromAccount();
        String toAccount = request.getToAccount();
        BigDecimal amount = request.getAmount();

        log.info("Initiating transfer: LKR {} from {} to {} (simulateFailure={})",
                amount, fromAccount, toAccount, request.isSimulateFailure());

        // Step 1: Validate accounts are different and both exist
        if (fromAccount == null || toAccount == null || fromAccount.trim().equalsIgnoreCase(toAccount.trim())) {
            throw new BankException("Sender and receiver accounts must be different");
        }

        Customer sender = customerRepository.getByAccountNumber(fromAccount)
                .orElseThrow(() -> new AccountNotFoundException(fromAccount));
        Customer receiver = customerRepository.getByAccountNumber(toAccount)
                .orElseThrow(() -> new AccountNotFoundException(toAccount));

        // Task 12 Extension: 1% transfer fee credited to administrative bank account
        // 999999
        BigDecimal fee = BigDecimal.ZERO;
        boolean applyFee = !"999999".equals(fromAccount) && !"999999".equals(toAccount)
                && customerRepository.countAccounts("999999") > 0;
        if (applyFee) {
            fee = amount.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal totalDebit = amount.add(fee);

        // Step 2: Atomic debit from sender (UPDATE ... WHERE balance >= :amount)
        int debitUpdated = customerRepository.debit(fromAccount, totalDebit);
        if (debitUpdated == 0) {
            throw new InsufficientBalanceException(fromAccount);
        }

        // Step 3: (Experiment) Throw a simulated failure if checkbox is ticked
        if (request.isSimulateFailure()) {
            throw new RuntimeException("Simulated system failure after the withdrawal");
        }

        // Step 4: Credit receiver's balance
        int creditUpdated = customerRepository.credit(toAccount, amount);
        if (creditUpdated == 0) {
            throw new BankException("Failed to credit receiver account " + toAccount);
        }

        // Step 4b: Credit 1% fee to administrative bank account 999999
        if (applyFee && fee.compareTo(BigDecimal.ZERO) > 0) {
            customerRepository.credit("999999", fee);
        }

        // Step 5: Save SUCCESS record in bank_transactions
        String description = "Transfer completed";
        if (applyFee && fee.compareTo(BigDecimal.ZERO) > 0) {
            description = "Transfer completed (includes LKR " + fee + " fee to 999999)";
        }

        BankTransaction tx = new BankTransaction(
                fromAccount,
                toAccount,
                amount,
                TransactionStatus.SUCCESS,
                description,
                LocalDateTime.now());

        BankTransaction saved = bankTransactionRepository.save(tx);
        log.info("Transfer completed successfully. Transaction ID: {}", saved.getId());
        return saved;
    }

    /**
     * Task 12 Extension: Deposit funds into an account within a single transaction.
     */
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction deposit(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Deposit amount must be greater than zero");
        }
        getCustomer(accountNumber); // Verify existence

        customerRepository.credit(accountNumber, amount);

        BankTransaction tx = new BankTransaction(
                null,
                accountNumber,
                amount,
                TransactionStatus.SUCCESS,
                "Deposit to account " + accountNumber,
                LocalDateTime.now());
        return bankTransactionRepository.save(tx);
    }

    /**
     * Task 12 Extension: Withdraw funds from an account within a single
     * transaction.
     */
    @Transactional(rollbackFor = Exception.class)
    public BankTransaction withdraw(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException("Withdrawal amount must be greater than zero");
        }
        getCustomer(accountNumber); // Verify existence

        int updated = customerRepository.debit(accountNumber, amount);
        if (updated == 0) {
            throw new InsufficientBalanceException(accountNumber);
        }

        BankTransaction tx = new BankTransaction(
                accountNumber,
                null,
                amount,
                TransactionStatus.SUCCESS,
                "Withdrawal from account " + accountNumber,
                LocalDateTime.now());
        return bankTransactionRepository.save(tx);
    }
}
