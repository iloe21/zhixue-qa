package com.graduation.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提问请求（SSE 流式返回）
 */
public class ChatRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题过长（最多 2000 字符）")
    private String question;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
