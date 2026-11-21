package com.ticketflow.pricing.grpc;

import com.ticketflow.pricing.service.PricingService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Point d'entrée gRPC (port 9091). Les classes PricingServiceGrpc, GetPriceRequest, QuoteItem, etc.
 * sont générées automatiquement à partir de contracts/proto/pricing.proto.
 *
 * Conventions d'erreur :
 *   requête invalide -> INVALID_ARGUMENT, catégorie inconnue -> NOT_FOUND, base KO -> UNAVAILABLE.
 * (Un code promo invalide n'est PAS une erreur : c'est PROMO_INVALID dans la réponse.)
 */
@GrpcService
public class PricingGrpcService extends PricingServiceGrpc.PricingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(PricingGrpcService.class);

    /** Garde-fou : un panier de plus de 100 lignes n'est pas réaliste. */
    private static final int MAX_BATCH_LINES = 100;

    private final PricingService pricingService;

    public PricingGrpcService(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    /** Unaire : une requête, une réponse. */
    @Override
    public void getPrice(GetPriceRequest request, StreamObserver<GetPriceResponse> responseObserver) {
        try {
            GetPriceResponse response = pricingService.getPrice(
                    request.getCategory(), request.getQuantity(), request.getSessionDate(), request.getPromoCode());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            responseObserver.onError(toStatus(e, "").asRuntimeException());
        }
    }

    /**
     * Client streaming : le client envoie plusieurs QuoteItem l'un après l'autre,
     * le serveur calcule chaque ligne au fur et à mesure et répond UNE seule fois à la fin.
     * Si une ligne est invalide, tout le devis est refusé (erreur gRPC qui nomme la ligne).
     */
    @Override
    public StreamObserver<QuoteItem> batchQuote(StreamObserver<BatchQuoteResponse> responseObserver) {
        return new StreamObserver<>() {
            private final List<QuoteLine> lines = new ArrayList<>();
            private boolean failed = false;

            @Override
            public void onNext(QuoteItem item) {
                if (failed) {
                    return;
                }
                try {
                    if (lines.size() >= MAX_BATCH_LINES) {
                        throw new IllegalArgumentException("Un devis ne peut pas dépasser " + MAX_BATCH_LINES + " lignes");
                    }
                    GetPriceResponse price = pricingService.getPrice(
                            item.getCategory(), item.getQuantity(), item.getSessionDate(), item.getPromoCode());
                    lines.add(QuoteLine.newBuilder().setLineId(item.getLineId()).setPrice(price).build());
                } catch (RuntimeException e) {
                    failed = true;
                    responseObserver.onError(toStatus(e, item.getLineId()).asRuntimeException());
                }
            }

            @Override
            public void onError(Throwable t) {
                // Le client a annulé ou coupé la connexion : rien à répondre.
                log.debug("BatchQuote interrompu par le client", t);
            }

            @Override
            public void onCompleted() {
                if (failed) {
                    return;
                }
                try {
                    responseObserver.onNext(pricingService.buildBatchResponse(lines));
                    responseObserver.onCompleted();
                } catch (RuntimeException e) {
                    responseObserver.onError(toStatus(e, "").asRuntimeException());
                }
            }
        };
    }

    private Status toStatus(RuntimeException e, String lineId) {
        String prefix = lineId == null || lineId.isBlank() ? "" : "[ligne " + lineId + "] ";
        if (e instanceof IllegalArgumentException || e instanceof ArithmeticException) {
            return Status.INVALID_ARGUMENT.withDescription(prefix + e.getMessage());
        }
        if (e instanceof NoSuchElementException) {
            return Status.NOT_FOUND.withDescription(prefix + e.getMessage());
        }
        if (e instanceof DataAccessException || e instanceof CannotCreateTransactionException) {
            log.error("Base de données indisponible", e);
            return Status.UNAVAILABLE.withDescription("Base de données indisponible");
        }
        log.error("Erreur inattendue", e);
        return Status.INTERNAL.withDescription("Erreur interne");
    }
}