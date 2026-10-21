package com.graduation.rag.service;

import com.graduation.rag.entity.ChunkMeta;
import com.graduation.rag.entity.Document;
import com.graduation.rag.mapper.ChunkMetaMapper;
import com.graduation.rag.mapper.DocumentMapper;
import com.graduation.rag.util.TextChunkSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档解析服务（异步执行，对应设计文档 2.3 流程一）
 *
 * 管道：Tika 提取文本 → 重叠切分 → chunk_meta 入库 → 向量化入向量库
 * 关键设计：向量 Document 的 id = chunk_meta.id（对齐），引用溯源据此回查原文
 *
 * 注：独立成 Service 是为了 @Async 走 Spring 代理生效
 *（同类内部调用不会触发异步）
 */
@Service
public class DocumentParseService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseService.class);

    private final DocumentMapper documentMapper;
    private final ChunkMetaMapper chunkMetaMapper;
    private final VectorStoreManager vectorStoreManager;
    private final TextChunkSplitter splitter;

    public DocumentParseService(DocumentMapper documentMapper, ChunkMetaMapper chunkMetaMapper,
                                VectorStoreManager vectorStoreManager, TextChunkSplitter splitter) {
        this.documentMapper = documentMapper;
        this.chunkMetaMapper = chunkMetaMapper;
        this.vectorStoreManager = vectorStoreManager;
        this.splitter = splitter;
    }

    @Async("parseExecutor")
    public void parseAsync(Long docId) {
        Document doc = documentMapper.selectById(docId);
        if (doc == null) {
            return;
        }
        try {
            Resource resource = new FileSystemResource(doc.getFilePath());
            if (!resource.exists()) {
                throw new IllegalStateException("文件不存在: " + doc.getFilePath());
            }

            // 1. Tika 提取文本（统一支持 PDF / Word / TXT / Markdown）
            String text = new TikaDocumentReader(resource).get().stream()
                    .map(org.springframework.ai.document.Document::getText)
                    .collect(Collectors.joining("\n"));

            // 2. 重叠切分
            List<String> pieces = splitter.split(text);
            if (pieces.isEmpty()) {
                throw new IllegalStateException("未能从文档提取到有效文本（可能是扫描版 PDF，请改用文字版）");
            }

            // 3. chunk_meta 入库 + 构建向量文档（id 对齐）
            List<org.springframework.ai.document.Document> aiDocs = new ArrayList<>();
            for (int i = 0; i < pieces.size(); i++) {
                String piece = pieces.get(i);
                ChunkMeta meta = new ChunkMeta();
                meta.setDocId(doc.getId());
                meta.setKbId(doc.getKbId());
                meta.setChunkIndex(i);
                meta.setContent(piece);
                meta.setCharCount(piece.length());
                chunkMetaMapper.insert(meta);   // MyBatis-Plus 回填自增 id

                aiDocs.add(new org.springframework.ai.document.Document(
                        String.valueOf(meta.getId()), piece,
                        // metadata 全部用字符串：向量库 JSON 持久化后数字类型会漂移（Long→Integer），
                        // 导致过滤表达式 in 匹配失效；字符串比较跨序列化稳定（重要坑，见开发注意事项）
                        Map.of("kbId", String.valueOf(doc.getKbId()),
                               "docId", String.valueOf(doc.getId()),
                               "docName", doc.getFilename(),
                               "chunkIndex", String.valueOf(i))));
            }

            // 4. 向量化入库（调百炼 Embedding API，分批）
            vectorStoreManager.addInBatches(aiDocs);

            doc.setChunkCount(pieces.size());
            doc.setParseStatus(1);
            doc.setErrorMsg(null);
            documentMapper.updateById(doc);
            log.info("文档解析成功: {}（{} 个片段）", doc.getFilename(), pieces.size());
        } catch (Exception e) {
            log.error("文档解析失败: docId={}, file={}", docId, doc.getFilename(), e);
            doc.setParseStatus(2);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            doc.setErrorMsg(msg.length() > 480 ? msg.substring(0, 480) : msg);
            documentMapper.updateById(doc);
        }
    }
}
