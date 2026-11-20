#!/usr/bin/env bash
# Usage: bash scripts/soap-test.sh   (payment-service must be running on :8085)
URL="http://localhost:8085/ws"

call() {
  curl -s -X POST "$URL" \
    -H "Content-Type: text/xml; charset=utf-8" \
    -H 'SOAPAction: ""' \
    -d "<?xml version=\"1.0\"?>
<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:pay=\"http://ticketflow.com/payment/v1\">
  <soapenv:Header/>
  <soapenv:Body>
    <pay:authorizePaymentRequest>
      <pay:orderId>o-1</pay:orderId>
      <pay:amount>$1</pay:amount>
      <pay:currency>EUR</pay:currency>
      <pay:cardToken>$2</pay:cardToken>
    </pay:authorizePaymentRequest>
  </soapenv:Body>
</soapenv:Envelope>"
  echo
}

echo "--- OK (tok_visa_ok) ---";   call 120.00 tok_visa_ok
echo "--- KO (tok_bad) ---";       call 120.00 tok_bad
echo "--- KO (amount 6000) ---";   call 6000.00 tok_visa_ok
