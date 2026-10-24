package com.konselyavisa.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.konselyavisa.catalog.domain.ProcedureVersion;
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
import com.konselyavisa.payment.persistence.CaseOrderRepository;
import com.konselyavisa.payment.persistence.PaymentRepository;
import com.konselyavisa.payment.persistence.PaymentWebhookReceiptRepository;
import com.konselyavisa.payment.provider.CheckoutSession;
import com.konselyavisa.payment.provider.ParsedWebhook;
import com.konselyavisa.payment.provider.PaymentProvider;
import com.konselyavisa.payment.provider.PaymentProviderCodes;
import com.konselyavisa.payment.provider.PaymentProviderRegistry;
import com.konselyavisa.payment.provider.PaymentStatusView;
import com.konselyavisa.payment.provider.WebhookPayload;
import com.konselyavisa.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private CaseService caseService;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private CaseOrderRepository caseOrderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentWebhookReceiptRepository webhookReceiptRepository;

    @Mock
    private PaymentProviderRegistry providerRegistry;

    @Mock
    private PaymentProviderAccessGuard paymentProviderAccessGuard;

    @Mock
    private OutboxAppender outboxAppender;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private PaymentProvider paymentProvider;

    private PaymentService service;
    private UUID orgId;
    private UUID caseId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        caseId = UUID.randomUUID();
        TenantContext.clear();
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new PaymentService(
                caseService,
                organizationRepository,
                caseOrderRepository,
                paymentRepository,
                webhookReceiptRepository,
                providerRegistry,
                paymentProviderAccessGuard,
                outboxAppender,
                orderMapper,
                transactionManager);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createCheckoutRejectsCompletedCase() {
        CaseFile caseFile = openCase(CaseStatus.COMPLETED);
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);

        assertThatThrownBy(() -> service.createCheckout(caseId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.case.not_modifiable");
    }

    @Test
    void createCheckoutRejectsExistingOpenOrder() {
        CaseFile caseFile = openCase(CaseStatus.CREATED);
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(caseOrderRepository.existsByCaseFile_IdAndStatusIn(eq(caseId), any())).thenReturn(true);

        assertThatThrownBy(() -> service.createCheckout(caseId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.order.already_exists");
    }

    @Test
    void createCheckoutPersistsPendingOrderAndSession() {
        CaseFile caseFile = openCase(CaseStatus.CREATED);
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(caseOrderRepository.existsByCaseFile_IdAndStatusIn(eq(caseId), any())).thenReturn(false);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());
        when(providerRegistry.require(PaymentProviderCodes.MOCK)).thenReturn(paymentProvider);
        when(paymentProvider.code()).thenReturn(PaymentProviderCodes.MOCK);
        when(caseOrderRepository.existsByOrganizationIdAndReference(eq(orgId), any())).thenReturn(false);
        when(caseOrderRepository.saveAndFlush(any(CaseOrder.class))).thenAnswer(inv -> {
            CaseOrder order = inv.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment payment = inv.getArgument(0);
            payment.setId(UUID.randomUUID());
            return payment;
        });
        when(paymentProvider.createCheckout(any()))
                .thenReturn(new CheckoutSession("prov-ref-1", "https://pay.example/1", PaymentStatus.PENDING));
        OrderResponse mapped = new OrderResponse(
                UUID.randomUUID(),
                caseId,
                "ORD-1",
                "EUR",
                8500L,
                OrderStatus.PENDING_PAYMENT,
                PaymentProviderCodes.MOCK,
                null);
        when(orderMapper.toResponse(any(CaseOrder.class), any(Payment.class))).thenReturn(mapped);

        OrderResponse response = service.createCheckout(caseId);

        assertThat(response).isSameAs(mapped);
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getProviderReference()).isEqualTo("prov-ref-1");
        assertThat(paymentCaptor.getValue().getCheckoutUrl()).isEqualTo("https://pay.example/1");
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void handleWebhookIgnoresParsedIgnoredEvents() {
        when(providerRegistry.require("mock")).thenReturn(paymentProvider);
        when(paymentProvider.handleWebhook(any(WebhookPayload.class)))
                .thenReturn(new ParsedWebhook("ref", "evt-1", false, true));

        service.handleWebhook("mock", Map.of());

        verify(webhookReceiptRepository, never()).existsByProviderCodeAndIdempotencyKey(any(), any());
        verify(paymentRepository, never()).findByProviderCodeAndProviderReference(any(), any());
    }

    @Test
    void handleWebhookIsIdempotentOnKnownReceipt() {
        when(providerRegistry.require("mock")).thenReturn(paymentProvider);
        when(paymentProvider.code()).thenReturn(PaymentProviderCodes.MOCK);
        when(paymentProvider.handleWebhook(any(WebhookPayload.class)))
                .thenReturn(new ParsedWebhook("ref-1", "evt-dup", true));
        when(webhookReceiptRepository.existsByProviderCodeAndIdempotencyKey(PaymentProviderCodes.MOCK, "evt-dup"))
                .thenReturn(true);

        service.handleWebhook("mock", Map.of());

        verify(paymentRepository, never()).findByProviderCodeAndProviderReference(any(), any());
        verify(outboxAppender, never()).appendPaymentCompleted(any(), any());
    }

    @Test
    void handleWebhookCompletesPaymentAndAdvancesCase() {
        Payment payment = pendingPayment();
        when(providerRegistry.require("mock")).thenReturn(paymentProvider);
        when(paymentProvider.code()).thenReturn(PaymentProviderCodes.MOCK);
        when(paymentProvider.handleWebhook(any(WebhookPayload.class)))
                .thenReturn(new ParsedWebhook("ref-1", "evt-ok", true));
        when(webhookReceiptRepository.existsByProviderCodeAndIdempotencyKey(PaymentProviderCodes.MOCK, "evt-ok"))
                .thenReturn(false);
        when(paymentRepository.findByProviderCodeAndProviderReference(PaymentProviderCodes.MOCK, "ref-1"))
                .thenReturn(Optional.of(payment));

        service.handleWebhook("mock", Map.of("event", "payment.completed"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment.getOrder().getCaseFile().getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
        verify(outboxAppender).appendPaymentCompleted(eq(caseId), any());
        verify(webhookReceiptRepository).saveAndFlush(any());
    }

    @Test
    void handleWebhookMarksFailedWhenNotCompleted() {
        Payment payment = pendingPayment();
        when(providerRegistry.require("mock")).thenReturn(paymentProvider);
        when(paymentProvider.code()).thenReturn(PaymentProviderCodes.MOCK);
        when(paymentProvider.handleWebhook(any(WebhookPayload.class)))
                .thenReturn(new ParsedWebhook("ref-1", "evt-fail", false));
        when(webhookReceiptRepository.existsByProviderCodeAndIdempotencyKey(PaymentProviderCodes.MOCK, "evt-fail"))
                .thenReturn(false);
        when(paymentRepository.findByProviderCodeAndProviderReference(PaymentProviderCodes.MOCK, "ref-1"))
                .thenReturn(Optional.of(payment));

        service.handleWebhook("mock", Map.of());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(outboxAppender, never()).appendPaymentCompleted(any(), any());
    }

    @Test
    void createCheckoutMarksManualPendingFromProviderSession() {
        CaseFile caseFile = openCase(CaseStatus.CREATED);
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(caseOrderRepository.existsByCaseFile_IdAndStatusIn(eq(caseId), any())).thenReturn(false);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());
        when(providerRegistry.require(PaymentProviderCodes.MOCK)).thenReturn(paymentProvider);
        when(paymentProvider.code()).thenReturn(PaymentProviderCodes.MOCK);
        when(caseOrderRepository.existsByOrganizationIdAndReference(eq(orgId), any())).thenReturn(false);
        when(caseOrderRepository.saveAndFlush(any(CaseOrder.class))).thenAnswer(inv -> {
            CaseOrder order = inv.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment payment = inv.getArgument(0);
            payment.setId(UUID.randomUUID());
            return payment;
        });
        when(paymentProvider.createCheckout(any()))
                .thenReturn(new CheckoutSession("manual-1", "https://pay.example/manual", PaymentStatus.PENDING_MANUAL));
        OrderResponse mapped = org.mockito.Mockito.mock(OrderResponse.class);
        when(orderMapper.toResponse(any(CaseOrder.class), any(Payment.class))).thenReturn(mapped);

        service.createCheckout(caseId);

        ArgumentCaptor<CaseOrder> orderCaptor = ArgumentCaptor.forClass(CaseOrder.class);
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(orderMapper).toResponse(orderCaptor.capture(), paymentCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING_MANUAL);
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING_MANUAL);
    }

    @Test
    void listByCaseMapsOrdersWithLatestPayment() {
        CaseFile caseFile = openCase(CaseStatus.IN_PROGRESS);
        Payment payment = pendingPayment();
        CaseOrder order = payment.getOrder();
        when(caseService.requireAccessible(caseId)).thenReturn(caseFile);
        when(caseOrderRepository.findByCaseFile_IdOrderByCreatedAtDesc(caseId)).thenReturn(List.of(order));
        when(paymentRepository.findFirstByOrder_IdOrderByCreatedAtDesc(order.getId()))
                .thenReturn(Optional.of(payment));
        OrderResponse mapped = org.mockito.Mockito.mock(OrderResponse.class);
        when(orderMapper.toResponse(order, payment)).thenReturn(mapped);

        assertThat(service.listByCase(caseId)).containsExactly(mapped);
    }

    @Test
    void confirmManualRejectsUnknownPayment() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findDetailedById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmManual(paymentId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.not_found");
    }

    @Test
    void confirmManualRejectsNonManualPayment() {
        Payment payment = pendingPayment();
        when(paymentRepository.findDetailedById(payment.getId())).thenReturn(Optional.of(payment));
        when(caseService.requireAccessible(caseId)).thenReturn(payment.getOrder().getCaseFile());

        assertThatThrownBy(() -> service.confirmManual(payment.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.not_manual");
    }

    @Test
    void confirmManualCompletesPendingManualPayment() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.PENDING_MANUAL);
        payment.getOrder().setStatus(OrderStatus.PENDING_MANUAL);
        when(paymentRepository.findDetailedById(payment.getId())).thenReturn(Optional.of(payment));
        when(caseService.requireAccessible(caseId)).thenReturn(payment.getOrder().getCaseFile());
        when(webhookReceiptRepository.existsByProviderCodeAndIdempotencyKey(
                        eq(PaymentProviderCodes.MOCK), any()))
                .thenReturn(false);
        OrderResponse mapped = org.mockito.Mockito.mock(OrderResponse.class);
        when(orderMapper.toResponse(payment.getOrder(), payment)).thenReturn(mapped);

        assertThat(service.confirmManual(payment.getId())).isSameAs(mapped);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment.getOrder().getCaseFile().getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
        verify(outboxAppender).appendPaymentCompleted(eq(caseId), any());
        verify(webhookReceiptRepository).saveAndFlush(any());
    }

    @Test
    void syncFromProviderReturnsEarlyWhenAlreadyCompleted() {
        Payment payment = pendingPayment();
        payment.setStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findDetailedById(payment.getId())).thenReturn(Optional.of(payment));
        when(caseService.requireAccessible(caseId)).thenReturn(payment.getOrder().getCaseFile());
        OrderResponse mapped = org.mockito.Mockito.mock(OrderResponse.class);
        when(orderMapper.toResponse(payment.getOrder(), payment)).thenReturn(mapped);

        assertThat(service.syncFromProvider(payment.getId())).isSameAs(mapped);
        verify(providerRegistry, never()).require(any());
    }

    @Test
    void syncFromProviderCompletesWhenProviderReportsPaid() {
        Payment payment = pendingPayment();
        when(paymentRepository.findDetailedById(payment.getId())).thenReturn(Optional.of(payment));
        when(caseService.requireAccessible(caseId)).thenReturn(payment.getOrder().getCaseFile());
        when(providerRegistry.require(PaymentProviderCodes.MOCK)).thenReturn(paymentProvider);
        when(paymentProvider.code()).thenReturn(PaymentProviderCodes.MOCK);
        when(paymentProvider.verify("ref-1"))
                .thenReturn(new PaymentStatusView(PaymentStatus.COMPLETED, "ref-1"));
        when(webhookReceiptRepository.existsByProviderCodeAndIdempotencyKey(
                        eq(PaymentProviderCodes.MOCK), any()))
                .thenReturn(false);
        OrderResponse mapped = org.mockito.Mockito.mock(OrderResponse.class);
        when(orderMapper.toResponse(payment.getOrder(), payment)).thenReturn(mapped);

        assertThat(service.syncFromProvider(payment.getId())).isSameAs(mapped);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(outboxAppender).appendPaymentCompleted(eq(caseId), any());
    }

    @Test
    void syncFromProviderMarksFailedWhenProviderReportsFailure() {
        Payment payment = pendingPayment();
        when(paymentRepository.findDetailedById(payment.getId())).thenReturn(Optional.of(payment));
        when(caseService.requireAccessible(caseId)).thenReturn(payment.getOrder().getCaseFile());
        when(providerRegistry.require(PaymentProviderCodes.MOCK)).thenReturn(paymentProvider);
        when(paymentProvider.verify("ref-1"))
                .thenReturn(new PaymentStatusView(PaymentStatus.FAILED, "ref-1"));
        OrderResponse mapped = org.mockito.Mockito.mock(OrderResponse.class);
        when(orderMapper.toResponse(payment.getOrder(), payment)).thenReturn(mapped);

        assertThat(service.syncFromProvider(payment.getId())).isSameAs(mapped);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(outboxAppender, never()).appendPaymentCompleted(any(), any());
    }

    @Test
    void handleWebhookSwallowsConcurrentReceiptInsert() {
        Payment payment = pendingPayment();
        when(providerRegistry.require("mock")).thenReturn(paymentProvider);
        when(paymentProvider.code()).thenReturn(PaymentProviderCodes.MOCK);
        when(paymentProvider.handleWebhook(any(WebhookPayload.class)))
                .thenReturn(new ParsedWebhook("ref-1", "evt-race", true));
        when(webhookReceiptRepository.existsByProviderCodeAndIdempotencyKey(PaymentProviderCodes.MOCK, "evt-race"))
                .thenReturn(false);
        when(paymentRepository.findByProviderCodeAndProviderReference(PaymentProviderCodes.MOCK, "ref-1"))
                .thenReturn(Optional.of(payment));
        when(webhookReceiptRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        service.handleWebhook("mock", Map.of());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(outboxAppender).appendPaymentCompleted(eq(caseId), any());
    }

    private CaseFile openCase(CaseStatus status) {
        ProcedureVersion version = new ProcedureVersion();
        version.setId(UUID.randomUUID());
        version.setPricing(Map.of("currency", "EUR", "amountMinor", 8500));
        CaseFile caseFile = new CaseFile();
        caseFile.setId(caseId);
        caseFile.setOrganizationId(orgId);
        caseFile.setStatus(status);
        caseFile.setReference("KV-2026-CASE0001");
        caseFile.setProcedureVersion(version);
        return caseFile;
    }

    private Payment pendingPayment() {
        CaseFile caseFile = openCase(CaseStatus.CREATED);
        CaseOrder order = new CaseOrder();
        order.setId(UUID.randomUUID());
        order.setCaseFile(caseFile);
        order.setOrganizationId(orgId);
        order.setReference("ORD-1");
        order.setCurrency("EUR");
        order.setAmountMinor(8500L);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setProviderCode(PaymentProviderCodes.MOCK);
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setOrganizationId(orgId);
        payment.setProviderCode(PaymentProviderCodes.MOCK);
        payment.setProviderReference("ref-1");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCurrency("EUR");
        payment.setAmountMinor(8500L);
        return payment;
    }
}
