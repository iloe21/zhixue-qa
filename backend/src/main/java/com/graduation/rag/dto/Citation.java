package com.graduation.rag.dto;

/**
 * 引用溯源条目（设计文档 5.4）
 *
 * @param ref        引用编号（[引用N]，与提示词中编号一致）
 * @param chunkId    片段 id（前端据此调 /api/chunks/{id} 查看原文）
 * @param docId      文档 id
 * @param docName    来源文档名
 * @param chunkIndex 片段序号（从 0 开始）
 * @param score      相似度得分（0~1）
 * @param snippet    片段摘要（前 120 字符）
 */
public record Citation(
        Integer ref,
        Long chunkId,
        Long docId,
        String docName,
        Integer chunkIndex,
        Double score,
        String snippet) {
}
