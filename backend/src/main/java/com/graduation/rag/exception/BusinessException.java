package com.graduation.rag.exception;

/**
 * 业务异常：Service 层主动抛出，由全局异常处理器统一转为 JSON 响应
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(400, message);
    }

    public int getCode() {
        return code;
    }
}
