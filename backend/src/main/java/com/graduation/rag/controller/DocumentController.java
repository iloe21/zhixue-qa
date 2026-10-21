package com.graduation.rag.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.rag.common.Result;
import com.graduation.rag.entity.ChunkMeta;
import com.graduation.rag.entity.Document;
import com.graduation.rag.security.LoginUser;
import com.graduation.rag.service.DocumentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档与片段接口（设计文档第四章）
 * POST /api/kbs/{kbId}/docs      上传（multipart）
 * GET  /api/kbs/{kbId}/docs      文档列表
 * POST /api/docs/{id}/reparse    重新解析
 * DELETE /api/docs/{id}          删除
 * GET  /api/docs/{docId}/chunks  片段分页浏览
 * GET  /api/chunks/{id}          单片段原文（溯源弹窗）
 */
@RestController
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/api/kbs/{kbId}/docs")
    public Result<Document> upload(@AuthenticationPrincipal LoginUser user,
                                   @PathVariable Long kbId,
                                   @RequestParam("file") MultipartFile file) {
        return Result.ok(documentService.upload(kbId, file, user));
    }

    @GetMapping("/api/kbs/{kbId}/docs")
    public Result<List<Document>> list(@AuthenticationPrincipal LoginUser user,
                                       @PathVariable Long kbId) {
        return Result.ok(documentService.listByKb(kbId, user));
    }

    @PostMapping("/api/docs/{id}/reparse")
    public Result<Void> reparse(@AuthenticationPrincipal LoginUser user,
                                @PathVariable Long id) {
        documentService.reparse(id, user);
        return Result.ok();
    }

    @DeleteMapping("/api/docs/{id}")
    public Result<Void> delete(@AuthenticationPrincipal LoginUser user,
                               @PathVariable Long id) {
        documentService.delete(id, user);
        return Result.ok();
    }

    @GetMapping("/api/docs/{docId}/chunks")
    public Result<Page<ChunkMeta>> chunks(@AuthenticationPrincipal LoginUser user,
                                          @PathVariable Long docId,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "5") long size) {
        return Result.ok(documentService.chunks(docId, page, size, user));
    }

    @GetMapping("/api/chunks/{id}")
    public Result<ChunkMeta> chunk(@AuthenticationPrincipal LoginUser user,
                                   @PathVariable Long id) {
        return Result.ok(documentService.getChunk(id, user));
    }
}
