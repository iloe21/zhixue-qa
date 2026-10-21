package com.graduation.rag.exception;

import com.graduation.rag.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：所有异常统一转为 {code, message, data} JSON，
 * 避免堆栈信息直接暴露给前端
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常（用户名已存在、密码错误等） */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getCode()).body(Result.error(e.getCode(), e.getMessage()));
    }

    /** 参数校验异常（@Valid 触发），返回第一条校验错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getDefaultMessage() : "参数校验失败";
        return ResponseEntity.badRequest().body(Result.error(400, msg));
    }

    /**
     * 方法级鉴权拒绝（@PreAuthorize 触发）→ 403 JSON
     * 必须显式处理：否则被下方 Exception 兜底捕获变成 500（实测踩坑）
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(org.springframework.security.access.AccessDeniedException e) {
        return ResponseEntity.status(403).body(Result.error(403, "没有权限执行此操作"));
    }

    /** 兜底：未预期的异常统一 500，详情只进日志 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("服务器内部错误", e);
        return ResponseEntity.status(500).body(Result.error(500, "服务器内部错误，请稍后重试"));
    }
}
