package com.example.bankapp.config;

import com.example.bankapp.model.Customer;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Inserts three sample customers the first time the application starts (empty database). */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (customerRepository.count() == 0) {
            log.info("Database empty. Seeding sample customers into bank_db...");

            Customer kamal = new Customer("100001", "Kamal Perera", "kamal@example.com", new BigDecimal("50000.00"));
            Customer nimali = new Customer("100002", "Nimali Silva", "nimali@example.com", new BigDecimal("25000.00"));
            Customer saman = new Customer("100003", "Saman Kumara", "saman@example.com", new BigDecimal("10000.00"));
            // Task 12 Extension: Administrative revenue account for the 1% transfer fee
            Customer bankRevenue = new Customer("999999", "Bank Revenue Account", "admin@bank.com", new BigDecimal("0.00"));

            customerRepository.saveAll(List.of(kamal, nimali, saman, bankRevenue));

            log.info("Sample customers seeded successfully. Total customer accounts: {}", customerRepository.count());
            log.info("Total initial system balance: LKR {}", customerRepository.getTotalBalance());
        } else {
            log.info("Database already contains {} customer records. Skipping sample data initialization.",
                    customerRepository.count());
        }
    }
}
