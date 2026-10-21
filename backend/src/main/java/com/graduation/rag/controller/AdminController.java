package com.graduation.rag.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.rag.common.Result;
import com.graduation.rag.dto.StatsResponse;
import com.graduation.rag.dto.UpdateRoleRequest;
import com.graduation.rag.dto.UpdateStatusRequest;
import com.graduation.rag.entity.User;
import com.graduation.rag.security.LoginUser;
import com.graduation.rag.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台接口（设计文档第四章 /api/admin）
 * 类级 @PreAuthorize：所有接口仅 admin 角色可访问（403 JSON 由 SecurityConfig 统一返回）
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('admin')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public Result<StatsResponse> stats() {
        return Result.ok(adminService.stats());
    }

    @GetMapping("/users")
    public Result<Page<User>> users(@RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "10") long size,
                                    @RequestParam(required = false) String keyword) {
        return Result.ok(adminService.listUsers(page, size, keyword));
    }

    @PutMapping("/users/{id}/status")
    public Result<Void> updateStatus(@AuthenticationPrincipal LoginUser admin,
                                     @PathVariable Long id,
                                     @Valid @RequestBody UpdateStatusRequest req) {
        adminService.updateStatus(id, req.getStatus(), admin);
        return Result.ok();
    }

    @PutMapping("/users/{id}/role")
    public Result<Void> updateRole(@AuthenticationPrincipal LoginUser admin,
                                   @PathVariable Long id,
                                   @Valid @RequestBody UpdateRoleRequest req) {
        adminService.updateRole(id, req.getRole(), admin);
        return Result.ok();
    }
}
