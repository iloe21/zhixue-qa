package com.graduation.rag.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 管理员修改用户状态请求
 */
public class UpdateStatusRequest {

    /** 1 启用 / 0 禁用 */
    @NotNull(message = "状态值不能为空")
    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
