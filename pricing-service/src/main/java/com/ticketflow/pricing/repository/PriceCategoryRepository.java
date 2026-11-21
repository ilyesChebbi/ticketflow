package com.ticketflow.pricing.repository;

import com.ticketflow.pricing.entity.PriceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

/** Recherche d'un prix de base par catégorie : findById("VIP"). */
public interface PriceCategoryRepository extends JpaRepository<PriceCategory, String> {
}