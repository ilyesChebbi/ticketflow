# payment-service (SOAP, contract-first)

- Contract: `src/main/resources/xsd/payment.xsd` (copy in `contracts/xsd/`)
- WSDL (generated from the XSD): http://localhost:8085/ws/payment.wsdl
- SOAP endpoint: POST http://localhost:8085/ws
- Operations: `authorizePayment`, `refundPayment`
- Rules: card token must end with `_ok`; amount must be <= 5000, otherwise a SOAP Fault is returned.

## Run
    cd payment-service
    mvn spring-boot:run

## Test
    mvn test
    bash ../scripts/soap-test.sh
