package com.motivalogic.bankapp.web;

import com.motivalogic.bankapp.domain.AccountStatus;
import com.motivalogic.bankapp.dto.AccountRequest;
import com.motivalogic.bankapp.dto.AccountResponse;
import com.motivalogic.bankapp.dto.PageResponse;
import com.motivalogic.bankapp.dto.TransactionResponse;
import com.motivalogic.bankapp.service.AccountService;
import com.motivalogic.bankapp.service.TransactionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    public AccountController(AccountService accountService, TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    @GetMapping(params = "customerId")
    public List<AccountResponse> listForCustomer(@RequestParam Long customerId) {
        return accountService.findByCustomer(customerId);
    }

    @GetMapping("/{accountNumber}")
    public AccountResponse get(@PathVariable String accountNumber) {
        return accountService.findByAccountNumber(accountNumber);
    }

    @PostMapping
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody AccountRequest request) {
        AccountResponse created = accountService.open(request);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + created.accountNumber())).body(created);
    }

    @PostMapping("/{accountNumber}/status")
    public AccountResponse updateStatus(@PathVariable String accountNumber, @RequestParam AccountStatus status) {
        return accountService.updateStatus(accountNumber, status);
    }

    @GetMapping("/{accountNumber}/transactions")
    public PageResponse<TransactionResponse> statement(@PathVariable String accountNumber,
                                                       @PageableDefault(size = 20) Pageable pageable) {
        return transactionService.statement(accountNumber, pageable);
    }
}
