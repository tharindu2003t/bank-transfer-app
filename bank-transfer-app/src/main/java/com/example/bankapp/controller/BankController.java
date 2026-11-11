package com.example.bankapp.controller;

import com.example.bankapp.dto.TransferRequest;
import com.example.bankapp.exception.BankException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.service.BankService;
import com.example.bankapp.service.TransactionAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BankController {

    private final BankService bankService;
    private final TransactionAuditService transactionAuditService;

    // Dashboard: statistics, forms, customers table, latest transactions
    @GetMapping("/")
    public String dashboard(@RequestParam(value = "keyword", required = false) String keyword, Model model) {
        loadDashboard(model, keyword);
        if (!model.containsAttribute("transferRequest")) {
            model.addAttribute("transferRequest", new TransferRequest());
        }
        if (!model.containsAttribute("customer")) {
            model.addAttribute("customer", new Customer());
        }
        return "bank";
    }

    private void loadDashboard(Model model, String keyword) {
        List<Customer> customers;
        if (keyword != null && !keyword.trim().isEmpty()) {
            customers = bankService.searchCustomers(keyword.trim());
        } else {
            customers = bankService.getAllCustomers();
        }

        model.addAttribute("customers", customers);
        model.addAttribute("allCustomers", bankService.getAllCustomers());
        model.addAttribute("keyword", keyword);
        model.addAttribute("totalCustomers", bankService.getCustomerCount());
        model.addAttribute("totalBalance", bankService.getTotalBalance());
        model.addAttribute("successCount", bankService.getTransactionCount(TransactionStatus.SUCCESS));
        model.addAttribute("failedCount", bankService.getTransactionCount(TransactionStatus.FAILED));
        model.addAttribute("transactions", bankService.getRecentTransactions());
    }

    // Add a new customer
    @PostMapping("/customers")
    public String addCustomer(@Valid @ModelAttribute("customer") Customer customer,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            loadDashboard(model, null);
            if (!model.containsAttribute("transferRequest")) {
                model.addAttribute("transferRequest", new TransferRequest());
            }
            return "bank";
        }

        try {
            bankService.createCustomer(customer);
            redirectAttributes.addFlashAttribute("success", "Customer " + customer.getFullName() + " added successfully.");
        } catch (BankException ex) {
            log.error("Failed to add customer: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }

    // Transfer money between accounts
    @PostMapping("/transfer")
    public String transfer(@Valid @ModelAttribute("transferRequest") TransferRequest transferRequest,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            loadDashboard(model, null);
            if (!model.containsAttribute("customer")) {
                model.addAttribute("customer", new Customer());
            }
            return "bank";
        }

        try {
            bankService.transfer(transferRequest);
            redirectAttributes.addFlashAttribute("success", "Transfer completed successfully!");
        } catch (Exception ex) {
            log.warn("Transfer failed due to: {}. Initiating independent failure audit...", ex.getMessage());
            // Record failure in isolated REQUIRES_NEW transaction so it commits despite outer rollback
            transactionAuditService.recordFailure(
                    transferRequest.getFromAccount(),
                    transferRequest.getToAccount(),
                    transferRequest.getAmount(),
                    ex.getMessage()
            );
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }

    // Account-specific transaction history
    @GetMapping("/accounts/{accountNumber}/history")
    public String accountHistory(@PathVariable("accountNumber") String accountNumber, Model model) {
        Customer customer = bankService.getCustomer(accountNumber);
        List<BankTransaction> history = bankService.getAccountHistory(accountNumber);
        BigDecimal totalSent = bankService.getTotalSent(accountNumber);

        model.addAttribute("customer", customer);
        model.addAttribute("transactions", history);
        model.addAttribute("totalSent", totalSent);
        return "history";
    }

    // Task 12 Extension: Filter transfers above a given amount
    @GetMapping("/large-transfers")
    public String largeTransfers(@RequestParam(value = "min", defaultValue = "10000.00") BigDecimal min, Model model) {
        List<BankTransaction> transfers = bankService.getLargeTransfers(min);
        model.addAttribute("min", min);
        model.addAttribute("transfers", transfers);
        return "large-transfers";
    }

    // Task 12 Extension: Deposit funds view & handler
    @GetMapping("/deposit")
    public String showDepositForm(Model model) {
        model.addAttribute("customers", bankService.getAllCustomers());
        return "deposit";
    }

    @PostMapping("/deposit")
    public String deposit(@RequestParam("accountNumber") String accountNumber,
                         @RequestParam("amount") BigDecimal amount,
                         RedirectAttributes redirectAttributes) {
        try {
            bankService.deposit(accountNumber, amount);
            redirectAttributes.addFlashAttribute("success", "Deposit of LKR " + amount + " to account " + accountNumber + " completed successfully.");
        } catch (Exception ex) {
            log.error("Deposit failed: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/deposit";
    }

    // Task 12 Extension: Withdraw funds view & handler
    @GetMapping("/withdraw")
    public String showWithdrawForm(Model model) {
        model.addAttribute("customers", bankService.getAllCustomers());
        return "withdraw";
    }

    @PostMapping("/withdraw")
    public String withdraw(@RequestParam("accountNumber") String accountNumber,
                          @RequestParam("amount") BigDecimal amount,
                          RedirectAttributes redirectAttributes) {
        try {
            bankService.withdraw(accountNumber, amount);
            redirectAttributes.addFlashAttribute("success", "Withdrawal of LKR " + amount + " from account " + accountNumber + " completed successfully.");
        } catch (Exception ex) {
            log.error("Withdrawal failed: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/withdraw";
    }
}
