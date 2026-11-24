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

    private String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    // ---------- existing tests (unchanged) ----------

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

    // ---------- CT-48: listing customers ----------

    @Test
    void findAllListsCustomersSortedByLastName() {
        CustomerResponse turing = customerService.create(
                new CustomerRequest("Alan", "Turing", uniqueEmail("alan"), null));
        CustomerResponse babbage = customerService.create(
                new CustomerRequest("Charles", "Babbage", uniqueEmail("charles"), null));

        var all = customerService.findAll();

        assertThat(all).extracting(CustomerResponse::id).contains(turing.id(), babbage.id());
        assertThat(all).extracting(CustomerResponse::lastName).isSorted();
    }

    // ---------- CT-48: updating a customer ----------

    @Test
    void updateChangesCustomerDetails() {
        Long id = customerService.create(sample(uniqueEmail("grace"))).id();
        String newEmail = uniqueEmail("ada");

        CustomerResponse updated = customerService.update(id,
                new CustomerRequest("Ada", "Lovelace", newEmail, "555-0199"));

        assertThat(updated.lastName()).isEqualTo("Lovelace");
        assertThat(customerService.findById(id).lastName()).isEqualTo("Lovelace");
    }

    @Test
    void updateKeepingTheSameEmailIsAllowed() {
        String email = uniqueEmail("same");
        Long id = customerService.create(sample(email)).id();

        CustomerResponse updated = customerService.update(id,
                new CustomerRequest("Grace", "Brewster", email, "555-0100"));

        assertThat(updated.lastName()).isEqualTo("Brewster");
    }

    @Test
    void updateToAnotherCustomersEmailIsRejected() {
        String takenEmail = uniqueEmail("taken");
        customerService.create(sample(takenEmail));
        Long id = customerService.create(sample(uniqueEmail("mine"))).id();

        assertThatThrownBy(() -> customerService.update(id, sample(takenEmail)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateUnknownCustomerIsRejected() {
        assertThatThrownBy(() -> customerService.update(999_999L, sample(uniqueEmail("ghost"))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- CT-48: deleting a customer ----------

    @Test
    void deletedCustomerCanNoLongerBeFound() {
        Long id = customerService.create(sample(uniqueEmail("gone"))).id();

        customerService.delete(id);

        assertThatThrownBy(() -> customerService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletingUnknownCustomerIsRejected() {
        assertThatThrownBy(() -> customerService.delete(999_999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}