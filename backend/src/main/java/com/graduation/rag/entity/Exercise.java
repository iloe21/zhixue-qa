package com.graduation.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 练习实体（表：exercise）
 */
@TableName("exercise")
public class Exercise {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long kbId;

    private String title;

    private Integer choiceCount;

    private Integer blankCount;

    /** 得分（作答后写入） */
    private Integer score;

    private Integer totalScore;

    /** 0 待作答 / 1 已作答 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
