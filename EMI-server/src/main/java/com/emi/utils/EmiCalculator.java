package com.emi.utils;

import com.emi.exception.DomainException;
import com.emi.utils.enums.InterestType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * EMI maths. Whole-rupee EMIs (field collection is in cash); the last installment absorbs rounding.
 * Rate is a yearly percentage. FLAT: interest = principal x rate x months / 12, spread evenly.
 * REDUCING_BALANCE: standard amortisation on the outstanding principal.
 */
public final class EmiCalculator {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    private static final BigDecimal MONTHS_X_PERCENT = BigDecimal.valueOf(1200);

    private EmiCalculator() {
    }

    public record Row(int installmentNo, BigDecimal principal, BigDecimal interest, BigDecimal emi, BigDecimal balance) {
    }

    public record Plan(BigDecimal emi, BigDecimal totalInterest, BigDecimal totalPayable, List<Row> rows) {
    }

    public static Plan calculate(BigDecimal principal, BigDecimal annualRate, InterestType type, int months) {
        List<Row> rows = type == InterestType.FLAT
                ? flat(principal, annualRate, months)
                : reducing(principal, annualRate, months);

        for (Row row : rows) {
            if (row.principal().signum() <= 0 || row.interest().signum() < 0) {
                throw new DomainException("Finance amount is too small for this tenure and rate");
            }
        }

        BigDecimal totalInterest = rows.stream().map(Row::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Plan(rows.get(0).emi(), totalInterest, principal.add(totalInterest), rows);
    }

    private static List<Row> flat(BigDecimal principal, BigDecimal rate, int n) {
        BigDecimal count = BigDecimal.valueOf(n);
        BigDecimal totalInterest = principal.multiply(rate).multiply(count)
                .divide(MONTHS_X_PERCENT, 0, RoundingMode.HALF_UP);
        BigDecimal total = principal.add(totalInterest);
        BigDecimal emi = total.divide(count, 0, RoundingMode.CEILING);
        BigDecimal interestEach = totalInterest.divide(count, 0, RoundingMode.DOWN);

        List<Row> rows = new ArrayList<>();
        BigDecimal balance = principal;
        for (int i = 1; i <= n; i++) {
            boolean last = i == n;
            BigDecimal amount = last ? total.subtract(emi.multiply(BigDecimal.valueOf(n - 1L))) : emi;
            BigDecimal interest = last ? totalInterest.subtract(interestEach.multiply(BigDecimal.valueOf(n - 1L))) : interestEach;
            BigDecimal prin = amount.subtract(interest);
            balance = balance.subtract(prin);
            rows.add(row(i, prin, interest, amount, balance));
        }
        return rows;
    }

    private static List<Row> reducing(BigDecimal principal, BigDecimal rate, int n) {
        BigDecimal r = rate.divide(MONTHS_X_PERCENT, MC);
        BigDecimal emi;
        if (r.signum() == 0) {
            emi = principal.divide(BigDecimal.valueOf(n), 0, RoundingMode.CEILING);
        } else {
            BigDecimal growth = BigDecimal.ONE.add(r).pow(n, MC);
            emi = principal.multiply(r).multiply(growth)
                    .divide(growth.subtract(BigDecimal.ONE), MC)
                    .setScale(0, RoundingMode.HALF_UP);
        }

        List<Row> rows = new ArrayList<>();
        BigDecimal balance = principal;
        for (int i = 1; i <= n; i++) {
            BigDecimal interest = balance.multiply(r).setScale(0, RoundingMode.HALF_UP);
            BigDecimal prin = i == n ? balance : emi.subtract(interest).min(balance);
            balance = balance.subtract(prin);
            rows.add(row(i, prin, interest, prin.add(interest), balance));
        }
        return rows;
    }

    private static Row row(int no, BigDecimal principal, BigDecimal interest, BigDecimal emi, BigDecimal balance) {
        return new Row(no, money(principal), money(interest), money(emi), money(balance));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
