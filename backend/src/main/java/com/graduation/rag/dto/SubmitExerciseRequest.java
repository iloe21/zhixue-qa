package com.graduation.rag.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 提交作答请求（服务端判分，答案绝不在前端比对）
 */
public class SubmitExerciseRequest {

    @NotEmpty(message = "作答内容不能为空")
    private List<AnswerItem> answers;

    public record AnswerItem(Long questionId, String answer) {
    }

    public List<AnswerItem> getAnswers() {
        return answers;
    }

    public void setAnswers(List<AnswerItem> answers) {
        this.answers = answers;
    }
}
