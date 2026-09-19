package com.konselyavisa.payment;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.CaseFile;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.CaseStatus;
import com.konselyavisa.organization.persistence.OrganizationRepository;
import com.konselyavisa.outbox.OutboxAppender;
import com.konselyavisa.payment.api.OrderMapper;
import com.konselyavisa.payment.api.OrderResponse;
import com.konselyavisa.payment.domain.CaseOrder;
import com.konselyavisa.payment.domain.OrderStatus;
import com.konselyavisa.payment.domain.Payment;
import com.konselyavisa.payment.domain.PaymentStatus;
import com.konselyavisa.payment.domain.PaymentWebhookReceipt;
import com.konselyavisa.payment.persistence.CaseOrderRepository;
import com.konselyavisa.payment.persistence.PaymentRepository;
import com.konselyavisa.payment.persistence.PaymentWebhookReceiptRepository;
import com.konselyavisa.payment.provider.CheckoutSession;
import com.konselyavisa.payment.provider.ParsedWebhook;
import com.konselyavisa.payment.provider.PaymentOrder;
import com.konselyavisa.payment.provider.PaymentProvider;
import com.konselyavisa.payment.provider.PaymentProviderCodes;
import com.konselyavisa.payment.provider.PaymentProviderRegistry;
import com.konselyavisa.payment.provider.WebhookPayload;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Year;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentService {

    private static final EnumSet<OrderStatus> OPEN_OR_PAID =
            EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_MANUAL, OrderStatus.PAID);

    private final CaseService caseService;
    private final OrganizationRepository organizationRepository;
    private final CaseOrderRepository caseOrderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookReceiptRepository webhookReceiptRepository;
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentProviderAccessGuard paymentProviderAccessGuard;
    private final OutboxAppender outboxAppender;
    private final OrderMapper orderMapper;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(
            CaseService caseService,
            OrganizationRepository organizationRepository,
            CaseOrderRepository caseOrderRepository,
            PaymentRepository paymentRepository,
            PaymentWebhookReceiptRepository webhookReceiptRepository,
            PaymentProviderRegistry providerRegistry,
            PaymentProviderAccessGuard paymentProviderAccessGuard,
            OutboxAppender outboxAppender,
            OrderMapper orderMapper,
            PlatformTransactionManager transactionManager) {
        this.caseService = caseService;
        this.organizationRepository = organizationRepository;
        this.caseOrderRepository = caseOrderRepository;
        this.paymentRepository = paymentRepository;
        this.webhookReceiptRepository = webhookReceiptRepository;
        this.providerRegistry = providerRegistry;
        this.paymentProviderAccessGuard = paymentProviderAccessGuard;
        this.outboxAppender = outboxAppender;
        this.orderMapper = orderMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public OrderResponse createCheckout(UUID caseId) {
        CaseFile caseFile = caseService.requireAccessible(caseId);
        if (caseFile.getStatus() == CaseStatus.CANCELLED || caseFile.getStatus() == CaseStatus.COMPLETED) {
            throw BusinessException.badRequest("error.case.not_modifiable");
        }
        if (caseOrderRepository.existsByCaseFile_IdAndStatusIn(caseId, OPEN_OR_PAID)) {
            throw BusinessException.conflict("error.order.already_exists");
        }
        FrozenPricing pricing = FrozenPricing.from(caseFile.getProcedureVersion().getPricing());
        String providerCode = resolveProviderCode(caseFile.getOrganizationId());
        PaymentProvider provider = providerRegistry.require(providerCode);

        CaseOrder order = new CaseOrder();
        order.setCaseFile(caseFile);
        order.setReference(nextReference(caseFile.getOrganizationId()));
        order.setCurrency(pricing.currency());
        order.setAmountMinor(pricing.amountMinor());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setProviderCode(provider.code());
        caseOrderRepository.saveAndFlush(order);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProviderCode(provider.code());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCurrency(pricing.currency());
        payment.setAmountMinor(pricing.amountMinor());
        paymentRepository.saveAndFlush(payment);

        CheckoutSession session = provider.createCheckout(toPaymentOrder(order, payment));
        payment.setProviderReference(session.providerReference());
        payment.setCheckoutUrl(session.checkoutUrl());
        payment.setStatus(session.paymentStatus());
        if (session.paymentStatus() == PaymentStatus.PENDING_MANUAL) {
            order.setStatus(OrderStatus.PENDING_MANUAL);
        }
        return orderMapper.toResponse(order, payment);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listByCase(UUID caseId) {
        caseService.requireAccessible(caseId);
        return caseOrderRepository.findByCaseFile_IdOrderByCreatedAtDesc(caseId).stream()
                .map(order -> orderMapper.toResponse(
                        order, paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(order.getId()).orElse(null)))
                .toList();
    }

    @Transactional
    public OrderResponse confirmManual(UUID paymentId) {
        Payment payment = paymentRepository
                .findDetailedById(paymentId)
                .orElseThrow(() -> BusinessException.notFound("error.payment.not_found"));
        caseService.requireAccessible(payment.getOrder().getCaseFile().getId());
        if (payment.getStatus() != PaymentStatus.PENDING_MANUAL) {
            throw BusinessException.badRequest("error.payment.not_manual");
        }
        recordReceipt(payment, payment.getProviderCode(), "manual-confirm-" + payment.getId());
        completeIfNeeded(payment);
        return orderMapper.toResponse(payment.getOrder(), payment);
    }

    /**
     * Webhook is unauthenticated: platform admin GUC is set <strong>before</strong> the transaction
     * starts so RLS can resolve the payment by provider reference, then organization_id is bound
     * on the entities for outbox writes.
     */
    public void handleWebhook(String providerCode, Map<String, Object> payload) {
        handleWebhook(providerCode, new WebhookPayload(payload, null, null));
    }

    public void handleWebhook(String providerCode, WebhookPayload payload) {
        boolean previousAdmin = TenantContext.isPlatformAdmin();
        UUID previousOrg = TenantContext.getOrganizationId();
        TenantContext.setPlatformAdmin(true);
        try {
            transactionTemplate.executeWithoutResult(status -> processWebhook(providerCode, payload));
        } finally {
            TenantContext.setPlatformAdmin(previousAdmin);
            TenantContext.setOrganizationId(previousOrg);
        }
    }

    @Transactional
    public OrderResponse syncFromProvider(UUID paymentId) {
        Payment payment = paymentRepository
                .findDetailedById(paymentId)
                .orElseThrow(() -> BusinessException.notFound("error.payment.not_found"));
        caseService.requireAccessible(payment.getOrder().getCaseFile().getId());
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return orderMapper.toResponse(payment.getOrder(), payment);
        }
        PaymentProvider provider = providerRegistry.require(payment.getProviderCode());
        var view = provider.verify(payment.getProviderReference());
        if (view.status() == PaymentStatus.COMPLETED) {
            recordReceipt(payment, provider.code(), "sync-" + payment.getId());
            completeIfNeeded(payment);
        } else if (view.status() == PaymentStatus.FAILED && payment.getStatus() != PaymentStatus.COMPLETED) {
            payment.setStatus(PaymentStatus.FAILED);
        }
        return orderMapper.toResponse(payment.getOrder(), payment);
    }

    private void processWebhook(String providerCode, WebhookPayload payload) {
        PaymentProvider provider = providerRegistry.require(providerCode);
        ParsedWebhook parsed = provider.handleWebhook(payload);
        if (parsed.ignored()) {
            return;
        }
        if (webhookReceiptRepository.existsByProviderCodeAndIdempotencyKey(provider.code(), parsed.idempotencyKey())) {
            return;
        }
        Payment payment = paymentRepository
                .findByProviderCodeAndProviderReference(provider.code(), parsed.providerReference())
                .orElseThrow(() -> BusinessException.notFound("error.payment.not_found"));
        TenantContext.setOrganizationId(payment.getOrganizationId());
        recordReceipt(payment, provider.code(), parsed.idempotencyKey());
        if (!parsed.completed()) {
            if (payment.getStatus() != PaymentStatus.COMPLETED) {
                payment.setStatus(PaymentStatus.FAILED);
            }
            return;
        }
        completeIfNeeded(payment);
    }

    private void completeIfNeeded(Payment payment) {
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return;
        }
        payment.setStatus(PaymentStatus.COMPLETED);
        CaseOrder order = payment.getOrder();
        order.setStatus(OrderStatus.PAID);
        CaseFile caseFile = order.getCaseFile();
        if (caseFile.getStatus() == CaseStatus.CREATED) {
            caseFile.setStatus(CaseStatus.IN_PROGRESS);
        }
        outboxAppender.appendPaymentCompleted(caseFile.getId(), completedPayload(payment));
    }

    private void recordReceipt(Payment payment, String providerCode, String idempotencyKey) {
        if (webhookReceiptRepository.existsByProviderCodeAndIdempotencyKey(providerCode, idempotencyKey)) {
            return;
        }
        PaymentWebhookReceipt receipt = new PaymentWebhookReceipt();
        receipt.setPayment(payment);
        receipt.setProviderCode(providerCode);
        receipt.setIdempotencyKey(idempotencyKey);
        receipt.setOrganizationId(payment.getOrganizationId());
        try {
            webhookReceiptRepository.saveAndFlush(receipt);
        } catch (DataIntegrityViolationException ex) {
            // concurrent retry of the same webhook key
        }
    }

    private String resolveProviderCode(UUID organizationId) {
        return organizationRepository
                .findById(organizationId)
                .map(organization -> {
                    if (organization.getSettings() == null || organization.getSettings().getSettings() == null) {
                        return PaymentProviderCodes.MOCK;
                    }
                    Object code = organization.getSettings().getSettings().get("paymentProvider");
                    if (code instanceof String value && !value.isBlank()) {
                        return paymentProviderAccessGuard.resolveEnabledCode(value);
                    }
                    return PaymentProviderCodes.MOCK;
                })
                .orElse(PaymentProviderCodes.MOCK);
    }

    private String nextReference(UUID organizationId) {
        for (int attempt = 0; attempt < 8; attempt++) {
            String reference = "ORD-" + Year.now() + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            if (!caseOrderRepository.existsByOrganizationIdAndReference(organizationId, reference)) {
                return reference;
            }
        }
        throw BusinessException.conflict("error.order.reference_collision");
    }

    private static PaymentOrder toPaymentOrder(CaseOrder order, Payment payment) {
        return new PaymentOrder(
                order.getOrganizationId(),
                order.getCaseFile().getId(),
                order.getId(),
                payment.getId(),
                order.getReference(),
                order.getCurrency(),
                order.getAmountMinor());
    }

    private static Map<String, Object> completedPayload(Payment payment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", payment.getOrder().getCaseFile().getId().toString());
        payload.put("orderId", payment.getOrder().getId().toString());
        payload.put("paymentId", payment.getId().toString());
        payload.put("reference", payment.getOrder().getReference());
        payload.put("providerCode", payment.getProviderCode());
        payload.put("providerReference", payment.getProviderReference());
        payload.put("currency", payment.getCurrency());
        payload.put("amountMinor", payment.getAmountMinor());
        return payload;
    }
}
