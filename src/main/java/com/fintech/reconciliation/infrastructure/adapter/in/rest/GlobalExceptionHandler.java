package com.fintech.reconciliation.infrastructure.adapter.in.rest;

import com.fintech.reconciliation.domain.exception.PaymentNotFoundException;
import com.fintech.reconciliation.domain.exception.ProcessorUnavailableException;
import com.fintech.reconciliation.infrastructure.adapter.in.rest.dto.ApiErrorDto;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiErrorDto> handlePaymentNotFound(PaymentNotFoundException ex) {
        log.warn("Payment not found: {}", ex.getPaymentId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            new ApiErrorDto(
                HttpStatus.NOT_FOUND.value(),
                "PAYMENT_NOT_FOUND",
                ex.getMessage(),
                null,
                LocalDateTime.now()
            )
        );
    }

    @ExceptionHandler(ProcessorUnavailableException.class)
    public ResponseEntity<ApiErrorDto> handleProcessorUnavailable(ProcessorUnavailableException ex) {
        log.error("Payment processor unavailable: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            new ApiErrorDto(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "PROCESSOR_UNAVAILABLE",
                "External payment processor is temporarily unavailable. Please retry.",
                null,
                LocalDateTime.now()
            )
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorDto> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiErrorDto.FieldErrorDto> fieldErrors = ex.getConstraintViolations().stream()
            .map(cv -> new ApiErrorDto.FieldErrorDto(
                cv.getPropertyPath().toString(),
                cv.getMessage()
            ))
            .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            new ApiErrorDto(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Request validation failed",
                fieldErrors,
                LocalDateTime.now()
            )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDto> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<ApiErrorDto.FieldErrorDto> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ApiErrorDto.FieldErrorDto(fe.getField(), fe.getDefaultMessage()))
            .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            new ApiErrorDto(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Request validation failed",
                fieldErrors,
                LocalDateTime.now()
            )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handleUnexpected(Exception ex) {
        log.error("Unexpected error during reconciliation", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            new ApiErrorDto(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An unexpected error occurred. Our team has been notified.",
                null,
                LocalDateTime.now()
            )
        );
    }
}
