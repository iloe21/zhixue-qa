package com.graduation.rag.dto;

import com.graduation.rag.entity.Conversation;

import java.util.List;

/**
 * 会话详情：会话 + 知识库范围（id 与名称，前端头部展示）
 */
public record ConversationDetail(
        Conversation conversation,
        List<Long> kbIds,
        List<String> kbNames) {
}
