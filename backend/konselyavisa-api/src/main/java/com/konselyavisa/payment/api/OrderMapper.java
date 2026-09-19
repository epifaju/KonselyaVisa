package com.konselyavisa.payment.api;

import com.konselyavisa.payment.domain.CaseOrder;
import com.konselyavisa.payment.domain.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    PaymentResponse toPaymentResponse(Payment payment);

    @Mapping(target = "id", source = "order.id")
    @Mapping(target = "caseId", source = "order.caseFile.id")
    @Mapping(target = "reference", source = "order.reference")
    @Mapping(target = "currency", source = "order.currency")
    @Mapping(target = "amountMinor", source = "order.amountMinor")
    @Mapping(target = "status", source = "order.status")
    @Mapping(target = "providerCode", source = "order.providerCode")
    @Mapping(target = "payment", source = "payment")
    OrderResponse toResponse(CaseOrder order, Payment payment);
}
