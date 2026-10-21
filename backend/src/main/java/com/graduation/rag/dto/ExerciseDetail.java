package com.graduation.rag.dto;

import com.graduation.rag.entity.Exercise;

import java.util.List;

/**
 * 练习详情视图：
 * - 待作答（status=0）：questions 不含 answer/explanation（防作弊）
 * - 已作答（status=1）：含正确答案、解析、判分结果
 */
public record ExerciseDetail(Exercise exercise, List<QuestionView> questions) {

    public record QuestionView(
            Long id,
            Integer type,
            String question,
            List<Option> options,
            String answer,
            String explanation,
            Integer score,
            Long sourceChunkId,
            String userAnswer,
            Integer isCorrect) {
    }

    public record Option(String key, String text) {
    }
}
