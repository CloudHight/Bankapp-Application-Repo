package com.motivalogic.bankapp.dto;

import com.motivalogic.bankapp.domain.Account;
import com.motivalogic.bankapp.domain.AccountStatus;
import com.motivalogic.bankapp.domain.AccountType;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String accountNumber,
        Long customerId,
        AccountType type,
        AccountStatus status,
        BigDecimal balance,
        String currency,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCustomer().getId(),
                account.getType(),
                account.getStatus(),
                account.getBalance(),
                account.getCurrency(),
                account.getCreatedAt()
        );
    }
}
