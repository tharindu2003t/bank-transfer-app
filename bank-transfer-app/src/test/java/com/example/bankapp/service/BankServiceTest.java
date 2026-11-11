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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BankTransactionRepository bankTransactionRepository;

    @InjectMocks
    private BankService bankService;

    private Customer kamal;
    private Customer nimali;
    private Customer saman;
    private Customer revenueAccount;

    @BeforeEach
    void setUp() {
        kamal = new Customer(1L, "100001", "Kamal Perera", "kamal@example.com", new BigDecimal("50000.00"));
        nimali = new Customer(2L, "100002", "Nimali Silva", "nimali@example.com", new BigDecimal("25000.00"));
        saman = new Customer(3L, "100003", "Saman Kumara", "saman@example.com", new BigDecimal("10000.00"));
        revenueAccount = new Customer(4L, "999999", "Bank Revenue Account", "admin@bank.com", new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Test 1: Successful transfer between two accounts with 1% fee routing")
    void testSuccessfulTransferWithFee() {
        TransferRequest request = new TransferRequest("100001", "100002", new BigDecimal("5000.00"), false);

        when(customerRepository.getByAccountNumber("100001")).thenReturn(Optional.of(kamal));
        when(customerRepository.getByAccountNumber("100002")).thenReturn(Optional.of(nimali));
        when(customerRepository.countAccounts("999999")).thenReturn(1L);

        // 5000 + 1% fee (50.00) = 5050.00 total debit
        when(customerRepository.debit(eq("100001"), eq(new BigDecimal("5050.00")))).thenReturn(1);
        when(customerRepository.credit(eq("100002"), eq(new BigDecimal("5000.00")))).thenReturn(1);
        when(customerRepository.credit(eq("999999"), eq(new BigDecimal("50.00")))).thenReturn(1);

        BankTransaction mockTx = new BankTransaction("100001", "100002", new BigDecimal("5000.00"),
                TransactionStatus.SUCCESS, "Transfer completed", LocalDateTime.now());
        when(bankTransactionRepository.save(any(BankTransaction.class))).thenReturn(mockTx);

        BankTransaction result = bankService.transfer(request);

        assertNotNull(result);
        assertEquals(TransactionStatus.SUCCESS, result.getStatus());
        verify(customerRepository).debit("100001", new BigDecimal("5050.00"));
        verify(customerRepository).credit("100002", new BigDecimal("5000.00"));
        verify(customerRepository).credit("999999", new BigDecimal("50.00"));
        verify(bankTransactionRepository).save(any(BankTransaction.class));
    }

    @Test
    @DisplayName("Test 2: Transfer fails when sender has insufficient balance")
    void testInsufficientBalanceThrowsException() {
        TransferRequest request = new TransferRequest("100003", "100001", new BigDecimal("20000.00"), false);

        when(customerRepository.getByAccountNumber("100003")).thenReturn(Optional.of(saman));
        when(customerRepository.getByAccountNumber("100001")).thenReturn(Optional.of(kamal));
        when(customerRepository.countAccounts("999999")).thenReturn(1L);

        // Debit returns 0 rows updated because balance < amount
        when(customerRepository.debit(eq("100003"), any(BigDecimal.class))).thenReturn(0);

        assertThrows(InsufficientBalanceException.class, () -> bankService.transfer(request));

        verify(customerRepository, never()).credit(eq("100001"), any(BigDecimal.class));
        verify(bankTransactionRepository, never()).save(any(BankTransaction.class));
    }

    @Test
    @DisplayName("Test 3: Transfer fails when sender and receiver accounts are identical")
    void testSameAccountThrowsException() {
        TransferRequest request = new TransferRequest("100001", "100001", new BigDecimal("5000.00"), false);

        BankException ex = assertThrows(BankException.class, () -> bankService.transfer(request));
        assertEquals("Sender and receiver accounts must be different", ex.getMessage());

        verify(customerRepository, never()).debit(anyString(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("Test 4: Transfer aborts and triggers rollback when simulated crash is requested")
    void testSimulatedCrashThrowsRuntimeExceptionAfterDebit() {
        TransferRequest request = new TransferRequest("100001", "100003", new BigDecimal("1000.00"), true);

        when(customerRepository.getByAccountNumber("100001")).thenReturn(Optional.of(kamal));
        when(customerRepository.getByAccountNumber("100003")).thenReturn(Optional.of(saman));
        when(customerRepository.countAccounts("999999")).thenReturn(1L);

        // Debit succeeds
        when(customerRepository.debit(eq("100001"), any(BigDecimal.class))).thenReturn(1);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> bankService.transfer(request));
        assertEquals("Simulated system failure after the withdrawal", ex.getMessage());

        // Credit to receiver should NEVER be called because exception aborted execution
        verify(customerRepository, never()).credit(eq("100003"), any(BigDecimal.class));
        verify(bankTransactionRepository, never()).save(any(BankTransaction.class));
    }

    @Test
    @DisplayName("Test 6: Duplicate customer account number check")
    void testDuplicateAccountThrowsException() {
        Customer duplicate = new Customer("100001", "Duplicate User", "dup@example.com", new BigDecimal("5000.00"));
        when(customerRepository.countAccounts("100001")).thenReturn(1L);

        BankException ex = assertThrows(BankException.class, () -> bankService.createCustomer(duplicate));
        assertTrue(ex.getMessage().contains("already exists"));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    @DisplayName("Task 12: Large transfers filter above threshold")
    void testLargeTransfersQuery() {
        BigDecimal min = new BigDecimal("10000.00");
        BankTransaction tx = new BankTransaction("100001", "100002", new BigDecimal("15000.00"),
                TransactionStatus.SUCCESS, "Large Transfer", LocalDateTime.now());
        when(bankTransactionRepository.findTransfersAboveAmount(min, TransactionStatus.SUCCESS))
                .thenReturn(List.of(tx));

        List<BankTransaction> result = bankService.getLargeTransfers(min);
        assertEquals(1, result.size());
        assertEquals(new BigDecimal("15000.00"), result.get(0).getAmount());
    }

    @Test
    @DisplayName("Task 12: Deposit funds successfully")
    void testDepositFunds() {
        when(customerRepository.getByAccountNumber("100001")).thenReturn(Optional.of(kamal));
        when(customerRepository.credit("100001", new BigDecimal("5000.00"))).thenReturn(1);
        when(bankTransactionRepository.save(any(BankTransaction.class))).thenAnswer(i -> i.getArgument(0));

        BankTransaction tx = bankService.deposit("100001", new BigDecimal("5000.00"));
        assertNotNull(tx);
        assertEquals(TransactionStatus.SUCCESS, tx.getStatus());
        assertEquals(new BigDecimal("5000.00"), tx.getAmount());
        assertEquals("100001", tx.getToAccount());
    }

    @Test
    @DisplayName("Task 12: Withdraw funds successfully")
    void testWithdrawFunds() {
        when(customerRepository.getByAccountNumber("100001")).thenReturn(Optional.of(kamal));
        when(customerRepository.debit("100001", new BigDecimal("2000.00"))).thenReturn(1);
        when(bankTransactionRepository.save(any(BankTransaction.class))).thenAnswer(i -> i.getArgument(0));

        BankTransaction tx = bankService.withdraw("100001", new BigDecimal("2000.00"));
        assertNotNull(tx);
        assertEquals(TransactionStatus.SUCCESS, tx.getStatus());
        assertEquals(new BigDecimal("2000.00"), tx.getAmount());
        assertEquals("100001", tx.getFromAccount());
    }
}

