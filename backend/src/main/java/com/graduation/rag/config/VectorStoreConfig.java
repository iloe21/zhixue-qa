package com.graduation.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * 向量存储配置：Spring AI SimpleVectorStore（文件持久化，零外部依赖）
 *
 * 设计说明（设计文档 2.4 节选型）：
 * - 向量存内存 + JSON 文件持久化，启动时自动加载
 * - Document 的 id 与 MySQL chunk_meta.id 对齐，保证引用溯源可回查原文
 * - 数据量增大后可平滑升级 pgvector/Milvus（Spring AI VectorStore 接口不变）
 */
@Configuration
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    @Bean
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel,
                                               @Value("${app.vector-store-path}") String path) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File file = new File(path);
        if (file.exists() && file.length() > 0) {
            store.load(file);
            log.info("向量库已加载: {}", file.getAbsolutePath());
        } else {
            log.info("向量库为空，将创建新库: {}", file.getAbsolutePath());
        }
        return store;
    }
}
