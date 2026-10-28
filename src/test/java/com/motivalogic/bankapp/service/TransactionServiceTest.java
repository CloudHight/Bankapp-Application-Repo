package com.motivalogic.bankapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivalogic.bankapp.domain.AccountStatus;
import com.motivalogic.bankapp.domain.AccountType;
import com.motivalogic.bankapp.dto.AccountRequest;
import com.motivalogic.bankapp.dto.AccountResponse;
import com.motivalogic.bankapp.dto.CustomerRequest;
import com.motivalogic.bankapp.dto.MoneyRequest;
import com.motivalogic.bankapp.dto.TransferRequest;
import com.motivalogic.bankapp.exception.BusinessRuleException;
import com.motivalogic.bankapp.exception.InsufficientFundsException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TransactionServiceTest {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private TransactionService transactionService;

    private String accountA;
    private String accountB;

    @BeforeEach
    void setUp() {
        Long customerId = customerService.create(new CustomerRequest("Ada", "Lovelace",
                "ada-" + System.nanoTime() + "@example.com", null)).id();
        accountA = openAccount(customerId, "100.00");
        accountB = openAccount(customerId, "0.00");
    }

    private String openAccount(Long customerId, String opening) {
        AccountResponse account = accountService.open(
                new AccountRequest(customerId, AccountType.CHECKING, new BigDecimal(opening)));
        return account.accountNumber();
    }

    @Test
    void depositIncreasesBalance() {
        transactionService.deposit(accountA, new MoneyRequest(new BigDecimal("25.50"), "paycheck"));
        assertThat(accountService.findByAccountNumber(accountA).balance())
                .isEqualByComparingTo("125.50");
    }

    @Test
    void withdrawRejectsInsufficientFunds() {
        assertThatThrownBy(() -> transactionService.withdraw(accountA,
                new MoneyRequest(new BigDecimal("1000.00"), "rent")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void transferMovesFundsBetweenAccounts() {
        transactionService.transfer(new TransferRequest(accountA, accountB, new BigDecimal("40.00"), "loan"));

        assertThat(accountService.findByAccountNumber(accountA).balance()).isEqualByComparingTo("60.00");
        assertThat(accountService.findByAccountNumber(accountB).balance()).isEqualByComparingTo("40.00");
    }

    @Test
    void transferToSameAccountIsRejected() {
        assertThatThrownBy(() -> transactionService.transfer(
                new TransferRequest(accountA, accountA, new BigDecimal("1.00"), null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void transferFromFrozenAccountIsRejected() {
        accountService.updateStatus(accountA, AccountStatus.FROZEN);
        assertThatThrownBy(() -> transactionService.transfer(
                new TransferRequest(accountA, accountB, new BigDecimal("10.00"), null)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
