package com.ticketflow.pricing.service;

import com.ticketflow.pricing.entity.PriceCategory;
import com.ticketflow.pricing.entity.Promotion;
import com.ticketflow.pricing.grpc.BatchQuoteResponse;
import com.ticketflow.pricing.grpc.GetPriceResponse;
import com.ticketflow.pricing.grpc.PromoStatus;
import com.ticketflow.pricing.grpc.QuoteLine;
import com.ticketflow.pricing.repository.PriceCategoryRepository;
import com.ticketflow.pricing.repository.PromotionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Logique métier du calcul de prix. Aucun prix ni aucune promotion n'est écrit ici :
 * tout vient de la base PostgreSQL (tables price_category et promotion).
 *
 * Erreurs : IllegalArgumentException (requête invalide) et NoSuchElementException (catégorie inconnue)
 * sont transformées en codes gRPC par PricingGrpcService.
 *
 * Règle de date : pas de majoration ni de réduction pour le moment. session_date est vérifiée (format ISO)
 * mais n'influence pas encore le prix ; c'est ici qu'on ajoutera une règle plus tard.
 */
@Service
public class PricingService {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    private final PriceCategoryRepository categories;
    private final PromotionRepository promotions;

    public PricingService(PriceCategoryRepository categories, PromotionRepository promotions) {
        this.categories = categories;
        this.promotions = promotions;
    }

    public GetPriceResponse getPrice(String category, int quantity, String sessionDate, String promoCode) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category est obligatoire");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity doit être supérieure à 0");
        }
        checkSessionDate(sessionDate);

        PriceCategory priceCategory = categories.findById(category)
                .orElseThrow(() -> new NoSuchElementException("Catégorie inconnue : " + category));

        // Promotion : pas de code -> NO_PROMO ; code valide -> PROMO_APPLIED ; sinon PROMO_INVALID (prix sans promo)
        PromoStatus promoStatus = PromoStatus.NO_PROMO;
        String appliedPromotion = "";
        BigDecimal discountPercent = BigDecimal.ZERO;

        String code = promoCode == null ? "" : promoCode.trim().toUpperCase();
        if (!code.isEmpty()) {
            LocalDate today = LocalDate.now(TUNIS);
            Optional<Promotion> promotion = promotions.findById(code).filter(p -> p.isValidOn(today));
            if (promotion.isPresent()) {
                promoStatus = PromoStatus.PROMO_APPLIED;
                appliedPromotion = promotion.get().getCode();
                discountPercent = promotion.get().getDiscountPercent();
            } else {
                promoStatus = PromoStatus.PROMO_INVALID;
            }
        }

        PriceCalculator.Result result =
                PriceCalculator.compute(priceCategory.getBasePriceMillimes(), quantity, discountPercent);

        return GetPriceResponse.newBuilder()
                .setUnitPriceMillimes(result.unitPriceMillimes())
                .setQuantity(result.quantity())
                .setSubtotalMillimes(result.subtotalMillimes())
                .setDiscountMillimes(result.discountMillimes())
                .setTotalMillimes(result.totalMillimes())
                .setCurrency(priceCategory.getCurrency())
                .setPromoStatus(promoStatus)
                .setAppliedPromotion(appliedPromotion)
                .build();
    }

    /** Assemble le devis global d'un BatchQuote : somme des lignes, une seule devise. */
    public BatchQuoteResponse buildBatchResponse(List<QuoteLine> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("BatchQuote doit contenir au moins une ligne");
        }

        long total = 0;
        String currency = lines.get(0).getPrice().getCurrency();
        for (QuoteLine line : lines) {
            if (!currency.equals(line.getPrice().getCurrency())) {
                throw new IllegalArgumentException("Les lignes du devis ont des devises différentes");
            }
            total = Math.addExact(total, line.getPrice().getTotalMillimes());
        }

        return BatchQuoteResponse.newBuilder()
                .addAllLines(lines)
                .setTotalMillimes(total)
                .setCurrency(currency)
                .build();
    }

    private void checkSessionDate(String sessionDate) {
        if (sessionDate == null || sessionDate.isBlank()) {
            return;
        }
        try {
            LocalDateTime.parse(sessionDate);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("session_date doit être au format ISO, ex. 2026-12-15T20:30:00");
        }
    }
}