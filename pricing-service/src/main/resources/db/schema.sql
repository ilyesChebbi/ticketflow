-- Schéma de pricing-service (exécuté à chaque démarrage : CREATE ... IF NOT EXISTS ne casse rien).
-- Tous les montants sont en MILLIMES de dinar tunisien (1 TND = 1000 millimes).

-- Grille de prix : un prix de base par catégorie de place.
CREATE TABLE IF NOT EXISTS price_category (
    category            VARCHAR(50) PRIMARY KEY,
    base_price_millimes BIGINT      NOT NULL CHECK (base_price_millimes > 0),
    currency            VARCHAR(3)  NOT NULL DEFAULT 'TND'
);

-- Promotions : un code, un pourcentage de réduction, une période de validité optionnelle.
CREATE TABLE IF NOT EXISTS promotion (
    code             VARCHAR(50)  PRIMARY KEY,
    discount_percent NUMERIC(5,2) NOT NULL CHECK (discount_percent > 0 AND discount_percent <= 100),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    valid_from       DATE,
    valid_until      DATE
);