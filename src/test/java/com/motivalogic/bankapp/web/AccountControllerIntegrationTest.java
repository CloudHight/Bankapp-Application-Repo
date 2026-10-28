package com.motivalogic.bankapp.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivalogic.bankapp.dto.AccountRequest;
import com.motivalogic.bankapp.dto.AccountResponse;
import com.motivalogic.bankapp.dto.CustomerRequest;
import com.motivalogic.bankapp.dto.CustomerResponse;
import com.motivalogic.bankapp.dto.MoneyRequest;
import com.motivalogic.bankapp.domain.AccountType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccountControllerIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void openAccountThenDeposit() {
        CustomerRequest customerRequest = new CustomerRequest("Alan", "Turing",
                "alan-" + System.nanoTime() + "@example.com", null);
        CustomerResponse customer = rest.postForObject("/api/v1/customers", customerRequest, CustomerResponse.class);
        assertThat(customer).isNotNull();

        AccountRequest accountRequest = new AccountRequest(customer.id(), AccountType.SAVINGS, new BigDecimal("10.00"));
        ResponseEntity<AccountResponse> accountResponse =
                rest.postForEntity("/api/v1/accounts", accountRequest, AccountResponse.class);
        assertThat(accountResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String accountNumber = accountResponse.getBody().accountNumber();

        MoneyRequest deposit = new MoneyRequest(new BigDecimal("90.00"), "opening top-up");
        rest.postForEntity("/api/v1/accounts/{n}/deposits", deposit, Object.class, accountNumber);

        AccountResponse reloaded =
                rest.getForObject("/api/v1/accounts/{n}", AccountResponse.class, accountNumber);
        assertThat(reloaded.balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void healthEndpointIsUp() {
        ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");
    }
}
