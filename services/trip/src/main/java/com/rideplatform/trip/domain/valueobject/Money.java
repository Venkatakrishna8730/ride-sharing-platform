package com.rideplatform.trip.domain.valueobject;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Embeddable
@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class Money {

    private BigDecimal amount;
    private String currency;

    public Money(BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative or null");
        }
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = (currency != null && !currency.isBlank()) ? currency.toUpperCase() : "USD";
    }

    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount), "USD");
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }
}
