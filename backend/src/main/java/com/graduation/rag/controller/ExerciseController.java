package com.graduation.rag.controller;

import com.graduation.rag.common.Result;
import com.graduation.rag.dto.ExerciseDetail;
import com.graduation.rag.dto.GenerateExerciseRequest;
import com.graduation.rag.dto.SubmitExerciseRequest;
import com.graduation.rag.entity.Exercise;
import com.graduation.rag.security.LoginUser;
import com.graduation.rag.service.ExerciseService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 练习接口（设计文档第四章 /api/exercises）
 * POST /            生成练习（AI 出题，同步等待约 10~40 秒）
 * GET  /            我的练习列表
 * GET  /{id}        练习详情（待作答时不含答案，防作弊）
 * POST /{id}/submit 提交作答（服务端判分，仅一次机会）
 */
@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @PostMapping
    public Result<ExerciseDetail> generate(@AuthenticationPrincipal LoginUser user,
                                           @Valid @RequestBody GenerateExerciseRequest req) {
        return Result.ok(exerciseService.generate(user, req));
    }

    @GetMapping
    public Result<List<Exercise>> list(@AuthenticationPrincipal LoginUser user) {
        return Result.ok(exerciseService.listMine(user));
    }

    @GetMapping("/{id}")
    public Result<ExerciseDetail> detail(@AuthenticationPrincipal LoginUser user,
                                         @PathVariable Long id) {
        return Result.ok(exerciseService.detail(id, user));
    }

    @PostMapping("/{id}/submit")
    public Result<ExerciseDetail> submit(@AuthenticationPrincipal LoginUser user,
                                         @PathVariable Long id,
                                         @Valid @RequestBody SubmitExerciseRequest req) {
        return Result.ok(exerciseService.submit(id, req, user));
    }
}
