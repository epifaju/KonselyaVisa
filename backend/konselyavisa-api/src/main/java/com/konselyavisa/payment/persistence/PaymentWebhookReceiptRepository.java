package com.konselyavisa.payment.persistence;

import com.konselyavisa.payment.domain.PaymentWebhookReceipt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookReceiptRepository extends JpaRepository<PaymentWebhookReceipt, UUID> {

    boolean existsByProviderCodeAndIdempotencyKey(String providerCode, String idempotencyKey);
}
