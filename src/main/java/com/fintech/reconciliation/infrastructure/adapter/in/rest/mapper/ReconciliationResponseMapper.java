package com.fintech.reconciliation.infrastructure.adapter.in.rest.mapper;

import com.fintech.reconciliation.domain.model.Discrepancy;
import com.fintech.reconciliation.domain.model.Payment;
import com.fintech.reconciliation.domain.model.ReconciliationResult;
import com.fintech.reconciliation.infrastructure.adapter.in.rest.dto.DiscrepancyDetailDto;
import com.fintech.reconciliation.infrastructure.adapter.in.rest.dto.PaymentSummaryDto;
import com.fintech.reconciliation.infrastructure.adapter.in.rest.dto.ReconciliationResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReconciliationResponseMapper {

    @Mapping(target = "paymentId",            expression = "java(result.getPaymentId().value())")
    @Mapping(target = "status",               expression = "java(result.getStatus().name())")
    @Mapping(target = "statusDescription",    expression = "java(result.getStatus().getDescription())")
    @Mapping(target = "fullyReconciled",      expression = "java(result.getStatus().isFullyReconciled())")
    @Mapping(target = "discrepancies",        source = "discrepancies")
    @Mapping(target = "internalPayment",      expression = "java(result.getInternalPayment().map(p -> toPaymentSummaryDto(p)).orElse(null))")
    @Mapping(target = "processorPayment",     expression = "java(result.getProcessorPayment().map(p -> toPaymentSummaryDto(p)).orElse(null))")
    @Mapping(target = "soapProcessorPayment", expression = "java(result.getSoapProcessorPayment().map(p -> toPaymentSummaryDto(p)).orElse(null))")
    ReconciliationResponseDto toResponseDto(ReconciliationResult result);

    @Mapping(target = "paymentId",  expression = "java(payment.getId().value())")
    @Mapping(target = "amount",     expression = "java(payment.getAmount().amount().toPlainString())")
    @Mapping(target = "currency",   expression = "java(payment.getAmount().currencyCode())")
    @Mapping(target = "source",     expression = "java(payment.getSource().name())")
    PaymentSummaryDto toPaymentSummaryDto(Payment payment);

    @Mapping(target = "type",           expression = "java(discrepancy.getType().name())")
    @Mapping(target = "processorSource", source = "processorSource")
    DiscrepancyDetailDto toDiscrepancyDetailDto(Discrepancy discrepancy);

    List<DiscrepancyDetailDto> toDiscrepancyDetailDtoList(List<Discrepancy> discrepancies);
}
