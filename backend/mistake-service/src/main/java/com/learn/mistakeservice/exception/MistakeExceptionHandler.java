package com.learn.mistakeservice.exception;

import com.learn.common.vo.ErrorVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MistakeExceptionHandler {

    @ExceptionHandler(MistakeNotFoundException.class)
    public ResponseEntity<ErrorVO> handleNotFound(MistakeNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorVO.of("MISTAKE_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(MistakeStorageException.class)
    public ResponseEntity<ErrorVO> handleStorage(MistakeStorageException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorVO.of("STORAGE_UNAVAILABLE", "错题图片暂时无法访问"));
    }
}
