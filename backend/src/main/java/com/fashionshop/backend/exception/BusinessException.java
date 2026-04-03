package com.fashionshop.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exception nghiệp vụ duy nhất — throw từ Service layer.
 * Convention: throw new BusinessException(ErrorCode.XXX, HttpStatus.YYY)
 *             hoặc throw new BusinessException(ErrorCode.XXX, HttpStatus.YYY, "overridden message")
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;

    public BusinessException(ErrorCode errorCode, HttpStatus status) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.status = status;
    }

    public BusinessException(ErrorCode errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    // ============ Static factory methods hay dùng ============

    public static BusinessException notFound(ErrorCode code) {
        return new BusinessException(code, HttpStatus.NOT_FOUND);
    }

    public static BusinessException conflict(ErrorCode code) {
        return new BusinessException(code, HttpStatus.CONFLICT);
    }

    public static BusinessException badRequest(ErrorCode code) {
        return new BusinessException(code, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
    }

    public static BusinessException unauthorized(ErrorCode code) {
        return new BusinessException(code, HttpStatus.UNAUTHORIZED);
    }
}
