package com.graduation.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 会话重命名请求
 */
public class RenameRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题过长")
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
