package com.motivalogic.bankapp.service;

import com.motivalogic.bankapp.domain.Account;
import com.motivalogic.bankapp.domain.AccountStatus;
import com.motivalogic.bankapp.domain.Customer;
import com.motivalogic.bankapp.dto.AccountRequest;
import com.motivalogic.bankapp.dto.AccountResponse;
import com.motivalogic.bankapp.exception.BusinessRuleException;
import com.motivalogic.bankapp.exception.ResourceNotFoundException;
import com.motivalogic.bankapp.repository.AccountRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ACCOUNT_NUMBER_DIGITS = 12;

    private final AccountRepository accountRepository;
    private final CustomerService customerService;

    public AccountService(AccountRepository accountRepository, CustomerService customerService) {
        this.accountRepository = accountRepository;
        this.customerService = customerService;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findByCustomer(Long customerId) {
        customerService.getCustomer(customerId);
        return accountRepository.findByCustomerId(customerId).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse findByAccountNumber(String accountNumber) {
        return AccountResponse.from(getAccount(accountNumber));
    }

    public AccountResponse open(AccountRequest request) {
        Customer customer = customerService.getCustomer(request.customerId());
        BigDecimal openingBalance = request.openingBalance().setScale(2, java.math.RoundingMode.HALF_UP);
        Account account = new Account(generateAccountNumber(), customer, request.type(), openingBalance);
        return AccountResponse.from(accountRepository.save(account));
    }

    public AccountResponse updateStatus(String accountNumber, AccountStatus status) {
        Account account = getAccount(accountNumber);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessRuleException("Account " + accountNumber + " is closed and cannot change status");
        }
        if (status == AccountStatus.CLOSED && account.getBalance().signum() != 0) {
            throw new BusinessRuleException("Account " + accountNumber + " must have a zero balance before closing");
        }
        account.setStatus(status);
        return AccountResponse.from(account);
    }

    Account getAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account " + accountNumber + " not found"));
    }

    private String generateAccountNumber() {
        String candidate;
        do {
            StringBuilder sb = new StringBuilder(ACCOUNT_NUMBER_DIGITS);
            for (int i = 0; i < ACCOUNT_NUMBER_DIGITS; i++) {
                sb.append(RANDOM.nextInt(10));
            }
            candidate = sb.toString();
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }
}
