package com.learn.paymentservice.exception;

import com.learn.common.vo.ErrorVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(PlanNotFoundException.class)
    public ResponseEntity<ErrorVO> handlePlanNotFound(PlanNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorVO.of("PLAN_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ErrorVO> handleInvalidIdempotencyKey(
            InvalidIdempotencyKeyException exception
    ) {
        return ResponseEntity.badRequest()
                .body(ErrorVO.of("INVALID_IDEMPOTENCY_KEY", exception.getMessage()));
    }

    @ExceptionHandler(PaymentIdempotencyConflictException.class)
    public ResponseEntity<ErrorVO> handleIdempotencyConflict(
            PaymentIdempotencyConflictException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorVO.of("IDEMPOTENCY_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(PaymentUserUnavailableException.class)
    public ResponseEntity<ErrorVO> handleUserUnavailable(
            PaymentUserUnavailableException exception
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorVO.of("CURRENT_USER_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(PaymentMessagingException.class)
    public ResponseEntity<ErrorVO> handleMessaging(PaymentMessagingException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorVO.of("PAYMENT_MESSAGING_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(PaymentLockUnavailableException.class)
    public ResponseEntity<ErrorVO> handleLockUnavailable(
            PaymentLockUnavailableException exception
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorVO.of("PAYMENT_LOCK_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorVO> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorVO(
                "VALIDATION_ERROR",
                "请求参数校验失败",
                fieldErrors
        ));
    }
}
