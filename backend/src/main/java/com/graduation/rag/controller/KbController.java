package com.graduation.rag.controller;

import com.graduation.rag.common.Result;
import com.graduation.rag.dto.KbCreateRequest;
import com.graduation.rag.dto.KbUpdateRequest;
import com.graduation.rag.entity.KnowledgeBase;
import com.graduation.rag.security.LoginUser;
import com.graduation.rag.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库接口（设计文档第四章 /api/kbs）
 * 全部需要登录；管理操作在 Service 层校验所有者/管理员
 */
@RestController
@RequestMapping("/api/kbs")
public class KbController {

    private final KnowledgeBaseService kbService;

    public KbController(KnowledgeBaseService kbService) {
        this.kbService = kbService;
    }

    /** 可见知识库列表（公共 + 自己的） */
    @GetMapping
    public Result<List<KnowledgeBase>> list(@AuthenticationPrincipal LoginUser user) {
        return Result.ok(kbService.listVisible(user));
    }

    @PostMapping
    public Result<KnowledgeBase> create(@AuthenticationPrincipal LoginUser user,
                                        @Valid @RequestBody KbCreateRequest req) {
        return Result.ok(kbService.create(user, req));
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBase> detail(@AuthenticationPrincipal LoginUser user,
                                        @PathVariable Long id) {
        return Result.ok(kbService.getForRead(id, user));
    }

    @PutMapping("/{id}")
    public Result<KnowledgeBase> update(@AuthenticationPrincipal LoginUser user,
                                        @PathVariable Long id,
                                        @Valid @RequestBody KbUpdateRequest req) {
        return Result.ok(kbService.update(id, req, user));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal LoginUser user,
                               @PathVariable Long id) {
        kbService.delete(id, user);
        return Result.ok();
    }
}
