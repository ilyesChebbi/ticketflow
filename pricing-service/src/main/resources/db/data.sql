-- Données d'EXEMPLE (pas des tarifs officiels).
-- ON CONFLICT DO NOTHING : ce script peut être rejoué à chaque démarrage sans doublon,
-- et il n'écrase JAMAIS un prix ou une promotion que tu as modifié(e) après coup.

INSERT INTO price_category (category, base_price_millimes, currency) VALUES
    ('STANDARD', 30000, 'TND'),   -- 30,000 TND
    ('VIP',      60000, 'TND')    -- 60,000 TND
ON CONFLICT (category) DO NOTHING;

INSERT INTO promotion (code, discount_percent, active) VALUES
    ('NOUR10', 10.00, TRUE)       -- 10 % de réduction, sans date limite
ON CONFLICT (code) DO NOTHING;