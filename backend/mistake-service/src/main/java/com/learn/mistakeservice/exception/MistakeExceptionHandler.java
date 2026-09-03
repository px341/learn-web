package com.learn.mistakeservice.exception;

import com.learn.common.vo.ErrorVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class MistakeExceptionHandler {

    @ExceptionHandler(MistakeNotFoundException.class)
    public ResponseEntity<ErrorVO> handleNotFound(MistakeNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorVO.of("MISTAKE_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(AnalysisNotCompletedException.class)
    public ResponseEntity<ErrorVO> handleAnalysisNotCompleted(
            AnalysisNotCompletedException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorVO.of("ANALYSIS_NOT_COMPLETED", exception.getMessage()));
    }

    @ExceptionHandler(MistakeStorageException.class)
    public ResponseEntity<ErrorVO> handleStorage(MistakeStorageException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorVO.of("STORAGE_UNAVAILABLE", "错题图片存储暂时不可用"));
    }

    @ExceptionHandler(MistakeContentRequiredException.class)
    public ResponseEntity<ErrorVO> handleContentRequired(
            MistakeContentRequiredException exception
    ) {
        return ResponseEntity.badRequest()
                .body(ErrorVO.of("MISTAKE_CONTENT_REQUIRED", exception.getMessage()));
    }

    @ExceptionHandler(InsufficientCreditsException.class)
    public ResponseEntity<ErrorVO> handleInsufficientCredits(
            InsufficientCreditsException exception
    ) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(ErrorVO.of("INSUFFICIENT_CREDITS", exception.getMessage()));
    }

    @ExceptionHandler({MistakeImageTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorVO> handleImageTooLarge(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorVO.of("IMAGE_TOO_LARGE", "错题图片不能超过 10MB"));
    }

    @ExceptionHandler(InvalidMistakeImageException.class)
    public ResponseEntity<ErrorVO> handleInvalidImage(InvalidMistakeImageException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorVO.of("UNSUPPORTED_IMAGE_TYPE", exception.getMessage()));
    }

    @ExceptionHandler(MistakeUserUnavailableException.class)
    public ResponseEntity<ErrorVO> handleUserUnavailable(
            MistakeUserUnavailableException exception
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorVO.of("CURRENT_USER_UNAVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorVO> handleValidation(MethodArgumentNotValidException exception) {
        return validationError(exception.getBindingResult());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorVO> handleBinding(BindException exception) {
        return validationError(exception.getBindingResult());
    }

    private ResponseEntity<ErrorVO> validationError(BindingResult bindingResult) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        bindingResult.getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(new ErrorVO(
                "VALIDATION_ERROR",
                "请求参数校验失败",
                fieldErrors
        ));
    }
}
