package com.motivalogic.bankapp.web;

import com.motivalogic.bankapp.dto.MoneyRequest;
import com.motivalogic.bankapp.dto.TransactionResponse;
import com.motivalogic.bankapp.dto.TransferRequest;
import com.motivalogic.bankapp.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/accounts/{accountNumber}/deposits")
    public TransactionResponse deposit(@PathVariable String accountNumber, @Valid @RequestBody MoneyRequest request) {
        return transactionService.deposit(accountNumber, request);
    }

    @PostMapping("/accounts/{accountNumber}/withdrawals")
    public TransactionResponse withdraw(@PathVariable String accountNumber, @Valid @RequestBody MoneyRequest request) {
        return transactionService.withdraw(accountNumber, request);
    }

    @PostMapping("/transfers")
    public TransactionResponse transfer(@Valid @RequestBody TransferRequest request) {
        return transactionService.transfer(request);
    }
}
