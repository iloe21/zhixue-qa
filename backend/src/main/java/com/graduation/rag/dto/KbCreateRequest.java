package com.graduation.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建知识库请求
 */
public class KbCreateRequest {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称过长")
    private String name;

    @Size(max = 500, message = "描述过长")
    private String description;

    /** private / public，默认 private；public 仅管理员可设（服务端校验） */
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
