-- Libération ATOMIQUE d'une réservation (compensation si le paiement échoue).
--
-- KEYS[1] = hash de la réservation (reservation:{reservation_id})
-- ARGV[1] = préfixe des clés de stock ("seats:")
-- ARGV[2] = canal pub/sub pour notifier WatchAvailability
--
-- Retour : {code, available, total}
-- code : 1 = RELEASED, 2 = NOT_FOUND (déjà libérée ou inconnue)

local r = redis.call('HMGET', KEYS[1], 'session_id', 'category', 'quantity')
if not r[1] then
  return {2, 0, 0}
end

local stockKey = ARGV[1] .. r[1] .. ':' .. r[2]
local quantity = tonumber(r[3])

local available = redis.call('HINCRBY', stockKey, 'available', quantity)
local total = tonumber(redis.call('HGET', stockKey, 'total') or '0')

redis.call('DEL', KEYS[1])
redis.call('PUBLISH', ARGV[2], r[1] .. '|' .. r[2] .. '|' .. available .. '|' .. total .. '|RELEASED_SEATS')

return {1, available, total}