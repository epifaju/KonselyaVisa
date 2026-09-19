package com.konselyavisa.payment.persistence;

import com.konselyavisa.payment.domain.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = {"order", "order.caseFile"})
    Optional<Payment> findByProviderCodeAndProviderReference(String providerCode, String providerReference);

    @EntityGraph(attributePaths = {"order", "order.caseFile"})
    Optional<Payment> findDetailedById(UUID id);

    Optional<Payment> findFirstByOrder_IdOrderByCreatedAtDesc(UUID orderId);
}
