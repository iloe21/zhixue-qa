package com.graduation.rag.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 生成练习请求
 */
public class GenerateExerciseRequest {

    @NotNull(message = "请选择知识库")
    private Long kbId;

    /** 选择题数量（1-10，默认 5） */
    private Integer choiceCount;

    /** 填空题数量（0-10，默认 3） */
    private Integer blankCount;

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public Integer getChoiceCount() {
        return choiceCount;
    }

    public void setChoiceCount(Integer choiceCount) {
        this.choiceCount = choiceCount;
    }

    public Integer getBlankCount() {
        return blankCount;
    }

    public void setBlankCount(Integer blankCount) {
        this.blankCount = blankCount;
    }
}
