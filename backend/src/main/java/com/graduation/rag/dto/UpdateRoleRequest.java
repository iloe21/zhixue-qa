package com.graduation.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 管理员修改用户角色请求
 */
public class UpdateRoleRequest {

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "student|admin", message = "角色只能是 student 或 admin")
    private String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
