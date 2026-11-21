package com.ticketflow.pricing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcul pur d'un prix, sans Spring ni base de données (facile à tester).
 * Tous les montants sont des entiers en MILLIMES : aucune erreur d'arrondi de type double.
 * Règle d'arrondi de la réduction : au millime le plus proche (0,5 arrondi vers le haut).
 */
public final class PriceCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record Result(long unitPriceMillimes, int quantity, long subtotalMillimes,
                         long discountMillimes, long totalMillimes) {}

    private PriceCalculator() {
    }

    /**
     * @param discountPercent pourcentage de réduction (10 = 10 %), ou null / 0 s'il n'y a pas de promotion
     * @throws ArithmeticException si le montant dépasse la capacité d'un long
     */
    public static Result compute(long unitPriceMillimes, int quantity, BigDecimal discountPercent) {
        long subtotal = Math.multiplyExact(unitPriceMillimes, (long) quantity);

        long discount = 0;
        if (discountPercent != null && discountPercent.signum() > 0) {
            discount = BigDecimal.valueOf(subtotal)
                    .multiply(discountPercent)
                    .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                    .longValueExact();
            discount = Math.min(discount, subtotal);
        }

        return new Result(unitPriceMillimes, quantity, subtotal, discount, subtotal - discount);
    }
}