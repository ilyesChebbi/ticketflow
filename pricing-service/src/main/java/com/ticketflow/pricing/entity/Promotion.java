package com.ticketflow.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Une promotion (table promotion, remplie par db/data.sql). */
@Entity
@Table(name = "promotion")
public class Promotion {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "discount_percent")
    private BigDecimal discountPercent;

    @Column(name = "active")
    private boolean active;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    protected Promotion() {
        // requis par JPA
    }

    public String getCode() { return code; }
    public BigDecimal getDiscountPercent() { return discountPercent; }

    /** Valable si active et si la date du jour est dans la période (bornes incluses, bornes vides = illimité). */
    public boolean isValidOn(LocalDate day) {
        return active
                && (validFrom == null || !day.isBefore(validFrom))
                && (validUntil == null || !day.isAfter(validUntil));
    }
}