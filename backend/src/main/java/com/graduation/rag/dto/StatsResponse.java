package com.graduation.rag.dto;

/**
 * 管理后台仪表盘统计数据
 */
public record StatsResponse(
        long users,
        long knowledgeBases,
        long publicKbs,
        long documents,
        long chunks,
        long conversations,
        long qaMessages,
        long exercises) {
}
