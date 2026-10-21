package com.graduation.rag.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 编辑知识库请求（字段均可选，只更新传入的字段）
 */
public class KbUpdateRequest {

    @Size(min = 1, max = 100, message = "知识库名称长度需为 1-100 个字符")
    private String name;

    @Size(max = 500, message = "描述过长")
    private String description;

    @Pattern(regexp = "private|public", message = "visibility 只能是 private 或 public")
    private String visibility;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }
}
