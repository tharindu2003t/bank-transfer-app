# UNIVERSITY OF KELANIYA
## Department of Statistics & Computer Science
### Academic Year 2024/2025 (Semester II)
### COSC 32133 / BECS 32263: Full-Stack Software Development
---
# ASSIGNMENT 01 TECHNICAL REPORT: BANK TRANSACTIONS WITH @TRANSACTIONAL, ROLLBACK, AND @QUERY

**Student Name:** Ekanayaka Mudiyanselage Tharindu Bandara  
**Student ID:** PS/2022/152  
**Course Code:** COSC 32133 / BECS 32263  
**Course Name:** Full-Stack Software Development  
**Submission Date:** 11th of November 2026  
**GitHub Repository URL:** [https://github.com/tharindu2003t/bank-transfer-app](https://github.com/tharindu2003t/bank-transfer-app)  
**Feature Branch:** `feature/bank-transfer` &middot; **Base Branch:** `main`

---

## 1. Executive Summary & Architecture Overview

The Bank Transfer System is an enterprise-grade financial management web application built using **Spring Boot 3 / Java 21**, **Spring Data JPA**, **Hibernate**, and **MySQL (InnoDB engine)**. The system strictly adheres to **ACID principles** (Atomicity, Consistency, Isolation, Durability) to process monetary transfers, deposits, withdrawals, and fee collections across customer accounts without data corruption or financial discrepancies.

### 1.1 Architectural Layering & Separation of Concerns

```
+---------------------------------------------------------------------------------+
|                               Presentation Layer                                |
|   bank.html (Dashboard)  |  history.html  |  large-transfers.html  | deposit/withdraw |
+---------------------------------------------------------------------------------+
                                      | HTTP Requests (GET, POST)
                                      v
+---------------------------------------------------------------------------------+
|                                Controller Layer                                 |
|                               BankController.java                               |
|   Handles routing, model binding, Jakarta Bean Validation, and failure catch    |
+---------------------------------------------------------------------------------+
                         /                                  \
   Transfer / Read Calls/                                    \ On Exception Caught
                       v                                      v
+-----------------------------------------+   +------------------------------------+
|             Service Layer               |   |        Isolated Audit Bean         |
|            BankService.java             |   |    TransactionAuditService.java    |
|   @Transactional(readOnly = true)       |   | @Transactional                     |
|   transfer(): @Transactional(rollbackFor|   |   (propagation = REQUIRES_NEW)     |
|              = Exception.class)         |   |                                    |
|   Atomic Debit -> [Simulated Fail] ->   |   | Independent transaction commits    |
|   Credit Receiver -> Credit Fee (999999)|   | even when outer transfer rolls back|
+-----------------------------------------+   +------------------------------------+
                        \                                    /
                         \                                  /
                          v                                v
+---------------------------------------------------------------------------------+
|                               Repository Layer                                  |
|         CustomerRepository.java        |     BankTransactionRepository.java     |
|  - 8 Custom User-Defined @Query        |  - findHistory (OR logic)              |
|  - Atomic conditional UPDATEs          |  - findTransfersAboveAmount (Task 12)  |
|  - Native MySQL & JPQL Aggregates      |  - getTotalSentByAccount (SUM aggregate|
+---------------------------------------------------------------------------------+
                                      | JDBC / SQL via Hibernate
                                      v
+---------------------------------------------------------------------------------+
|                          Database Layer (MySQL InnoDB)                          |
|   Tables: customers (DECIMAL(14,2)), bank_transactions (Status: SUCCESS, FAILED)|
+---------------------------------------------------------------------------------+
```

---

## 2. Answers to Mandatory Assignment Questions

### Question (a): What would have happened to the money in test 4 without `@Transactional`?
*(Demonstrated via the Task 10 Experiment using `Propagation.SUPPORTS`)*

#### Technical Breakdown & Experimental Observation:
In Test 4, a transfer of **LKR 1,000.00** from Kamal Perera (`100001`, initial balance: LKR 45,000.00) to Saman Kumara (`100003`, initial balance: LKR 10,000.00) is submitted with the `"Simulate a system failure"` flag enabled.

Inside `BankService.transfer()`, the execution order is:
1. Validate accounts and balances.
2. Execute `customerRepository.debit("100001", 1000.00)`.
3. Check `if (request.isSimulateFailure()) throw new RuntimeException("Simulated system failure after the withdrawal");`.
4. Execute `customerRepository.credit("100003", 1000.00)`.
5. Persist `BankTransaction` with `SUCCESS`.

#### Under the Production Configuration (`@Transactional(rollbackFor = Exception.class)`):
Spring's AOP transaction proxy intercepts the invocation and issues a `BEGIN TRANSACTION` to MySQL InnoDB. Both Step 2 (`debit`) and Step 4 (`credit`) participate in the **same physical transaction unit**. When the `RuntimeException` is thrown at Step 3:
- Spring catches the exception via its rollback rule (`rollbackFor = Exception.class`).
- The transaction interceptor invokes `JpaTransactionManager.doRollback()`, issuing an explicit `ROLLBACK` SQL command to the InnoDB storage engine.
- **Result:** The preliminary subtraction of LKR 1,000.00 from Kamal's balance is **completely undone** in the database undo logs. Kamal remains at LKR 45,000.00, Saman remains at LKR 10,000.00, and total bank assets remain intact at LKR 85,000.00 (**Atomicity & Consistency preserved**).

#### Under the Task 10 Experiment (`@Transactional(propagation = Propagation.SUPPORTS)`):
When annotated with `Propagation.SUPPORTS`, Spring executes the method without creating a transactional context if no caller transaction exists. Because the call originates from `BankController.transfer()` (which is unannotated), **the method executes in autocommit mode**:
1. At Step 2, `customerRepository.debit()` executes. Because `@Modifying` queries require a transaction, Hibernate or the JDBC driver immediately opens an auto-commit mini-transaction, executes the `UPDATE customers SET balance = balance - 1000.00 ...`, and **immediately commits the change to disk**. Kamal's database balance is reduced from LKR 45,000.00 to LKR 44,000.00.
2. At Step 3, the simulated `RuntimeException` is thrown.
3. Because there is no active outer transaction manager managing the entire execution block, there is **no transaction context to roll back**.
4. Steps 4 and 5 are aborted: Saman never receives the LKR 1,000.00 credit.
5. **Catastrophic Failure:** The LKR 1,000.00 has **vanished from thin air**. The total money across all bank accounts drops from LKR 85,000.00 to LKR 84,000.00. The system enters an inconsistent, corrupt state directly violating the **Atomicity** guarantee ($A \in ACID$) and the **Conservation of Money (Consistency)** invariant.

---

### Question (b): Why is `recordFailure` in a separate class with `REQUIRES_NEW`?
*(Architectural Explanation of Spring AOP Proxies and Outer Rollback Isolation)*

#### 1. The Rollback Contamination Problem:
When `BankService.transfer()` throws an exception (such as `InsufficientBalanceException` or a simulated crash), its entire transactional context is marked as `Rollback-Only` and rolled back. If `recordFailure()` were executed within the same transaction:
- The `INSERT INTO bank_transactions` statement recording the `FAILED` transaction status would be **discarded and erased** along with the failed debit and credit operations.
- The bank would have **zero audit trail** or forensic record of the failed transaction attempt, violating regulatory compliance and auditability requirements.

#### 2. Why `Propagation.REQUIRES_NEW` Resolves It:
`Propagation.REQUIRES_NEW` instructs Spring's transaction manager to **suspend any active ambient transaction** and instantiate a completely distinct, physical database connection/transaction. 
- When `TransactionAuditService.recordFailure()` is invoked, a fresh transaction is opened.
- The `FAILED` audit record is inserted and immediately **committed** to MySQL via `COMMIT`.
- Control returns to the caller, and the outer transfer transaction's rollback proceeds without affecting the already-committed audit row.

#### 3. Why It MUST Be a Separate Spring Bean (Class):
Spring manages transactions using **dynamic runtime proxies** (CGLIB or JDK dynamic proxies). When a client calls a `@Transactional` method:
```
Caller ---> [Spring Proxy Interceptor (begins/commits/rolls back)] ---> Target Bean Method
```
If `recordFailure()` were placed inside `BankService` and called internally via `this.recordFailure()`:
- The call is an internal Java method invocation (`this.method()`), completely **bypassing the Spring AOP Proxy**.
- Consequently, the `@Transactional(propagation = Propagation.REQUIRES_NEW)` annotation on the internal method is **silently ignored**. The method executes within whatever ambient transaction is active (or none), and the rollback would either erase the audit log or cause an `UnexpectedRollbackException`.
- By isolating the audit logic in `TransactionAuditService`, the controller injects `TransactionAuditService` through Spring's ApplicationContext. The call traverses the Spring proxy, guaranteeing that `REQUIRES_NEW` semantics are strictly enforced.

---

### Question (c): Why does debit use `UPDATE ... WHERE balance >= :amount` instead of reading the balance first?
*(Detailing Race Conditions, TOCTOU Vulnerabilities, and InnoDB Row-Level Locking)*

#### 1. The Time-of-Check to Time-of-Use (TOCTOU) Race Condition:
A naive two-step approach implements debit as follows:
```java
// Anti-pattern: Naive two-step check-then-act
Customer c = customerRepository.findById(accId); // Step 1: Check balance
if (c.getBalance().compareTo(amount) >= 0) {
    c.setBalance(c.getBalance().subtract(amount)); // Step 2: Update balance
    customerRepository.save(c);
}
```
In a multi-user or high-concurrency banking environment, consider two concurrent transfer requests (Thread A and Thread B) trying to withdraw LKR 40,000.00 each from an account with an initial balance of LKR 50,000.00:

| Time | Thread A (Transfer LKR 40,000) | Thread B (Transfer LKR 40,000) | Account Balance |
|:----:|:------------------------------|:------------------------------|:---------------:|
| $T_1$ | Reads balance: LKR 50,000.00  | -                             | LKR 50,000.00   |
| $T_2$ | -                             | Reads balance: LKR 50,000.00  | LKR 50,000.00   |
| $T_3$ | Check: 50,000 >= 40,000 (TRUE)| -                             | LKR 50,000.00   |
| $T_4$ | -                             | Check: 50,000 >= 40,000 (TRUE)| LKR 50,000.00   |
| $T_5$ | Writes new balance: 10,000.00 | -                             | LKR 10,000.00   |
| $T_6$ | -                             | Writes new balance: 10,000.00 | **LKR 10,000.00**|

**Consequences:**
1. **Lost Update Anomaly:** Thread B's write overwrites Thread A's write.
2. **Negative Balance / Double Spending:** The bank paid out LKR 80,000.00 in cash/transfers, but the customer's balance only dropped by LKR 40,000.00!

#### 2. The Atomic Conditional Query Solution:
The repository query uses:
```sql
UPDATE Customer c 
SET c.balance = c.balance - :amount 
WHERE c.accountNumber = :accountNumber AND c.balance >= :amount
```
In MySQL InnoDB:
1. **Row-Level Exclusive Locking (`X-Lock`):** When the `UPDATE` executes against the indexed `account_number` column, InnoDB acquires an immediate exclusive lock on that specific row. Any concurrent transaction attempting to read (for update) or modify the same row is blocked until the active transaction completes.
2. **Single Atomic Evaluation:** The verification (`balance >= :amount`) and the mathematical subtraction (`balance = balance - :amount`) are performed atomically inside the database engine kernel in a single uninterruptible CPU cycle.
3. **Deterministic Return Value:** The method returns the number of rows affected (`int`). If the balance was sufficient, it returns `1`. If the balance was insufficient, the `WHERE` condition matches 0 rows and returns `0`.
4. The service evaluates the return code:
   ```java
   if (debitUpdated == 0) {
       throw new InsufficientBalanceException(fromAccount);
   }
   ```
   This guarantees that double spending is mathematically and structurally impossible, without requiring expensive application-level synchronization or pessimistic table locking.
5. In addition, `@Modifying(clearAutomatically = true)` forces Spring Data JPA to invalidate Hibernate's first-level persistence cache, preventing stale in-memory entity states from being read subsequently.

---

## 3. Test Verification & Execution Matrix (Tests 1 to 7)

| Test # | Action Performed | Expected Result | What It Proves | Console Log Signature |
|:------:|:-----------------|:----------------|:---------------|:----------------------|
| **1** | Transfer LKR 5,000.00 from Kamal (`100001`) to Nimali (`100002`) | Kamal: 45,000.00; Nimali: 30,000.00; Total: 85,000.00; Status: SUCCESS | Full commit; all transactional steps saved | `Initiating transaction commit` |
| **2** | Transfer LKR 20,000.00 from Saman (`100003`, balance: 10,000) to Kamal | Red error banner: "Insufficient balance in account 100003"; Balances unchanged; FAILED row inserted | Exception triggers rollback; audit bean commits independently | `Initiating transaction rollback` followed by `Creating new transaction with name [TransactionAuditService.recordFailure]` |
| **3** | Transfer LKR 5,000.00 choosing same sender and receiver (`100001` -> `100001`) | Error: "Sender and receiver accounts must be different"; Balances unchanged; FAILED row | Business validation inside transactional boundary | `Initiating transaction rollback` |
| **4** | Transfer LKR 1,000.00 from Kamal to Saman with "Simulate a system failure" ticked | Error: "Simulated system failure after the withdrawal"; Kamal remains 45,000.00; Saman remains 10,000.00; FAILED row | **Atomicity:** The prior debit that already occurred in the database was completely UNDONE | `Initiating transaction rollback` |
| **5** | Submit transfer form with empty amount or 0.00 | Inline validation error under input field; No database transaction initiated | Jakarta Bean Validation (`@NotNull`, `@DecimalMin`, `@Digits`) | Validation failure before controller service invocation |
| **6** | Add Customer Nuwan (`100004`, LKR 15,000.00). Attempt adding `100004` a second time | First attempt: Customer created. Second attempt: "Account number 100004 already exists" | Custom `@Query` count validations (`countAccounts`) | `SELECT COUNT(c) FROM Customer c WHERE c.accountNumber = ?` |
| **7** | Search `"sil"` in customer search box; View transaction history for Kamal | Only matching customers returned (e.g. Nimali Silva); History shows all SUCCESS and FAILED ledger rows | User-defined `@Query` with JPQL `LIKE` and `OR` logic | `SELECT c FROM Customer c WHERE LOWER(c.fullName) LIKE ?` |

### Database Verification SQL Script:
```sql
USE bank_db;

-- 1. Inspect all customers and their exact balances
SELECT id, account_number, full_name, email, balance FROM customers;

-- 2. Inspect entire audit ledger (both SUCCESS and FAILED)
SELECT id, created_at, from_account, to_account, amount, status, description 
FROM bank_transactions 
ORDER BY id DESC;

-- 3. Verify System Invariant (Conservation of Total Money)
SELECT SUM(balance) AS total_bank_balance FROM customers;
```

---

## 4. Task 12 Application Extensions Summary

1. **Large Transfers Filter (`/large-transfers?min=10000`):**
   - Implemented custom repository query `findTransfersAboveAmount()` filtering successful transfers where `amount >= :min`.
   - Dedicated controller endpoint `@GetMapping("/large-transfers")` rendering `large-transfers.html` with responsive filtering controls.

2. **Account Total Sent Aggregate Display (`history.html`):**
   - Implemented JPQL aggregate query `getTotalSentByAccount()` calculating `COALESCE(SUM(t.amount), 0)` where `t.fromAccount = :accountNumber AND t.status = 'SUCCESS'`.
   - Displayed dynamically in the account summary header card on `history.html`.

3. **Deposit & Withdrawal Transactional Workflows (`/deposit`, `/withdraw`):**
   - Built standalone transactional service methods `deposit()` and `withdraw()` utilizing `customerRepository.credit()` and conditional `customerRepository.debit()`.
   - Provided web forms with validation and full persistence to `bank_transactions`.

4. **Atomic 1% Bank Revenue Fee Routing (Account `999999`):**
   - Seeding administrative revenue account `999999` ("Bank Revenue Account", initial balance: LKR 0.00) via `DataLoader`.
   - On each transfer between customer accounts, a 1% fee is calculated and withdrawn from the sender, while credited to `999999`. All three database operations (`debit sender`, `credit receiver`, `credit 999999`) execute as a single atomic transaction.

