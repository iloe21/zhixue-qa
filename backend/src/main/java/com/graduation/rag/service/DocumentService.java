package com.graduation.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.rag.entity.ChunkMeta;
import com.graduation.rag.entity.Document;
import com.graduation.rag.entity.KnowledgeBase;
import com.graduation.rag.exception.BusinessException;
import com.graduation.rag.mapper.ChunkMetaMapper;
import com.graduation.rag.mapper.DocumentMapper;
import com.graduation.rag.mapper.KnowledgeBaseMapper;
import com.graduation.rag.security.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 文档服务：上传（安全校验+落盘+触发异步解析）、列表、删除、重新解析、片段浏览
 *
 * 上传安全（设计文档 5.7）：
 * 1. 扩展名白名单：pdf/doc/docx/txt/md
 * 2. 大小限制 10MB（spring.servlet.multipart 全局限制）
 * 3. 文件名清洗路径分隔符（防路径穿越）+ UUID 随机存储名（防覆盖/防猜测）
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Set<String> ALLOWED_EXT = Set.of("pdf", "doc", "docx", "txt", "md");

    private final DocumentMapper documentMapper;
    private final ChunkMetaMapper chunkMetaMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeBaseService kbService;
    private final DocumentParseService parseService;
    private final VectorStoreManager vectorStoreManager;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public DocumentService(DocumentMapper documentMapper, ChunkMetaMapper chunkMetaMapper,
                           KnowledgeBaseMapper kbMapper, KnowledgeBaseService kbService,
                           DocumentParseService parseService, VectorStoreManager vectorStoreManager) {
        this.documentMapper = documentMapper;
        this.chunkMetaMapper = chunkMetaMapper;
        this.kbMapper = kbMapper;
        this.kbService = kbService;
        this.parseService = parseService;
        this.vectorStoreManager = vectorStoreManager;
    }

    /**
     * 上传文档：校验 → 落盘 → 建 document 记录（解析中）→ 触发异步解析
     */
    public Document upload(Long kbId, MultipartFile file, LoginUser user) {
        KnowledgeBase kb = kbService.getForManage(kbId, user);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件为空");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "未命名";
        String ext = extensionOf(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("不支持的文件类型 ." + ext + "，仅支持 pdf/doc/docx/txt/md");
        }

        // 文件名清洗 + 随机存储名
        String safeName = original.replaceAll("[\\\\/:*?\"<>|]", "_");
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = dir.resolve(storedName);
        try {
            Files.createDirectories(dir);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败: " + e.getMessage());
        }

        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setFilename(safeName);
        doc.setFilePath(target.toString());
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setParseStatus(0);   // 解析中
        doc.setCreatedBy(user.userId());
        documentMapper.insert(doc);

        // 知识库文档计数 +1
        kb.setDocCount((kb.getDocCount() == null ? 0 : kb.getDocCount()) + 1);
        kbMapper.updateById(kb);

        // 异步解析（不阻塞上传响应）
        parseService.parseAsync(doc.getId());
        log.info("文档已上传，进入解析队列: kbId={}, docId={}, file={}", kbId, doc.getId(), safeName);
        return doc;
    }

    public List<Document> listByKb(Long kbId, LoginUser user) {
        kbService.getForRead(kbId, user);
        return documentMapper.selectList(
                new QueryWrapper<Document>().eq("kb_id", kbId).orderByDesc("id"));
    }

    public Document getForRead(Long docId, LoginUser user) {
        Document doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        kbService.getForRead(doc.getKbId(), user);
        return doc;
    }

    public Document getForManage(Long docId, LoginUser user) {
        Document doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        kbService.getForManage(doc.getKbId(), user);
        return doc;
    }

    /** 删除文档：向量 + chunk 记录 + 磁盘文件 + 数据库行 */
    public void delete(Long docId, LoginUser user) {
        Document doc = getForManage(docId, user);
        removeVectorsAndChunks(docId);
        KnowledgeBaseService.deleteFileQuietly(doc.getFilePath());
        documentMapper.deleteById(docId);
        // 知识库文档计数 -1（防负数）
        KnowledgeBase kb = kbMapper.selectById(doc.getKbId());
        if (kb != null) {
            kb.setDocCount(Math.max(0, (kb.getDocCount() == null ? 1 : kb.getDocCount()) - 1));
            kbMapper.updateById(kb);
        }
    }

    /** 重新解析：清理旧向量与片段 → 状态重置 → 重新走解析管道 */
    public void reparse(Long docId, LoginUser user) {
        Document doc = getForManage(docId, user);
        if (doc.getParseStatus() != null && doc.getParseStatus() == 0) {
            throw new BusinessException("文档正在解析中，请稍后再试");
        }
        removeVectorsAndChunks(docId);
        doc.setParseStatus(0);
        doc.setChunkCount(0);
        doc.setErrorMsg(null);
        documentMapper.updateById(doc);
        parseService.parseAsync(docId);
    }

    /** 分页浏览文档片段 */
    public Page<ChunkMeta> chunks(Long docId, long page, long size, LoginUser user) {
        getForRead(docId, user);
        Page<ChunkMeta> p = new Page<>(page, size);
        return chunkMetaMapper.selectPage(p, new QueryWrapper<ChunkMeta>()
                .eq("doc_id", docId)
                .orderByAsc("chunk_index"));
    }

    /** 单片段原文（引用溯源弹窗用），权限跟随所属文档→知识库 */
    public ChunkMeta getChunk(Long chunkId, LoginUser user) {
        ChunkMeta chunk = chunkMetaMapper.selectById(chunkId);
        if (chunk == null) {
            throw new BusinessException(404, "片段不存在");
        }
        getForRead(chunk.getDocId(), user);
        return chunk;
    }

    private void removeVectorsAndChunks(Long docId) {
        List<ChunkMeta> chunks = chunkMetaMapper.selectList(
                new QueryWrapper<ChunkMeta>().eq("doc_id", docId));
        vectorStoreManager.deleteByIds(chunks.stream().map(c -> String.valueOf(c.getId())).toList());
        chunkMetaMapper.delete(new QueryWrapper<ChunkMeta>().eq("doc_id", docId));
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }
}
