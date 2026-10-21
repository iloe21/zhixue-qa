package com.graduation.rag.controller;

import com.graduation.rag.common.Result;
import com.graduation.rag.dto.ChangePasswordRequest;
import com.graduation.rag.dto.ForgotPasswordRequest;
import com.graduation.rag.dto.LoginRequest;
import com.graduation.rag.dto.LoginResponse;
import com.graduation.rag.dto.RegisterRequest;
import com.graduation.rag.dto.ResetPasswordRequest;
import com.graduation.rag.dto.UpdateProfileRequest;
import com.graduation.rag.entity.User;
import com.graduation.rag.security.LoginUser;
import com.graduation.rag.service.AuthService;
import com.graduation.rag.service.CaptchaService;
import com.graduation.rag.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（对应设计文档第四章 /api/auth）
 * 公开：注册 / 登录 / 图形验证码 / 忘记密码(发送+重置)
 * 需认证：个人信息 / 修改密码
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, CaptchaService captchaService,
                          PasswordResetService passwordResetService) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.passwordResetService = passwordResetService;
    }

    /** 获取图形验证码（ID + Base64 图片） */
    @GetMapping("/captcha")
    public Result<CaptchaService.CaptchaResult> captcha() {
        return Result.ok(captchaService.generate());
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return Result.ok();
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    /** 忘记密码 - 发送验证码到注册邮箱 */
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordResetService.sendResetCode(req.getUsername(), req.getEmail());
        return Result.ok();
    }

    /** 忘记密码 - 校验验证码并重置 */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.resetPassword(req.getUsername(), req.getCode(), req.getNewPassword());
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<User> me(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(authService.getById(loginUser.userId()));
    }

    @PutMapping("/me")
    public Result<User> updateMe(@AuthenticationPrincipal LoginUser loginUser,
                                 @Valid @RequestBody UpdateProfileRequest req) {
        return Result.ok(authService.updateProfile(loginUser.userId(), req));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                       @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(loginUser.userId(), req);
        return Result.ok();
    }
}
