package com.motivalogic.bankapp.dto;

import com.motivalogic.bankapp.domain.Transaction;
import com.motivalogic.bankapp.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        String accountNumber,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        String reference,
        String counterpartyAccountNumber,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getAccount().getAccountNumber(),
                tx.getType(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getDescription(),
                tx.getReference(),
                tx.getCounterpartyAccountNumber(),
                tx.getCreatedAt()
        );
    }
}
