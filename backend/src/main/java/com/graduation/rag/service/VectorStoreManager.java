package com.graduation.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * 向量库管理器：SimpleVectorStore 的统一出入口
 *
 * 职责：
 * 1. 分批写入（百炼 Embedding 接口单批条数限制，每批触发一次远程向量化）
 * 2. 每次变更后同步持久化到 JSON 文件（synchronized 防并发写坏文件）
 * 3. 封装相似度检索（供模块 C 问答使用）
 */
@Component
public class VectorStoreManager {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreManager.class);

    private final SimpleVectorStore store;
    private final File file;
    private final int batchSize;

    public VectorStoreManager(SimpleVectorStore store,
                              @Value("${app.vector-store-path}") String path,
                              @Value("${app.parse.embedding-batch-size}") int batchSize) {
        this.store = store;
        this.file = new File(path);
        this.batchSize = batchSize;
    }

    /** 分批添加（每批调用一次 Embedding API），完成后持久化 */
    public void addInBatches(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        for (int i = 0; i < docs.size(); i += batchSize) {
            store.add(docs.subList(i, Math.min(i + batchSize, docs.size())));
        }
        persist();
        log.info("向量库新增 {} 条向量", docs.size());
    }

    /** 按 id 删除向量并持久化 */
    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        store.delete(ids);
        persist();
        log.info("向量库删除 {} 条向量", ids.size());
    }

    /**
     * 相似度检索（模块 C 问答链路使用）
     * @param filter 元数据过滤表达式（如按 kbId 范围过滤），可为 null
     */
    public List<Document> search(String query, int topK, double threshold, Filter.Expression filter) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold);
        if (filter != null) {
            builder.filterExpression(filter);
        }
        return store.similaritySearch(builder.build());
    }

    private synchronized void persist() {
        try {
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            store.save(file);
        } catch (Exception e) {
            throw new IllegalStateException("向量库持久化失败: " + e.getMessage(), e);
        }
    }
}
