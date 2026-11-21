package com.ticketflow.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketflow.pricing.grpc.BatchQuoteResponse;
import com.ticketflow.pricing.grpc.GetPriceRequest;
import com.ticketflow.pricing.grpc.GetPriceResponse;
import com.ticketflow.pricing.grpc.PricingServiceGrpc;
import com.ticketflow.pricing.grpc.PromoStatus;
import com.ticketflow.pricing.grpc.QuoteItem;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test d'intégration : appelle le VRAI serveur gRPC (port 9098) avec le VRAI PostgreSQL (localhost:5433).
 * Avant de lancer : conteneur ticketflow-pricing-db démarré et $env:DB_PASSWORD défini.
 * Les tests de calcul créent leurs propres catégories et promotions (noms uniques) puis les suppriment :
 * ils ne dépendent pas des prix d'exemple. Un seul test vérifie que les données d'exemple (data.sql) sont chargées.
 */
@SpringBootTest(properties = {"grpc.server.port=9098", "server.port=0"})
class PricingGrpcServiceTest {

    private static ManagedChannel channel;
    private static PricingServiceGrpc.PricingServiceBlockingStub stub;
    private static PricingServiceGrpc.PricingServiceStub asyncStub;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<String> createdCategories = new ArrayList<>();
    private final List<String> createdPromotions = new ArrayList<>();

    @BeforeAll
    static void connect() {
        channel = ManagedChannelBuilder.forAddress("localhost", 9098).usePlaintext().build();
        stub = PricingServiceGrpc.newBlockingStub(channel);
        asyncStub = PricingServiceGrpc.newStub(channel);
    }

    @AfterAll
    static void disconnect() {
        channel.shutdownNow();
    }

    @AfterEach
    void cleanUp() {
        createdCategories.forEach(c -> jdbc.update("DELETE FROM price_category WHERE category = ?", c));
        createdPromotions.forEach(c -> jdbc.update("DELETE FROM promotion WHERE code = ?", c));
    }

    @Test
    void sampleData_isLoadedFromDataSql() {
        GetPriceResponse standard = stub.getPrice(request("STANDARD", 1, ""));
        GetPriceResponse vip = stub.getPrice(request("VIP", 2, "NOUR10"));

        assertThat(standard.getUnitPriceMillimes()).isEqualTo(30000);
        assertThat(standard.getCurrency()).isEqualTo("TND");
        assertThat(vip.getUnitPriceMillimes()).isEqualTo(60000);
        assertThat(vip.getPromoStatus()).isEqualTo(PromoStatus.PROMO_APPLIED);
        assertThat(vip.getDiscountMillimes()).isEqualTo(12000);
        assertThat(vip.getTotalMillimes()).isEqualTo(108000);
    }

    @Test
    void getPrice_withoutPromo() {
        String category = newCategory(12345);

        GetPriceResponse response = stub.getPrice(request(category, 3, ""));

        assertThat(response.getUnitPriceMillimes()).isEqualTo(12345);
        assertThat(response.getSubtotalMillimes()).isEqualTo(37035);
        assertThat(response.getDiscountMillimes()).isZero();
        assertThat(response.getTotalMillimes()).isEqualTo(37035);
        assertThat(response.getPromoStatus()).isEqualTo(PromoStatus.NO_PROMO);
        assertThat(response.getAppliedPromotion()).isEmpty();
    }

    @Test
    void getPrice_withValidPromo_roundsToTheNearestMillime() {
        String category = newCategory(12345);
        String promo = newPromotion("10.00", true, null, null);

        GetPriceResponse response = stub.getPrice(request(category, 3, promo));

        assertThat(response.getPromoStatus()).isEqualTo(PromoStatus.PROMO_APPLIED);
        assertThat(response.getAppliedPromotion()).isEqualTo(promo);
        assertThat(response.getDiscountMillimes()).isEqualTo(3704);
        assertThat(response.getTotalMillimes()).isEqualTo(33331);
    }

    @Test
    void getPrice_promoCodeIsCaseInsensitive() {
        String category = newCategory(10000);
        String promo = newPromotion("10.00", true, null, null);

        GetPriceResponse response = stub.getPrice(request(category, 1, promo.toLowerCase()));

        assertThat(response.getPromoStatus()).isEqualTo(PromoStatus.PROMO_APPLIED);
        assertThat(response.getTotalMillimes()).isEqualTo(9000);
    }

    @Test
    void getPrice_unknownInactiveOrExpiredPromo_isInvalidButPriceIsStillComputed() {
        String category = newCategory(10000);
        String inactive = newPromotion("10.00", false, null, null);
        String expired = newPromotion("10.00", true, null, LocalDate.now().minusDays(2));
        String notYetValid = newPromotion("10.00", true, LocalDate.now().plusDays(2), null);

        for (String code : List.of("CODE-INCONNU", inactive, expired, notYetValid)) {
            GetPriceResponse response = stub.getPrice(request(category, 1, code));

            assertThat(response.getPromoStatus()).as(code).isEqualTo(PromoStatus.PROMO_INVALID);
            assertThat(response.getDiscountMillimes()).as(code).isZero();
            assertThat(response.getTotalMillimes()).as(code).isEqualTo(10000);
        }
    }

    @Test
    void getPrice_unknownCategory_returnsNotFound() {
        assertThatThrownBy(() -> stub.getPrice(request("CATEGORIE-INCONNUE", 1, "")))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    void getPrice_invalidQuantity_returnsInvalidArgument() {
        assertThatThrownBy(() -> stub.getPrice(request("STANDARD", 0, "")))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    void getPrice_invalidSessionDate_returnsInvalidArgument() {
        GetPriceRequest bad = GetPriceRequest.newBuilder()
                .setCategory("STANDARD").setQuantity(1).setSessionDate("15/12/2026").build();

        assertThatThrownBy(() -> stub.getPrice(bad))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    void getPrice_validIsoSessionDate_isAccepted() {
        GetPriceRequest ok = GetPriceRequest.newBuilder()
                .setCategory("STANDARD").setQuantity(1).setSessionDate("2026-12-15T20:30:00").build();

        assertThat(stub.getPrice(ok).getTotalMillimes()).isEqualTo(30000);
    }

    /** Client streaming : 2 lignes envoyées, UNE seule réponse avec le total. */
    @Test
    void batchQuote_sumsAllLines() throws Exception {
        String standard = newCategory(30000);
        String vip = newCategory(60000);
        String promo = newPromotion("10.00", true, null, null);

        CompletableFuture<BatchQuoteResponse> result = new CompletableFuture<>();
        StreamObserver<QuoteItem> requests = asyncStub.batchQuote(collector(result));
        requests.onNext(item("ligne-1", standard, 2, ""));      // 2 x 30000 = 60000
        requests.onNext(item("ligne-2", vip, 1, promo));        // 60000 - 6000 = 54000
        requests.onCompleted();

        BatchQuoteResponse response = result.get(10, TimeUnit.SECONDS);

        assertThat(response.getLinesCount()).isEqualTo(2);
        assertThat(response.getLines(0).getLineId()).isEqualTo("ligne-1");
        assertThat(response.getLines(0).getPrice().getTotalMillimes()).isEqualTo(60000);
        assertThat(response.getLines(1).getLineId()).isEqualTo("ligne-2");
        assertThat(response.getLines(1).getPrice().getTotalMillimes()).isEqualTo(54000);
        assertThat(response.getTotalMillimes()).isEqualTo(114000);
        assertThat(response.getCurrency()).isEqualTo("TND");
    }

    @Test
    void batchQuote_oneUnknownCategory_failsWholeQuoteAndNamesTheLine() {
        String standard = newCategory(30000);

        CompletableFuture<BatchQuoteResponse> result = new CompletableFuture<>();
        StreamObserver<QuoteItem> requests = asyncStub.batchQuote(collector(result));
        requests.onNext(item("ligne-1", standard, 1, ""));
        requests.onNext(item("ligne-2", "CATEGORIE-INCONNUE", 1, ""));
        requests.onCompleted();

        assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .satisfies(e -> {
                    StatusRuntimeException cause = (StatusRuntimeException) e.getCause();
                    assertThat(cause.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                    assertThat(cause.getStatus().getDescription()).contains("ligne-2");
                });
    }

    @Test
    void batchQuote_emptyStream_returnsInvalidArgument() {
        CompletableFuture<BatchQuoteResponse> result = new CompletableFuture<>();
        StreamObserver<QuoteItem> requests = asyncStub.batchQuote(collector(result));
        requests.onCompleted();

        assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .satisfies(e -> assertThat(((StatusRuntimeException) e.getCause()).getStatus().getCode())
                        .isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    // ---------- Outils de test ----------

    private String newCategory(long priceMillimes) {
        String name = "TEST-" + UUID.randomUUID();
        jdbc.update("INSERT INTO price_category (category, base_price_millimes, currency) VALUES (?, ?, 'TND')",
                name, priceMillimes);
        createdCategories.add(name);
        return name;
    }

    private String newPromotion(String percent, boolean active, LocalDate validFrom, LocalDate validUntil) {
        String code = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        jdbc.update("INSERT INTO promotion (code, discount_percent, active, valid_from, valid_until) "
                        + "VALUES (?, ?::numeric, ?, ?, ?)",
                code, percent, active, validFrom, validUntil);
        createdPromotions.add(code);
        return code;
    }

    private static GetPriceRequest request(String category, int quantity, String promoCode) {
        return GetPriceRequest.newBuilder()
                .setCategory(category).setQuantity(quantity).setPromoCode(promoCode).build();
    }

    private static QuoteItem item(String lineId, String category, int quantity, String promoCode) {
        return QuoteItem.newBuilder()
                .setLineId(lineId).setCategory(category).setQuantity(quantity).setPromoCode(promoCode).build();
    }

    private static StreamObserver<BatchQuoteResponse> collector(CompletableFuture<BatchQuoteResponse> result) {
        return new StreamObserver<>() {
            @Override
            public void onNext(BatchQuoteResponse value) {
                result.complete(value);
            }

            @Override
            public void onError(Throwable t) {
                result.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                // la réponse unique a déjà été reçue dans onNext
            }
        };
    }
}