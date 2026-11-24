@echo off
REM Test rapide de la stack (Windows cmd). Lancer d'abord : docker compose up --build
echo --- Gateway health ---
curl.exe -s http://localhost:8080/actuator/health
echo.
echo --- GET /api/v1/events via la gateway (attendu : 200) ---
curl.exe -s -i http://localhost:8080/api/v1/events
echo.
echo --- SOAP OK (attendu : AUTHORIZED) ---
curl.exe -s -X POST http://localhost:8085/ws -H "Content-Type: text/xml; charset=utf-8" -d "@%~dp0ok.xml"
echo.
echo --- SOAP token KO (attendu : Fault REFUSED) ---
curl.exe -s -X POST http://localhost:8085/ws -H "Content-Type: text/xml; charset=utf-8" -d "@%~dp0bad-token.xml"
echo.
echo --- SOAP montant trop eleve (attendu : Fault REFUSED) ---
curl.exe -s -X POST http://localhost:8085/ws -H "Content-Type: text/xml; charset=utf-8" -d "@%~dp0big-amount.xml"
echo.
