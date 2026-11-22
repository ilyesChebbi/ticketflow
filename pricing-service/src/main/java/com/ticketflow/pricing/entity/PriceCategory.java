package com.ticketflow.pricing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Une ligne de la grille de prix (table price_category, remplie par db/data.sql). */
@Entity
@Table(name = "price_category")
public class PriceCategory {

    @Id
    @Column(name = "category")
    private String category;

    @Column(name = "base_price_millimes")
    private long basePriceMillimes;

    @Column(name = "currency")
    private String currency;

    protected PriceCategory() {
        // requis par JPA
    }

    public String getCategory() { return category; }
    public long getBasePriceMillimes() { return basePriceMillimes; }
    public String getCurrency() { return currency; }
}