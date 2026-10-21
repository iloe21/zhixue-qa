package com.graduation.rag.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建会话请求：必须指定知识库检索范围（公共库 + 自己的私有库，可多选）
 */
public class CreateConversationRequest {

    @NotEmpty(message = "请至少选择一个知识库")
    private List<Long> kbIds;

    @Size(max = 100, message = "会话标题过长")
    private String title;

    public List<Long> getKbIds() {
        return kbIds;
    }

    public void setKbIds(List<Long> kbIds) {
        this.kbIds = kbIds;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
