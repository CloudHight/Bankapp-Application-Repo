package com.motivalogic.bankapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivalogic.bankapp.domain.AccountStatus;
import com.motivalogic.bankapp.domain.AccountType;
import com.motivalogic.bankapp.dto.AccountRequest;
import com.motivalogic.bankapp.dto.AccountResponse;
import com.motivalogic.bankapp.dto.CustomerRequest;
import com.motivalogic.bankapp.exception.BusinessRuleException;
import com.motivalogic.bankapp.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AccountServiceTest {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;

    private Long customerId;

    @BeforeEach
    void setUp() {
        customerId = newCustomer("Grace");
    }

    private Long newCustomer(String firstName) {
        return customerService.create(new CustomerRequest(firstName, "Hopper",
                firstName.toLowerCase() + "-" + System.nanoTime() + "@example.com", null)).id();
    }

    private String openAccount(Long owner, String opening) {
        return accountService.open(
                new AccountRequest(owner, AccountType.CHECKING, new BigDecimal(opening))).accountNumber();
    }

    // ---------- opening an account ----------

    @Test
    void openGivesA12DigitAccountNumberAndRoundsTheBalance() {
        AccountResponse account = accountService.open(
                new AccountRequest(customerId, AccountType.CHECKING, new BigDecimal("10.005")));

        assertThat(account.accountNumber()).matches("\\d{12}");
        assertThat(account.balance()).isEqualByComparingTo("10.01");
    }

    // ---------- finding accounts ----------

    @Test
    void findByCustomerReturnsOnlyThatCustomersAccounts() {
        openAccount(customerId, "50.00");
        openAccount(customerId, "0.00");
        openAccount(newCustomer("Alan"), "75.00");

        assertThat(accountService.findByCustomer(customerId)).hasSize(2);
    }

    @Test
    void findByCustomerRejectsUnknownCustomer() {
        assertThatThrownBy(() -> accountService.findByCustomer(999_999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByAccountNumberRejectsUnknownAccount() {
        assertThatThrownBy(() -> accountService.findByAccountNumber("000000000000"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    // ---------- changing status ----------

    @Test
    void accountCanBeFrozen() {
        String account = openAccount(customerId, "100.00");

        AccountResponse updated = accountService.updateStatus(account, AccountStatus.FROZEN);

        assertThat(updated.status()).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    void accountWithZeroBalanceCanBeClosed() {
        String account = openAccount(customerId, "0.00");

        AccountResponse updated = accountService.updateStatus(account, AccountStatus.CLOSED);

        assertThat(updated.status()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void closingAccountWithMoneyInItIsRejected() {
        String account = openAccount(customerId, "100.00");

        assertThatThrownBy(() -> accountService.updateStatus(account, AccountStatus.CLOSED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("zero balance");
    }

    @Test
    void closedAccountCannotChangeStatus() {
        String account = openAccount(customerId, "0.00");
        accountService.updateStatus(account, AccountStatus.CLOSED);

        assertThatThrownBy(() -> accountService.updateStatus(account, AccountStatus.FROZEN))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("closed");
    }
}