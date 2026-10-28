package com.motivalogic.bankapp.service;

import com.motivalogic.bankapp.domain.Customer;
import com.motivalogic.bankapp.dto.CustomerRequest;
import com.motivalogic.bankapp.dto.CustomerResponse;
import com.motivalogic.bankapp.exception.BusinessRuleException;
import com.motivalogic.bankapp.exception.ResourceNotFoundException;
import com.motivalogic.bankapp.repository.CustomerRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> findAll() {
        return customerRepository.findAll(Sort.by("lastName", "firstName")).stream()
                .map(CustomerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        return CustomerResponse.from(getCustomer(id));
    }

    public CustomerResponse create(CustomerRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("A customer with email " + request.email() + " already exists");
        }
        Customer customer = new Customer(request.firstName(), request.lastName(), request.email(), request.phone());
        return CustomerResponse.from(customerRepository.save(customer));
    }

    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = getCustomer(id);
        customerRepository.findByEmail(request.email())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessRuleException("A customer with email " + request.email() + " already exists");
                });
        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setEmail(request.email());
        customer.setPhone(request.phone());
        return CustomerResponse.from(customer);
    }

    public void delete(Long id) {
        Customer customer = getCustomer(id);
        customerRepository.delete(customer);
    }

    Customer getCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer " + id + " not found"));
    }
}
