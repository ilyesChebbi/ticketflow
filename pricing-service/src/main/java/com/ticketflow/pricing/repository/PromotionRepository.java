package com.ticketflow.pricing.repository;

import com.ticketflow.pricing.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

/** Recherche d'une promotion par son code : findById("NOUR10"). */
public interface PromotionRepository extends JpaRepository<Promotion, String> {
}