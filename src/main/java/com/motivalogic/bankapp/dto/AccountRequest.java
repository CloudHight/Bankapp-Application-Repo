package com.motivalogic.bankapp.dto;

import com.motivalogic.bankapp.domain.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AccountRequest(
        @NotNull Long customerId,
        @NotNull AccountType type,
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal openingBalance
) {
}
