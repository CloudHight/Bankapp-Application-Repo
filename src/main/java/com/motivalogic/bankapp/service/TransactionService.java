package com.motivalogic.bankapp.service;

import com.motivalogic.bankapp.domain.Account;
import com.motivalogic.bankapp.domain.Transaction;
import com.motivalogic.bankapp.domain.TransactionType;
import com.motivalogic.bankapp.dto.MoneyRequest;
import com.motivalogic.bankapp.dto.PageResponse;
import com.motivalogic.bankapp.dto.TransactionResponse;
import com.motivalogic.bankapp.dto.TransferRequest;
import com.motivalogic.bankapp.exception.BusinessRuleException;
import com.motivalogic.bankapp.exception.InsufficientFundsException;
import com.motivalogic.bankapp.exception.ResourceNotFoundException;
import com.motivalogic.bankapp.repository.AccountRepository;
import com.motivalogic.bankapp.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> statement(String accountNumber, Pageable pageable) {
        Account account = accountByNumber(accountNumber);
        return PageResponse.from(
                transactionRepository.findByAccountIdOrderByCreatedAtDesc(account.getId(), pageable)
                        .map(TransactionResponse::from));
    }

    public TransactionResponse deposit(String accountNumber, MoneyRequest request) {
        Account account = lockActiveAccount(accountNumber);
        BigDecimal amount = normalize(request.amount());
        account.setBalance(account.getBalance().add(amount));
        Transaction tx = record(account, TransactionType.DEPOSIT, amount, request.description(), null);
        return TransactionResponse.from(tx);
    }

    public TransactionResponse withdraw(String accountNumber, MoneyRequest request) {
        Account account = lockActiveAccount(accountNumber);
        BigDecimal amount = normalize(request.amount());
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Account " + accountNumber + " has insufficient funds");
        }
        account.setBalance(account.getBalance().subtract(amount));
        Transaction tx = record(account, TransactionType.WITHDRAWAL, amount, request.description(), null);
        return TransactionResponse.from(tx);
    }

    public TransactionResponse transfer(TransferRequest request) {
        if (request.fromAccountNumber().equals(request.toAccountNumber())) {
            throw new BusinessRuleException("Cannot transfer to the same account");
        }
        BigDecimal amount = normalize(request.amount());

        Account first = accountByNumber(request.fromAccountNumber());
        Account second = accountByNumber(request.toAccountNumber());

        // Lock in a consistent order (by id) to avoid deadlocks.
        Account lowId = first.getId() < second.getId() ? first : second;
        Account highId = first.getId() < second.getId() ? second : first;
        lockById(lowId.getId());
        lockById(highId.getId());

        Account from = accountByNumber(request.fromAccountNumber());
        Account to = accountByNumber(request.toAccountNumber());
        requireActive(from);
        requireActive(to);

        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Account " + from.getAccountNumber() + " has insufficient funds");
        }

        String reference = UUID.randomUUID().toString();
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        Transaction debit = recordWithReference(from, TransactionType.TRANSFER_OUT, amount, request.description(),
                to.getAccountNumber(), reference);
        recordWithReference(to, TransactionType.TRANSFER_IN, amount, request.description(),
                from.getAccountNumber(), reference);
        return TransactionResponse.from(debit);
    }

    private Transaction record(Account account, TransactionType type, BigDecimal amount, String description,
                               String counterparty) {
        return recordWithReference(account, type, amount, description, counterparty, UUID.randomUUID().toString());
    }

    private Transaction recordWithReference(Account account, TransactionType type, BigDecimal amount, String description,
                                            String counterparty, String reference) {
        Transaction tx = new Transaction(account, type, amount, account.getBalance(), description, reference);
        tx.setCounterpartyAccountNumber(counterparty);
        return transactionRepository.save(tx);
    }

    private Account lockActiveAccount(String accountNumber) {
        Account account = accountByNumber(accountNumber);
        lockById(account.getId());
        Account locked = accountByNumber(accountNumber);
        requireActive(locked);
        return locked;
    }

    private void lockById(Long id) {
        accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account " + id + " not found"));
    }

    private Account accountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account " + accountNumber + " not found"));
    }

    private void requireActive(Account account) {
        if (!account.isActive()) {
            throw new BusinessRuleException("Account " + account.getAccountNumber() + " is not active");
        }
    }

    private BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
