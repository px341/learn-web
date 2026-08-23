package com.learn.auth.exception;

import com.learn.common.vo.ErrorVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将认证领域异常和参数校验异常转换成稳定的 API 错误结构。
 *
 * <p>登录失败统一返回“邮箱或密码错误”，避免向客户端泄露账号是否存在。</p>
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    /** 头像内容或大小不符合约束时返回 400。 */
    @ExceptionHandler(InvalidAvatarException.class)
    public ResponseEntity<ErrorVO> handleInvalidAvatar(InvalidAvatarException exception) {
        return ResponseEntity.badRequest()
                .body(ErrorVO.of("INVALID_AVATAR", exception.getMessage()));
    }

    /** multipart 在进入 Controller 前超过配置上限时返回稳定错误。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorVO> handleAvatarTooLarge() {
        return ResponseEntity.badRequest()
                .body(ErrorVO.of("INVALID_AVATAR", "头像不能超过 5MB"));
    }

    /** Garage 不可用时不把 SDK 或连接细节暴露给客户端。 */
    @ExceptionHandler(AvatarStorageException.class)
    public ResponseEntity<ErrorVO> handleAvatarStorage() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorVO.of("AVATAR_STORAGE_UNAVAILABLE", "头像存储服务暂时不可用"));
    }

    /** 用户资料修改请求没有有效字段时返回 400。 */
    @ExceptionHandler(InvalidProfileUpdateException.class)
    public ResponseEntity<ErrorVO> handleInvalidProfileUpdate(
            InvalidProfileUpdateException exception
    ) {
        return ResponseEntity.badRequest()
                .body(ErrorVO.of("INVALID_PROFILE_UPDATE", exception.getMessage()));
    }

    /** JWT 合法但对应账号已不存在或被禁用时，要求客户端清理当前会话。 */
    @ExceptionHandler(CurrentUserUnavailableException.class)
    public ResponseEntity<ErrorVO> handleCurrentUserUnavailable(
            CurrentUserUnavailableException exception
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorVO.of("INVALID_SESSION", exception.getMessage()));
    }

    /** 登录邮箱不存在、密码错误或账号不可用时统一返回 401。 */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorVO> handleBadCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorVO.of("INVALID_CREDENTIALS", "邮箱或密码错误"));
    }

    /** 邮箱唯一约束冲突时返回 409。 */
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorVO> handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorVO.of("EMAIL_ALREADY_REGISTERED", exception.getMessage()));
    }

    /** 两次密码不一致时返回字段级校验错误。 */
    @ExceptionHandler(PasswordConfirmationMismatchException.class)
    public ResponseEntity<ErrorVO> handlePasswordConfirmationMismatch(
            PasswordConfirmationMismatchException exception
    ) {
        return ResponseEntity.badRequest().body(new ErrorVO(
                "VALIDATION_ERROR",
                "请求参数校验失败",
                Map.of("passwordConfirmation", exception.getMessage())
        ));
    }

    /** 汇总 Bean Validation 产生的字段错误。 */
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
