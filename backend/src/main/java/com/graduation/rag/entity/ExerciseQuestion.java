package com.graduation.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 题目实体（表：exercise_question）
 *
 * answer / explanation 带 @JsonIgnore：
 * 实体绝不直接返回给前端，防止作答前泄露答案（前端只见 QuestionView DTO）
 */
@TableName("exercise_question")
public class ExerciseQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long exerciseId;

    /** 1 选择题 / 2 填空题 */
    private Integer type;

    private String question;

    /** 选择题选项 JSON：[{"key":"A","text":"..."}]；填空题为 null */
    private String options;

    /** 正确答案；填空题多答案用 | 分隔 */
    @JsonIgnore
    private String answer;

    @JsonIgnore
    private String explanation;

    private Integer score;

    /** 出题依据片段（溯源） */
    private Long sourceChunkId;

    private String userAnswer;

    /** 1 对 / 0 错 / null 未判 */
    private Integer isCorrect;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Long getSourceChunkId() {
        return sourceChunkId;
    }

    public void setSourceChunkId(Long sourceChunkId) {
        this.sourceChunkId = sourceChunkId;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer;
    }

    public Integer getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Integer isCorrect) {
        this.isCorrect = isCorrect;
    }
}
