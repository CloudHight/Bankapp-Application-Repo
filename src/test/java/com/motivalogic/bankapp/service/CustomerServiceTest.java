package com.motivalogic.bankapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivalogic.bankapp.dto.CustomerRequest;
import com.motivalogic.bankapp.dto.CustomerResponse;
import com.motivalogic.bankapp.exception.BusinessRuleException;
import com.motivalogic.bankapp.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CustomerServiceTest {

    @Autowired
    private CustomerService customerService;

    private CustomerRequest sample(String email) {
        return new CustomerRequest("Grace", "Hopper", email, "555-0100");
    }

    @Test
    void createAndFetchCustomer() {
        CustomerResponse created = customerService.create(sample("grace-" + System.nanoTime() + "@example.com"));
        assertThat(created.id()).isNotNull();
        assertThat(customerService.findById(created.id()).lastName()).isEqualTo("Hopper");
    }

    @Test
    void duplicateEmailIsRejected() {
        String email = "dup-" + System.nanoTime() + "@example.com";
        customerService.create(sample(email));
        assertThatThrownBy(() -> customerService.create(sample(email)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void missingCustomerThrowsNotFound() {
        assertThatThrownBy(() -> customerService.findById(999_999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
