-- Réservation ATOMIQUE de places (un script Lua s'exécute en un seul bloc dans Redis :
-- aucune autre commande ne peut s'intercaler, donc pas de double réservation).
--
-- KEYS[1] = hash du stock          (seats:{session}:{category} -> available, total)
-- KEYS[2] = hash de la réservation (reservation:{reservation_id})
-- ARGV[1] = quantité demandée
-- ARGV[2] = session_id
-- ARGV[3] = category
-- ARGV[4] = canal pub/sub pour notifier WatchAvailability
--
-- Retour : {code, available, total}
-- code : 1 = RESERVED, 2 = INSUFFICIENT_SEATS, 3 = ALREADY_RESERVED, 0 = stock introuvable

local stock = redis.call('HMGET', KEYS[1], 'available', 'total')
if not stock[1] then
  return {0, 0, 0}
end

local available = tonumber(stock[1])
local total = tonumber(stock[2]) or 0

if redis.call('EXISTS', KEYS[2]) == 1 then
  return {3, available, total}
end

local quantity = tonumber(ARGV[1])
if available < quantity then
  return {2, available, total}
end

available = redis.call('HINCRBY', KEYS[1], 'available', -quantity)
redis.call('HSET', KEYS[2], 'session_id', ARGV[2], 'category', ARGV[3], 'quantity', ARGV[1])
redis.call('PUBLISH', ARGV[4], ARGV[2] .. '|' .. ARGV[3] .. '|' .. available .. '|' .. total .. '|RESERVED_SEATS')

return {1, available, total}