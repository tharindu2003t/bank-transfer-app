package com.example.bankapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Account number is required")
    @Size(min = 6, max = 6, message = "Account number must be exactly 6 digits")
    @Pattern(regexp = "^[0-9]{6}$", message = "Account number must be 6 digits")
    @Column(name = "account_number", unique = true, nullable = false, length = 6)
    private String accountNumber;

    @NotBlank(message = "Full name is required")
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Column(nullable = false, unique = true)
    private String email;

    @NotNull(message = "Opening balance is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Balance cannot be negative")
    @Digits(integer = 12, fraction = 2, message = "Invalid amount format")
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    public Customer(String accountNumber, String fullName, String email, BigDecimal balance) {
        this.accountNumber = accountNumber;
        this.fullName = fullName;
        this.email = email;
        this.balance = balance;
    }
}
